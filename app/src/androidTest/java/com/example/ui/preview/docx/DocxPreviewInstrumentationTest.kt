package com.example.ui.preview.docx

import android.graphics.Bitmap
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.MotionEvent
import android.widget.FrameLayout
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import com.example.MainActivity
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import android.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DocxPreviewInstrumentationTest {
    private val visualStateRequestIds = AtomicLong()

    @Test fun nativeWebViewZoomSupportsHorizontalPanToBothEdges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = launchTestActivity(instrumentation)
        val fixture = fixtureDocx()
        val ready = CountDownLatch(1)
        val failures = AtomicReference<String?>(null)
        val confirmedTaps = java.util.concurrent.atomic.AtomicInteger()
        val tapConfirmed = CountDownLatch(1)
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(activity))
                .addPathHandler("/docx-src/", object : WebViewAssetLoader.PathHandler {
                    override fun handle(path: String): WebResourceResponse? = if (path == "document.docx")
                        WebResourceResponse("application/vnd.openxmlformats-officedocument.wordprocessingml.document", null,
                            200, "OK", mapOf("Content-Length" to fixture.size.toString()), ByteArrayInputStream(fixture)) else null
                }).build()
            web = WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                installConfirmedSingleTapListener {
                    confirmedTaps.incrementAndGet()
                    tapConfirmed.countDown()
                }
                addJavascriptInterface(object {
                    @JavascriptInterface fun onDocumentVisuallyRendered(payload: String) {
                        signalAfterVisualState(web, ready)
                    }
                    @JavascriptInterface fun onFailure(payload: String) { failures.set(payload); ready.countDown() }
                }, "DocxBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                        request?.url?.let(loader::shouldInterceptRequest)
                    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean =
                        reportRendererGone(failures, ready, detail)
                }
                val density = resources.displayMetrics.density
                attachWebView(activity, this, (412 * density).toInt(), (915 * density).toInt())
                loadUrl("https://appassets.androidplatform.net/assets/docx_viewer/index.html?generation=31&file=" +
                    android.net.Uri.encode("https://appassets.androidplatform.net/docx-src/document.docx"))
            }
        }
        try {
            assertTrue("DOCX render timed out: ${failures.get()}", ready.await(45, TimeUnit.SECONDS))
            assertTrue("DOCX renderer failed: ${failures.get()}", failures.get() == null)
            instrumentation.runOnMainSync {
                assertTrue("Native zoom must be enabled", web.settings.supportZoom())
                assertTrue("Native pinch controls must be enabled", web.settings.builtInZoomControls)
                assertTrue("Native zoom buttons must stay hidden", !web.settings.displayZoomControls)
                assertTrue("Wide viewport support must be enabled", web.settings.useWideViewPort)
                assertTrue("Overview mode must not override 100% initial zoom", !web.settings.loadWithOverviewMode)
                val density = web.resources.displayMetrics.density
                assertTrue("Native WebView starts at exactly 100%: ${web.scale / density}", kotlin.math.abs(web.scale / density - 1f) < .02f)
            }
            repeat(4) {
                instrumentation.runOnMainSync {
                    dispatchPinch(web, SystemClock.uptimeMillis(), 40f * web.resources.displayMetrics.density, 160f * web.resources.displayMetrics.density)
                }
                awaitVisualState(instrumentation, web, "native zoom-in pinch")
            }
            val maxZoom = readVisualViewportScale(instrumentation, web)
            assertTrue("Native page zoom is capped at 500%: $maxZoom", maxZoom <= 5.02f && maxZoom >= 4.9f)
            repeat(6) {
                instrumentation.runOnMainSync {
                    dispatchPinch(web, SystemClock.uptimeMillis(), 160f * web.resources.displayMetrics.density, 40f * web.resources.displayMetrics.density)
                }
                awaitVisualState(instrumentation, web, "native zoom-out pinch")
            }
            val minZoom = readVisualViewportScale(instrumentation, web)
            assertTrue("Native page zoom is floored at 50%: $minZoom", minZoom >= .49f && minZoom <= .52f)
            instrumentation.runOnMainSync {
                dispatchPinch(web, SystemClock.uptimeMillis(), 40f * web.resources.displayMetrics.density, 80f * web.resources.displayMetrics.density)
            }
            awaitVisualState(instrumentation, web, "native zoom recovery pinch")
            val recoveredZoom = readVisualViewportScale(instrumentation, web)
            assertTrue("Native pinch must zoom back in from the 50% floor: $recoveredZoom", recoveredZoom > minZoom && recoveredZoom <= 5.02f)

            val panRead = CountDownLatch(1)
            val panJson = AtomicReference<String?>(null)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("(function(){var style=document.createElement('style');style.textContent='#scaled-document{width:1800px!important}';document.head.appendChild(style);var s=document.getElementById('scroller');s.scrollLeft=0;s.scrollTop=0;var left=s.scrollLeft;var maxX=Math.max(0,s.scrollWidth-s.clientWidth);var maxY=Math.max(0,s.scrollHeight-s.clientHeight);s.scrollLeft=maxX;s.scrollTop=maxY;return JSON.stringify({left:left,right:s.scrollLeft,maxX:maxX,top:0,bottom:s.scrollTop,maxY:maxY,zoom:visualViewport.scale,cssZoom:document.getElementById('scaled-document').style.zoom,transform:getComputedStyle(document.getElementById('scaled-document')).transform})})()") {
                    panJson.set(it); panRead.countDown()
                }
            }
            assertTrue("Could not read native pan boundaries", panRead.await(5, TimeUnit.SECONDS))
            val pan = JSONObject(org.json.JSONTokener(panJson.get()).nextValue().toString())
            assertTrue("Fixture must provide horizontal overflow at native zoom", pan.optDouble("maxX") > 0)
            assertTrue("Native pan must reach the far-left edge: $pan", pan.optDouble("left") == 0.0)
            assertTrue("Native pan must reach the far-right edge: $pan", pan.optDouble("right") >= pan.optDouble("maxX") - 1)
            assertTrue("Native pan must work downward as well as upward: $pan", pan.optDouble("maxY") > 0 && pan.optDouble("bottom") >= pan.optDouble("maxY") - 1)
            assertTrue("Viewer must not use custom CSS zoom: $pan", pan.optString("cssZoom").isEmpty())
            assertTrue("Viewer must not apply a CSS transform: $pan", pan.optString("transform") == "none")

            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                dispatchTap(web, now)
            }
            assertTrue("A confirmed single tap must reach focus mode", tapConfirmed.await(2, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                dispatchTap(web, now)
                dispatchTap(web, now + 130)
            }
            Thread.sleep(500)
            assertTrue("A double tap must remain owned by native Chromium, not toggle focus", confirmedTaps.get() == 1)
        } finally {
            destroyWebView(instrumentation, activity, web)
        }
    }

    private fun dispatchTap(web: WebView, downTime: Long) {
        val x = 100f
        val y = 100f
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 40, MotionEvent.ACTION_UP, x, y, 0)
        try {
            web.dispatchTouchEvent(down)
            web.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun readVisualViewportScale(instrumentation: android.app.Instrumentation, web: WebView): Float {
        val result = AtomicReference<String?>(null)
        val read = CountDownLatch(1)
        instrumentation.runOnMainSync {
            web.evaluateJavascript("window.visualViewport ? window.visualViewport.scale.toString() : '1'") {
                result.set(it?.trim('"'))
                read.countDown()
            }
        }
        assertTrue("Could not read native visual viewport scale", read.await(5, TimeUnit.SECONDS))
        return result.get()?.toFloatOrNull() ?: error("Invalid visual viewport scale: ${result.get()}")
    }

    private fun dispatchPinch(web: WebView, downTime: Long, startDistance: Float, endDistance: Float) {
        val centerX = web.width / 2f
        val centerY = web.height * .4f
        val properties = Array(2) { index ->
            MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }
        var eventTime = downTime
        fun coords(distance: Float): Array<MotionEvent.PointerCoords> = Array(2) { index ->
            MotionEvent.PointerCoords().apply {
                x = centerX + if (index == 0) -distance / 2f else distance / 2f
                y = centerY
                pressure = 1f
                size = 1f
            }
        }
        fun dispatch(action: Int, count: Int, pointers: Array<MotionEvent.PointerProperties>, points: Array<MotionEvent.PointerCoords>) {
            val event = MotionEvent.obtain(
                downTime, eventTime, action, count, pointers, points,
                0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0
            )
            try { web.dispatchTouchEvent(event) } finally { event.recycle() }
            eventTime += 16
        }
        val first = properties[0]
        dispatch(MotionEvent.ACTION_DOWN, 1, arrayOf(first), arrayOf(coords(startDistance)[0]))
        dispatch(
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coords(startDistance)
        )
        for (step in 1..6) {
            val fraction = step / 6f
            val distance = startDistance + (endDistance - startDistance) * fraction
            dispatch(MotionEvent.ACTION_MOVE, 2, properties, coords(distance))
        }
        dispatch(
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coords(endDistance)
        )
        dispatch(MotionEvent.ACTION_UP, 1, arrayOf(first), arrayOf(coords(endDistance)[0]))
    }

    @Test fun printModeUsesTheSameOfflineRendererAndReadinessCallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = launchTestActivity(instrumentation)
        val fixture = fixtureDocx()
        val ready = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/docx-src/", object : WebViewAssetLoader.PathHandler {
                    override fun handle(path: String): WebResourceResponse? = if (path == "document.docx")
                        WebResourceResponse("application/vnd.openxmlformats-officedocument.wordprocessingml.document", null,
                            200, "OK", mapOf("Content-Length" to fixture.size.toString()), ByteArrayInputStream(fixture)) else null
                }).build()
            web = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                addJavascriptInterface(object {
                    @JavascriptInterface fun onDocumentVisuallyRendered(payload: String) {
                        val metrics = JSONObject(payload)
                        if (metrics.optInt("pageCount") > 0 && metrics.optInt("contentHeight") > 0 &&
                            metrics.optInt("visiblePageCount") > 0
                        ) signalAfterVisualState(web, ready)
                    }
                    @JavascriptInterface fun onFailure(payload: String) { failure.set(payload); ready.countDown() }
                }, "DocxBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                        request?.url?.let(loader::shouldInterceptRequest)
                    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean =
                        reportRendererGone(failure, ready, detail)
                }
                val width = (412 * resources.displayMetrics.density).toInt()
                val height = (915 * resources.displayMetrics.density).toInt()
                attachWebView(context, this, width, height)
                loadUrl("https://appassets.androidplatform.net/assets/docx_viewer/index.html?print=1&generation=9&file=" +
                    android.net.Uri.encode("https://appassets.androidplatform.net/docx-src/document.docx"))
            }
        }
        try {
            assertTrue("Print-mode render did not complete: ${failure.get()}", ready.await(45, TimeUnit.SECONDS))
            assertTrue("Print-mode document failure: ${failure.get()}", failure.get() == null)
            val themeCheck = CountDownLatch(1)
            val themeResult = AtomicReference<String?>(null)
            instrumentation.runOnMainSync {
                web.evaluateJavascript(
                    "(function(){DocxViewer.setTheme(true,'#181715','#252320','#faf9f5','#2d2c2a');return getComputedStyle(document.documentElement).getPropertyValue('--paper').trim()+'|'+getComputedStyle(document.documentElement).getPropertyValue('--page-paper').trim()+'|'+(document.getElementById('spacer')===null)+'|'+document.getElementById('scaled-document').style.transform;})()"
                ) { value -> themeResult.set(value); themeCheck.countDown() }
            }
            assertTrue("Theme and layout check timed out", themeCheck.await(5, TimeUnit.SECONDS))
            assertTrue("Theme tokens or normal-flow zoom missing: ${themeResult.get()}",
                themeResult.get()?.contains("#181715|#252320|true|") == true)
        } finally {
            destroyWebView(instrumentation, context, web)
        }
    }

    @Test fun horizontallyOffscreenPagesNeverSignalReady() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = launchTestActivity(instrumentation)
        val fixture = fixtureDocx()
        val ready = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val error = AtomicReference<String?>(null)
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/docx-src/", object : WebViewAssetLoader.PathHandler {
                    override fun handle(path: String): WebResourceResponse? = if (path == "document.docx")
                        WebResourceResponse("application/vnd.openxmlformats-officedocument.wordprocessingml.document", null,
                            200, "OK", mapOf("Content-Length" to fixture.size.toString()), ByteArrayInputStream(fixture)) else null
                }).build()
            web = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                addJavascriptInterface(object {
                    @JavascriptInterface fun onDocumentVisuallyRendered(payload: String) { ready.countDown() }
                    @JavascriptInterface fun onFailure(payload: String) { error.set(payload); failed.countDown() }
                }, "DocxBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                        request?.url?.let(loader::shouldInterceptRequest)
                    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean =
                        reportRendererGone(error, failed, detail)
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript("var s=document.createElement('style');s.textContent='#scaled-document{transform:translateX(-5000px)!important}';document.head.appendChild(s);", null)
                    }
                }
                val width = (360 * resources.displayMetrics.density).toInt()
                val height = (800 * resources.displayMetrics.density).toInt()
                attachWebView(context, this, width, height)
                loadUrl("https://appassets.androidplatform.net/assets/docx_viewer/index.html?generation=12&file=" +
                    android.net.Uri.encode("https://appassets.androidplatform.net/docx-src/document.docx"))
            }
        }
        try {
            assertTrue("Expected actionable failure after forced offscreen layout", failed.await(20, TimeUnit.SECONDS))
            assertTrue("Offscreen page incorrectly reported ready", ready.count == 1L)
            assertTrue("Failure should identify visibility/layout", error.get()?.contains("LAYOUT_FAILED") == true)
        } finally {
            destroyWebView(instrumentation, context, web)
        }
    }

    @Test fun bundledViewerRendersVisibleUnicodeTableAndCrossRunSearch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = launchTestActivity(instrumentation)
        val fixture = fixtureDocx()
        val ready = CountDownLatch(1)
        val searched = CountDownLatch(1)
        val matchCount = AtomicReference(0)
        val failure = AtomicReference<String?>(null)
        val rendererGone = AtomicBoolean(false)
        val result = AtomicReference<JSONObject?>(null)
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/docx-src/", object : WebViewAssetLoader.PathHandler {
                    override fun handle(path: String): WebResourceResponse? = if (path == "document.docx")
                        WebResourceResponse("application/vnd.openxmlformats-officedocument.wordprocessingml.document", null,
                            200, "OK", mapOf("Content-Length" to fixture.size.toString()), ByteArrayInputStream(fixture)) else null
                }).build()
            web = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                setBackgroundColor(Color.rgb(250, 249, 245))
                addJavascriptInterface(object {
                    @JavascriptInterface fun onDocumentVisuallyRendered(payload: String) {
                        result.set(JSONObject(payload))
                        signalAfterVisualState(web, ready)
                    }
                    @JavascriptInterface fun onFailure(payload: String) { failure.set(payload); ready.countDown() }
                    @JavascriptInterface fun onSearchResults(payload: String) {
                        val data = JSONObject(payload)
                        if (data.optInt("total") > 0) { matchCount.set(data.optInt("total")); searched.countDown() }
                    }
                }, "DocxBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                        request?.url?.let(loader::shouldInterceptRequest)
                    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean =
                        reportRendererGone(failure, ready, detail, rendererGone)
                }
                val density = resources.displayMetrics.density
                val width = (360 * density).toInt()
                val height = (800 * density).toInt()
                attachWebView(context, this, width, height)
                loadUrl("https://appassets.androidplatform.net/assets/docx_viewer/index.html?generation=7&file=" +
                    android.net.Uri.encode("https://appassets.androidplatform.net/docx-src/document.docx"))
            }
        }
        try {
            assertTrue("DOCX viewer did not report visible readiness; error=${failure.get()}", ready.await(45, TimeUnit.SECONDS))
            assertTrue("DOCX renderer failed: ${failure.get()}", failure.get() == null)
            assertTrue("Expected visible-render callback with estimated pages", (result.get()?.optInt("pageCount") ?: 0) > 0)
            assertTrue("Visible-render callback must report positive content height", (result.get()?.optInt("contentHeight") ?: 0) > 0)
            assertTrue("Visible-render callback must report an intersecting page", (result.get()?.optInt("visiblePageCount") ?: 0) > 0)
            val captured = Bitmap.createBitmap(web.width, web.height, Bitmap.Config.ARGB_8888)
            instrumentation.runOnMainSync { web.draw(android.graphics.Canvas(captured)) }
            var changedPixels = 0
            for (y in 0 until captured.height step 3) for (x in 0 until captured.width step 3) {
                if (Color.rgb(Color.red(captured.getPixel(x, y)), Color.green(captured.getPixel(x, y)), Color.blue(captured.getPixel(x, y))) != Color.rgb(250, 249, 245)) changedPixels++
            }
            captured.recycle()
            assertTrue("Rendered WebView pixels are indistinguishable from the blank background", changedPixels > 50)
            val evaluated = CountDownLatch(1)
            val dom = AtomicReference<String>()
            instrumentation.runOnMainSync {
                web.evaluateJavascript("DocxViewer.search('styled phrase'); JSON.stringify({text:document.body.innerText,pages:document.querySelectorAll('.docx,section.docx').length,image:(function(){var i=document.querySelector('#scaled-document img');return !!i&&i.complete&&i.naturalWidth>0})(),visible:(function(){var r=document.querySelector('.docx,section.docx').getBoundingClientRect();return r.width>10&&r.height>10&&r.bottom>0&&r.top<innerHeight})(),list:(function(){var e=Array.from(document.querySelectorAll('#scaled-document *')).find(function(n){return n.children.length===0&&n.textContent==='fixture list item'});var p=e&&e.closest('p');return p?getComputedStyle(p).display:''})(),color:(function(){var e=Array.from(document.querySelectorAll('#scaled-document *')).find(function(n){return n.children.length===0&&n.textContent==='colored marker'});return e?getComputedStyle(e).color:''})()})") { value -> dom.set(value); evaluated.countDown() }
            }
            assertTrue("DOM evaluation timed out", evaluated.await(10, TimeUnit.SECONDS))
            val jsonText = JSONObject(org.json.JSONTokener(dom.get()).nextValue().toString())
            assertTrue("Rendered text absent", jsonText.optString("text").contains("हिंदी पाठ"))
            assertTrue("Table content absent", jsonText.optString("text").contains("visible table cell"))
            assertTrue("Word numbering was not rendered as a list item: ${jsonText.optString("list")}",
                jsonText.optString("list") == "list-item")
            assertTrue("Page is not in visible viewport", jsonText.optBoolean("visible"))
            assertTrue("Explicit page break did not create multiple page nodes", jsonText.optInt("pages") >= 2)
            assertTrue("Related image did not load", jsonText.optBoolean("image"))
            assertTrue("Styled run lost its font color", jsonText.optString("color") == "rgb(180, 0, 0)")
            val highlightedColor = AtomicReference<String?>(null)
            val highlightCheck = CountDownLatch(1)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("(function(){var m=document.querySelector('mark.docx-highlight');return m?getComputedStyle(m).backgroundColor:''})()") { value -> highlightedColor.set(value); highlightCheck.countDown() }
            }
            assertTrue("Search highlight did not appear", highlightCheck.await(5, TimeUnit.SECONDS))
            assertTrue("Search result was not visually highlighted: ${highlightedColor.get()}", highlightedColor.get()?.contains("255") == true)
            assertTrue("Cross-run search did not report its match", searched.await(10, TimeUnit.SECONDS))
            assertTrue("One cross-run phrase must count as one logical match", matchCount.get() == 1)

            val searchRendered = CountDownLatch(1)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("DocxViewer.search('styled phrase')") { searchRendered.countDown() }
            }
            assertTrue("Search mutation did not complete", searchRendered.await(5, TimeUnit.SECONDS))
            awaitVisualState(instrumentation, web, "search highlight")
            instrumentation.runOnMainSync {
                val bitmap = Bitmap.createBitmap(web.width, web.height, Bitmap.Config.ARGB_8888)
                web.draw(android.graphics.Canvas(bitmap))
                saveScreenshot(context, bitmap, "docx-360x800.png")
                val bg = Color.rgb(250, 249, 245)
                var changed = 0
                for (y in 0 until bitmap.height step 3) for (x in 0 until bitmap.width step 3) {
                    if (Color.rgb(Color.red(bitmap.getPixel(x, y)), Color.green(bitmap.getPixel(x, y)), Color.blue(bitmap.getPixel(x, y))) != bg) changed++
                }
                bitmap.recycle()
                assertTrue("Rendered WebView pixels are indistinguishable from the blank background", changed > 50)
            }
        } catch (failure: Throwable) {
            if (!rendererGone.get()) captureFailureScreenshot(context, web, "docx-360x800-failure.png")
            throw failure
        } finally {
            destroyWebView(instrumentation, context, web)
        }
    }

    @Test fun viewerRendersVisiblePagesAndNonBlankPixelsAt412x915() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = launchTestActivity(instrumentation)
        val fixture = fixtureDocx()
        val finished = CountDownLatch(1)
        val readiness = AtomicReference<JSONObject?>(null)
        val failure = AtomicReference<String?>(null)
        val rendererGone = AtomicBoolean(false)
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/docx-src/", object : WebViewAssetLoader.PathHandler {
                    override fun handle(path: String): WebResourceResponse? = if (path == "document.docx")
                        WebResourceResponse(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", null,
                            200, "OK", mapOf("Content-Length" to fixture.size.toString()), ByteArrayInputStream(fixture)
                        ) else null
                }).build()
            web = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                setBackgroundColor(Color.rgb(250, 249, 245))
                addJavascriptInterface(object {
                    @JavascriptInterface fun onDocumentVisuallyRendered(payload: String) {
                        readiness.set(JSONObject(payload))
                        signalAfterVisualState(web, finished)
                    }
                    @JavascriptInterface fun onFailure(payload: String) {
                        failure.set(payload)
                        finished.countDown()
                    }
                }, "DocxBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                        request?.url?.let(loader::shouldInterceptRequest)
                    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean =
                        reportRendererGone(failure, finished, detail, rendererGone)
                }
                val density = resources.displayMetrics.density
                val width = (412 * density).toInt()
                val height = (915 * density).toInt()
                attachWebView(context, this, width, height)
                loadUrl("https://appassets.androidplatform.net/assets/docx_viewer/index.html?generation=15&file=" +
                    android.net.Uri.encode("https://appassets.androidplatform.net/docx-src/document.docx"))
            }
        }
        try {
            assertTrue("412x915 visual render timed out: ${failure.get()}", finished.await(45, TimeUnit.SECONDS))
            assertTrue("412x915 renderer failed: ${failure.get()}", failure.get() == null)
            val report = readiness.get() ?: throw AssertionError("No visual readiness report")
            assertTrue(report.optInt("contentHeight") > 0)
            assertTrue(report.optInt("pageCount") >= 2)
            assertTrue(report.optInt("visiblePageCount") > 0)
            instrumentation.runOnMainSync {
                val bitmap = Bitmap.createBitmap(web.width, web.height, Bitmap.Config.ARGB_8888)
                web.draw(android.graphics.Canvas(bitmap))
                saveScreenshot(context, bitmap, "docx-412x915.png")
                var differentPixels = 0
                for (y in 0 until bitmap.height step 3) for (x in 0 until bitmap.width step 3) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.red(pixel) !in 245..255 || Color.green(pixel) !in 244..255 || Color.blue(pixel) !in 240..255) {
                        differentPixels++
                    }
                }
                bitmap.recycle()
                assertTrue("412x915 WebView was visually blank", differentPixels > 50)
            }
        } catch (testFailure: Throwable) {
            if (!rendererGone.get()) captureFailureScreenshot(context, web, "docx-412x915-failure.png")
            throw testFailure
        } finally {
            destroyWebView(instrumentation, context, web)
        }
    }

    private fun launchTestActivity(instrumentation: android.app.Instrumentation): Activity {
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val activity = instrumentation.startActivitySync(intent)
        instrumentation.runOnMainSync { activity.setContentView(FrameLayout(activity)) }
        return activity
    }

    private fun attachWebView(activity: Activity, web: WebView, width: Int, height: Int) {
        activity.addContentView(web, FrameLayout.LayoutParams(width, height))
        web.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        web.layout(0, 0, width, height)
    }

    private fun signalAfterVisualState(web: WebView, latch: CountDownLatch) {
        val posted = web.post {
            web.postVisualStateCallback(
                visualStateRequestIds.incrementAndGet(),
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        latch.countDown()
                    }
                }
            )
        }
        if (!posted) latch.countDown()
    }

    private fun awaitVisualState(instrumentation: android.app.Instrumentation, web: WebView, description: String) {
        val rendered = CountDownLatch(1)
        instrumentation.runOnMainSync {
            web.postVisualStateCallback(
                visualStateRequestIds.incrementAndGet(),
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        rendered.countDown()
                    }
                }
            )
        }
        assertTrue("WebView did not commit visual state for $description", rendered.await(10, TimeUnit.SECONDS))
    }

    private fun reportRendererGone(
        failure: AtomicReference<String?>,
        completed: CountDownLatch,
        detail: android.webkit.RenderProcessGoneDetail,
        rendererGone: AtomicBoolean? = null
    ): Boolean {
        rendererGone?.set(true)
        failure.set("RENDERER_UNAVAILABLE (didCrash=${detail.didCrash()})")
        completed.countDown()
        return true
    }

    private fun destroyWebView(instrumentation: android.app.Instrumentation, activity: Activity, web: WebView) {
        instrumentation.runOnMainSync {
            web.stopLoading()
            web.removeJavascriptInterface("DocxBridge")
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
            activity.finish()
        }
    }

    private fun saveScreenshot(context: android.content.Context, bitmap: Bitmap, name: String) {
        val directory = File(context.cacheDir, "docx-test-screenshots")
        if (!directory.exists()) directory.mkdirs()
        FileOutputStream(File(directory, name)).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun captureFailureScreenshot(context: android.content.Context, web: WebView, name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = AtomicReference<Bitmap?>(null)
        instrumentation.runOnMainSync {
            if (web.width > 0 && web.height > 0) {
                val captured = Bitmap.createBitmap(web.width, web.height, Bitmap.Config.ARGB_8888)
                web.draw(android.graphics.Canvas(captured))
                bitmap.set(captured)
            }
        }
        bitmap.get()?.let { captured ->
            saveScreenshot(context, captured, name)
            captured.recycle()
        }
    }

    private fun fixtureDocx(): ByteArray {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>
            <w:p><w:r><w:t>styled </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>phrase</w:t></w:r></w:p>
            <w:p><w:r><w:t>हिंदी पाठ</w:t></w:r></w:p>
            <w:p><w:r><w:rPr><w:color w:val="B40000"/></w:rPr><w:t>colored marker</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>fixture list item</w:t></w:r></w:p>
            <w:tbl><w:tblPr><w:tblW w:w="0" w:type="auto"/></w:tblPr><w:tr><w:tc><w:p><w:r><w:t>visible table cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
            <w:p><w:r><w:drawing><wp:inline><wp:extent cx="914400" cy="914400"/><wp:docPr id="1" name="fixture image"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="0" name="fixture.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdImg"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="914400" cy="914400"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
            <w:p><w:r><w:br w:type="page"/><w:t>Second page marker</w:t></w:r></w:p>
            <w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
            </w:body></w:document>""".trimIndent()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) { zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("[Content_Types].xml", """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry("word/_rels/document.xml.rels", """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdImg" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/fixture.png"/><Relationship Id="rIdNum" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/></Relationships>""")
            entry("word/numbering.xml", """<?xml version="1.0"?><w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="singleLevel"/><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num></w:numbering>""")
            entry("word/document.xml", xml)
            zip.putNextEntry(ZipEntry("word/media/fixture.png"))
            zip.write(Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jE9sAAAAASUVORK5CYII=", Base64.DEFAULT))
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
