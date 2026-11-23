package com.example.ui.preview.docx

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
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
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import android.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DocxPreviewInstrumentationTest {
    private val visualStateRequestIds = AtomicLong()

    @Test fun defaultZoomSettlesAndRotationKeepsTheSameDocumentAndReadingPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = fixtureDocx()
        val ready = CountDownLatch(1)
        val failures = AtomicReference<String?>(null)
        val zoomReports = java.util.concurrent.CopyOnWriteArrayList<Float>()
        val effectiveScaleReports = java.util.concurrent.CopyOnWriteArrayList<Pair<Long, Float>>()
        val singleTaps = java.util.concurrent.atomic.AtomicInteger(0)
        val doubleTaps = java.util.concurrent.atomic.AtomicInteger(0)
        val singleTapDelivered = CountDownLatch(1)
        val doubleTapDelivered = CountDownLatch(1)
        val finishedPages = java.util.concurrent.atomic.AtomicInteger(0)
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
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                addJavascriptInterface(object {
                    @JavascriptInterface fun onDocumentVisuallyRendered(payload: String) {
                        signalAfterVisualState(web, ready)
                    }
                    @JavascriptInterface fun onFailure(payload: String) { failures.set(payload); ready.countDown() }
                    @JavascriptInterface fun onZoomChanged(payload: String) {
                        val zoom = JSONObject(payload)
                        zoomReports.add(zoom.optDouble("zoom", -1.0).toFloat())
                        effectiveScaleReports.add(
                            SystemClock.elapsedRealtime() to zoom.optDouble("effectiveScale", -1.0).toFloat()
                        )
                    }
                    @JavascriptInterface fun onDocumentTap() {
                        singleTaps.incrementAndGet()
                        singleTapDelivered.countDown()
                    }
                    @JavascriptInterface fun onDocumentDoubleTap(payload: String) {
                        doubleTaps.incrementAndGet()
                        doubleTapDelivered.countDown()
                    }
                }, "DocxBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                        request?.url?.let(loader::shouldInterceptRequest)
                    override fun onPageFinished(view: WebView?, url: String?) { finishedPages.incrementAndGet() }
                }
                val density = resources.displayMetrics.density
                val width = (412 * density).toInt()
                val height = (915 * density).toInt()
                measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                layout(0, 0, width, height)
                loadUrl("https://appassets.androidplatform.net/assets/docx_viewer/index.html?generation=31&file=" +
                    android.net.Uri.encode("https://appassets.androidplatform.net/docx-src/document.docx"))
            }
        }
        try {
            assertTrue("DOCX render timed out: ${failures.get()}", ready.await(45, TimeUnit.SECONDS))
            assertTrue("DOCX renderer failed: ${failures.get()}", failures.get() == null)
            assertTrue("Opening the document must report the default user zoom of 1.0: ${zoomReports}",
                zoomReports.isNotEmpty() && zoomReports.all { kotlin.math.abs(it - 1f) < .001f })
            val reportedScales = effectiveScaleReports.map { (it.second * 10000).toInt() }.distinct()
            assertTrue("Fit scale should settle instead of oscillating: $reportedScales", reportedScales.size <= 1)

            val prepared = CountDownLatch(1)
            val position = AtomicReference<String?>(null)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("(function(){DocxViewer.setZoom(99);var high=DocxViewer.getState().zoom;DocxViewer.setZoom(.1);var low=DocxViewer.getState().zoom;DocxViewer.setZoom(1.75);var s=document.getElementById('scroller');var p=document.querySelectorAll('.docx,section.docx')[1];s.scrollTop+=p.getBoundingClientRect().top-s.getBoundingClientRect().top+80;return JSON.stringify({zoom:DocxViewer.getState().zoom,high:high,low:low,scroll:s.scrollTop})})()") {
                    position.set(it); prepared.countDown()
                }
            }
            assertTrue("Could not prepare the reader position", prepared.await(5, TimeUnit.SECONDS))
            val before = JSONObject(org.json.JSONTokener(position.get()).nextValue().toString())
            assertTrue("Zoom must clamp to 5.0 at the upper bound: ${before.optDouble("high")}", before.optDouble("high") == 5.0)
            assertTrue("Zoom must clamp to 0.5 at the lower bound: ${before.optDouble("low")}", before.optDouble("low") == 0.5)
            assertTrue("Test fixture must be scrolled into page two", before.optDouble("scroll") > 0.0)

            instrumentation.runOnMainSync {
                val density = web.resources.displayMetrics.density
                val width = (915 * density).toInt()
                val height = (412 * density).toInt()
                web.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                web.layout(0, 0, width, height)
            }
            val resizeLayout = CountDownLatch(1)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("document.documentElement.getBoundingClientRect().width") { resizeLayout.countDown() }
            }
            assertTrue("Resize layout did not reach the DOM", resizeLayout.await(5, TimeUnit.SECONDS))
            awaitVisualState(instrumentation, web, "rotation-sized resize")
            val afterLatch = CountDownLatch(1)
            val afterValue = AtomicReference<String?>(null)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("JSON.stringify({zoom:DocxViewer.getState().zoom,scroll:document.getElementById('scroller').scrollTop,loads:document.querySelectorAll('.docx,section.docx').length})") {
                    afterValue.set(it); afterLatch.countDown()
                }
            }
            assertTrue("Could not read document state after rotation-sized resize", afterLatch.await(5, TimeUnit.SECONDS))
            val after = JSONObject(org.json.JSONTokener(afterValue.get()).nextValue().toString())
            assertTrue("Zoom must survive a viewport rotation: ${after.optDouble("zoom")}",
                kotlin.math.abs(after.optDouble("zoom") - 1.75) < .001)
            assertTrue("Scroll position must remain in the same document rather than reset: ${after.optDouble("scroll")}",
                after.optDouble("scroll") > 0.0)
            assertTrue("Resize must not replace/reload the rendered document",
                after.optInt("loads") >= 2 && finishedPages.get() == 1)

            val singleClick = CountDownLatch(1)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("document.getElementById('scroller').dispatchEvent(new MouseEvent('click',{bubbles:true,clientX:20,clientY:20}))") {
                    singleClick.countDown()
                }
            }
            assertTrue("Single click dispatch timed out", singleClick.await(5, TimeUnit.SECONDS))
            assertTrue("Single tap should toggle focus once", singleTapDelivered.await(5, TimeUnit.SECONDS))
            assertTrue("Single tap should not be treated as a double tap", singleTaps.get() == 1 && doubleTaps.get() == 0)

            val doubleClick = CountDownLatch(1)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("(function(){var e=document.getElementById('scroller');var a={bubbles:true,clientX:30,clientY:30};e.dispatchEvent(new MouseEvent('click',a));e.dispatchEvent(new MouseEvent('click',a));return true})()") {
                    doubleClick.countDown()
                }
            }
            assertTrue("Double-click dispatch timed out", doubleClick.await(5, TimeUnit.SECONDS))
            assertTrue("Double tap callback timed out", doubleTapDelivered.await(5, TimeUnit.SECONDS))
            assertTrue("Double tap must zoom without producing two single-tap focus toggles",
                singleTaps.get() == 1 && doubleTaps.get() == 1)
            val finalZoom = CountDownLatch(1)
            val finalZoomValue = AtomicReference<String?>(null)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("JSON.stringify({zoom:DocxViewer.getState().zoom})") {
                    finalZoomValue.set(it); finalZoom.countDown()
                }
            }
            assertTrue("Final zoom query timed out", finalZoom.await(5, TimeUnit.SECONDS))
            val finalZoomJson = JSONObject(org.json.JSONTokener(finalZoomValue.get()).nextValue().toString())
            assertTrue("Double tap should return 1.75x to the default 1.0x", kotlin.math.abs(finalZoomJson.optDouble("zoom") - 1.0) < .001)
        } finally {
            destroyWebView(instrumentation, web)
        }
    }

    @Test fun printModeUsesTheSameOfflineRendererAndReadinessCallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
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
                settings.useWideViewPort = false
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
                }
                val width = (412 * resources.displayMetrics.density).toInt()
                val height = (915 * resources.displayMetrics.density).toInt()
                measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                layout(0, 0, width, height)
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
            destroyWebView(instrumentation, web)
        }
    }

    @Test fun horizontallyOffscreenPagesNeverSignalReady() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
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
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                addJavascriptInterface(object {
                    @JavascriptInterface fun onDocumentVisuallyRendered(payload: String) { ready.countDown() }
                    @JavascriptInterface fun onFailure(payload: String) { error.set(payload); failed.countDown() }
                }, "DocxBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                        request?.url?.let(loader::shouldInterceptRequest)
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript("var s=document.createElement('style');s.textContent='#scaled-document{transform:translateX(-5000px)!important}';document.head.appendChild(s);", null)
                    }
                }
                val width = (360 * resources.displayMetrics.density).toInt()
                val height = (800 * resources.displayMetrics.density).toInt()
                measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                layout(0, 0, width, height)
                loadUrl("https://appassets.androidplatform.net/assets/docx_viewer/index.html?generation=12&file=" +
                    android.net.Uri.encode("https://appassets.androidplatform.net/docx-src/document.docx"))
            }
        }
        try {
            assertTrue("Expected actionable failure after forced offscreen layout", failed.await(20, TimeUnit.SECONDS))
            assertTrue("Offscreen page incorrectly reported ready", ready.count == 1L)
            assertTrue("Failure should identify visibility/layout", error.get()?.contains("LAYOUT_FAILED") == true)
        } finally {
            destroyWebView(instrumentation, web)
        }
    }

    @Test fun bundledViewerRendersVisibleUnicodeTableAndCrossRunSearch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = fixtureDocx()
        val ready = CountDownLatch(1)
        val searched = CountDownLatch(1)
        val matchCount = AtomicReference(0)
        val failure = AtomicReference<String?>(null)
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
                settings.useWideViewPort = false
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
                    @JavascriptInterface fun onZoomChanged(payload: String) { }
                }, "DocxBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                        request?.url?.let(loader::shouldInterceptRequest)
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript("DocxViewer.setZoom(1)", null)
                    }
                }
                val density = resources.displayMetrics.density
                val width = (360 * density).toInt()
                val height = (800 * density).toInt()
                measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                layout(0, 0, width, height)
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
            captureFailureScreenshot(context, web, "docx-360x800-failure.png")
            throw failure
        } finally {
            destroyWebView(instrumentation, web)
        }
    }

    @Test fun viewerRendersVisiblePagesAndNonBlankPixelsAt412x915() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = fixtureDocx()
        val finished = CountDownLatch(1)
        val readiness = AtomicReference<JSONObject?>(null)
        val failure = AtomicReference<String?>(null)
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
                settings.useWideViewPort = false
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
                }
                val density = resources.displayMetrics.density
                val width = (412 * density).toInt()
                val height = (915 * density).toInt()
                measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                layout(0, 0, width, height)
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
            captureFailureScreenshot(context, web, "docx-412x915-failure.png")
            throw testFailure
        } finally {
            destroyWebView(instrumentation, web)
        }
    }

    private fun signalAfterVisualState(web: WebView, latch: CountDownLatch) {
        val posted = web.post {
            web.postVisualStateCallback(visualStateRequestIds.incrementAndGet()) {
                latch.countDown()
            }
        }
        if (!posted) latch.countDown()
    }

    private fun awaitVisualState(instrumentation: android.app.Instrumentation, web: WebView, description: String) {
        val rendered = CountDownLatch(1)
        instrumentation.runOnMainSync {
            web.postVisualStateCallback(visualStateRequestIds.incrementAndGet()) {
                rendered.countDown()
            }
        }
        assertTrue("WebView did not commit visual state for $description", rendered.await(10, TimeUnit.SECONDS))
    }

    private fun destroyWebView(instrumentation: android.app.Instrumentation, web: WebView) {
        instrumentation.runOnMainSync {
            web.stopLoading()
            web.removeJavascriptInterface("DocxBridge")
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
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
