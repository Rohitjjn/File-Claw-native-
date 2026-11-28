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
import org.junit.Assert.assertFalse
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
        val launchedIntent = AtomicReference<Intent?>(null)
        val linkIntentDispatched = CountDownLatch(1)
        val linkTapResolved = CountDownLatch(1)
        val resolvedTapCount = java.util.concurrent.atomic.AtomicInteger()
        val zoomAnimationScaleSamples = java.util.concurrent.CopyOnWriteArrayList<Float>()
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
                installDocxGestureListeners(
                    onSingleTapConfirmed = { event ->
                        dispatchSingleTapIfNotLink(
                            event,
                            onTap = {
                                confirmedTaps.incrementAndGet()
                                tapConfirmed.countDown()
                            },
                            onResolved = {
                                if (resolvedTapCount.incrementAndGet() >= 1) linkTapResolved.countDown()
                            }
                        )
                    },
                    onDoubleTap = { event ->
                        animateDocxDoubleTapZoom(event, onScaleFrame = { scale -> zoomAnimationScaleSamples.add(scale) })
                    }
                )
                addJavascriptInterface(object {
                    @JavascriptInterface
                    @Suppress("UNUSED_PARAMETER")
                    fun onDocumentVisuallyRendered(payload: String) {
                        signalAfterVisualState(web, ready)
                    }
                    @JavascriptInterface fun onFailure(payload: String) { failures.set(payload); ready.countDown() }
                }, "DocxBridge")
                val launchContext = RecordingContext(activity, launchedIntent, linkIntentDispatched)
                webViewClient = DocxWebViewClient(
                    assets = loader,
                    onFailure = { code -> failures.set(code); ready.countDown() },
                    onExternalUrl = { uri -> launchDocxExternalLink(launchContext, uri) { } }
                )
                val density = resources.displayMetrics.density
                attachWebView(activity, this, (412 * density).toInt(), (915 * density).toInt())
                loadUrl("https://appassets.androidplatform.net/assets/docx_viewer/index.html?generation=31&file=" +
                    android.net.Uri.encode("https://appassets.androidplatform.net/docx-src/document.docx"))
            }
        }
        try {
            assertTrue("DOCX render timed out: ${failures.get()}", ready.await(45, TimeUnit.SECONDS))
            assertTrue("DOCX renderer failed: ${failures.get()}", failures.get() == null)
            val fitScale = evaluateJson(instrumentation, web, "JSON.stringify(DocxViewer.getState())").optDouble("fitScale").toFloat()
            instrumentation.runOnMainSync {
                assertTrue("Native zoom must be enabled", web.settings.supportZoom())
                assertTrue("Native pinch controls must be enabled", web.settings.builtInZoomControls)
                assertTrue("Native zoom buttons must stay hidden", !web.settings.displayZoomControls)
                assertTrue("Wide viewport support must be enabled", web.settings.useWideViewPort)
                assertTrue("Overview mode must stay disabled so Chromium cannot reset the fit layout", !web.settings.loadWithOverviewMode)
            }
            val initialScale = readVisualViewportScale(instrumentation, web)
            assertTrue("Initial page zoom must equal the measured fit baseline: $initialScale fit=$fitScale",
                kotlin.math.abs(initialScale - fitScale) < .03f)
            val initialLayout = evaluateJson(instrumentation, web,
                "(function(){var s=document.getElementById('scroller'),p=document.querySelector('#scaled-document .docx, #scaled-document section.docx'),r=p.getBoundingClientRect(),v=visualViewport,z=v.scale;return JSON.stringify({clientWidth:s.clientWidth,scrollWidth:s.scrollWidth,visualViewportWidth:v.width,pageWidth:r.width*z,pageHeight:r.height*z,pageLeft:(r.left-v.offsetLeft)*z,pageRight:(r.right-v.offsetLeft)*z,viewportWidth:v.width*z,pageScrollWidth:p.scrollWidth,pageClientWidth:p.clientWidth,fitTransform:document.getElementById('scaled-document').style.transform,computedTransform:getComputedStyle(document.getElementById('scaled-document')).transform,layoutViewportWidth:document.documentElement.clientWidth,fitScale:DocxViewer.getState().fitScale});})()")
            assertTrue("DOCX page must fit within the viewport at fit baseline: $initialLayout",
                initialLayout.optDouble("pageWidth") > 0 && initialLayout.optDouble("pageRight") <= initialLayout.optDouble("viewportWidth"))
            val leftMargin = initialLayout.optDouble("pageLeft")
            val rightMargin = initialLayout.optDouble("viewportWidth") - initialLayout.optDouble("pageRight")
            assertTrue("Fit-to-width page must have balanced 12px margins: $initialLayout",
                kotlin.math.abs(leftMargin - 12) <= 2 && kotlin.math.abs(rightMargin - 12) <= 2)
            assertSingleWrapperAndStableIdleLayout(instrumentation, web)
            assertTrue("Opening the DOCX must not clip the page horizontally: $initialLayout",
                initialLayout.optDouble("scrollWidth") <= initialLayout.optDouble("visualViewportWidth") + 1)
            val pageTwoBefore = evaluateJson(instrumentation, web,
                "(function(){var p=Array.from(document.querySelectorAll('#scaled-document p')).find(function(n){return n.textContent.indexOf('Second page marker')>=0}),s=document.getElementById('scroller');if(!p)return JSON.stringify({found:false});p.scrollIntoView({block:'center'});var r=p.getBoundingClientRect(),sr=s.getBoundingClientRect();return JSON.stringify({found:true,top:r.top-sr.top,scrollTop:s.scrollTop,text:p.textContent});})()")
            assertTrue("The multi-page zoom regression must start on page two: $pageTwoBefore", pageTwoBefore.optBoolean("found"))
            val tapPoint = AtomicReference<FloatArray>()
            instrumentation.runOnMainSync {
                tapPoint.set(floatArrayOf(web.width * .33f, web.height * .62f, web.resources.displayMetrics.density))
            }
            fun documentPointAtTap(): JSONObject {
                val point = tapPoint.get()
                val x = point[0].toDouble().toString()
                val y = point[1].toDouble().toString()
                val density = point[2].toDouble().toString()
                return evaluateJson(instrumentation, web,
                    "(function(){var s=document.getElementById('scroller'),v=window.visualViewport,d=document.getElementById('scaled-document'),r=d.getBoundingClientRect(),sr=s.getBoundingClientRect();return JSON.stringify({x:$x/($density*v.scale)+v.offsetLeft-(r.left-sr.left),y:$y/($density*v.scale)+v.offsetTop-(r.top-sr.top),offsetLeft:v.offsetLeft,offsetTop:v.offsetTop,scale:v.scale,scrollerLeft:s.scrollLeft,scrollerTop:s.scrollTop,scrollerMaxLeft:s.scrollWidth-s.clientWidth,scrollerMaxTop:s.scrollHeight-s.clientHeight,docLeft:d.offsetLeft,docTop:d.offsetTop,docRectLeft:r.left,docRectTop:r.top,scrollerRectLeft:sr.left,scrollerRectTop:sr.top});})()")
            }
            val anchorBeforeZoom = documentPointAtTap()
            val doubleTapStartedAt = SystemClock.elapsedRealtime()
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                dispatchTap(web, now, tapPoint.get()[0], tapPoint.get()[1])
                dispatchTap(web, now + 130, tapPoint.get()[0], tapPoint.get()[1])
            }
            awaitVisualViewportScale(instrumentation, web, fitScale * 4f)
            val intermediateAnimationScales = zoomAnimationScaleSamples.filter {
                it > initialScale + .02f && it < fitScale * 4f - .02f
            }.distinct()
            assertTrue(
                "Double-tap should deliver at least 10 vsync animation frames: start=$initialScale samples=$zoomAnimationScaleSamples target=${fitScale * 4f}",
                intermediateAnimationScales.size >= 10
            )
            val zoomRequest = evaluateJson(instrumentation, web, "JSON.stringify(window.__docxZoomRequest)")
            assertTrue("Double-tap must compute its target from the measured fit scale: $zoomRequest fit=$fitScale",
                kotlin.math.abs(zoomRequest.optDouble("fitScale") - fitScale) < .03 &&
                    kotlin.math.abs(zoomRequest.optDouble("target") - fitScale * 4.0) < .04)
            var anchorAt400Percent = documentPointAtTap()
            val anchorDeadline = SystemClock.elapsedRealtime() + 3_500L
            while ((kotlin.math.abs(anchorBeforeZoom.optDouble("x") - anchorAt400Percent.optDouble("x")) >= 2.0 ||
                    kotlin.math.abs(anchorBeforeZoom.optDouble("y") - anchorAt400Percent.optDouble("y")) >= 2.0) &&
                SystemClock.elapsedRealtime() < anchorDeadline
            ) {
                Thread.sleep(50)
                anchorAt400Percent = documentPointAtTap()
            }
            val anchorCorrection = evaluateJson(instrumentation, web, "JSON.stringify(window.__docxAnchorCorrection||{})")
            assertTrue("Animated double-tap must keep the document point under the tap while zooming in: before=$anchorBeforeZoom after=$anchorAt400Percent correction=$anchorCorrection",
                kotlin.math.abs(anchorBeforeZoom.optDouble("x") - anchorAt400Percent.optDouble("x")) < 2.0 &&
                    kotlin.math.abs(anchorBeforeZoom.optDouble("y") - anchorAt400Percent.optDouble("y")) < 2.0)
            val correctionFrames = evaluateJson(instrumentation, web,
                "JSON.stringify({frames:window.__docxZoomCorrectionFrames||0})").optInt("frames")
            val correctionStats = evaluateJson(instrumentation, web,
                "JSON.stringify(window.__docxZoomCorrectionStop||{})")
            val androidAnimatorScale = android.animation.ValueAnimator.getDurationScale()
            val doubleTapElapsedMs = SystemClock.elapsedRealtime() - doubleTapStartedAt
            assertTrue("Focal correction should run in Chromium during native zoom: frames=$correctionFrames stats=$correctionStats animatorScale=$androidAnimatorScale elapsedMs=$doubleTapElapsedMs", correctionFrames >= 1)
            assertTrue("Double-tap must not trigger focus mode", confirmedTaps.get() == 0)
            instrumentation.runOnMainSync {
                val now = SystemClock.uptimeMillis()
                dispatchTap(web, now, tapPoint.get()[0], tapPoint.get()[1])
                dispatchTap(web, now + 130, tapPoint.get()[0], tapPoint.get()[1])
            }
            awaitVisualViewportScale(instrumentation, web, fitScale)
            assertTrue("Second double-tap must return to 100% fit width", kotlin.math.abs(readVisualViewportScale(instrumentation, web) - fitScale) < .03f)
            val pageTwoAfter = evaluateJson(instrumentation, web,
                "(function(){var p=Array.from(document.querySelectorAll('#scaled-document p')).find(function(n){return n.textContent.indexOf('Second page marker')>=0}),s=document.getElementById('scroller');if(!p)return JSON.stringify({found:false});var r=p.getBoundingClientRect(),sr=s.getBoundingClientRect();return JSON.stringify({found:true,top:r.top-sr.top,scrollTop:s.scrollTop,text:p.textContent});})()")
            assertTrue("Zooming back to fit must preserve the current page and paragraph: before=$pageTwoBefore after=$pageTwoAfter",
                pageTwoAfter.optBoolean("found") && pageTwoAfter.optDouble("scrollTop") > 0.0 &&
                    kotlin.math.abs(pageTwoBefore.optDouble("top") - pageTwoAfter.optDouble("top")) < 48.0)
            val zoomSettleDeadline = SystemClock.elapsedRealtime() + 5_000L
            var zoomStillSettling = evaluateJson(instrumentation, web,
                "JSON.stringify({active:!!window.__docxDoubleTapZoomActive})").optBoolean("active")
            while (zoomStillSettling && SystemClock.elapsedRealtime() < zoomSettleDeadline) {
                Thread.sleep(50)
                zoomStillSettling = evaluateJson(instrumentation, web,
                    "JSON.stringify({active:!!window.__docxDoubleTapZoomActive})").optBoolean("active")
            }
            assertFalse("Double-tap focal correction must settle before subsequent document navigation",
                zoomStillSettling)

            instrumentation.runOnMainSync { dispatchTap(web, SystemClock.uptimeMillis()) }
            assertTrue("A confirmed single tap must reach focus mode", tapConfirmed.await(2, TimeUnit.SECONDS))
            evaluateJson(instrumentation, web,
                "(function(){var a=document.querySelector('#scaled-document a[href^=\\\"https://\\\"]');if(a)a.scrollIntoView({block:'center',inline:'center'});return JSON.stringify({found:!!a});})()")
            awaitVisualState(instrumentation, web, "hyperlink scrolled into view after page-preserving zoom-out")
            val anchor = evaluateJson(instrumentation, web,
                "(function(){var a=document.querySelector('#scaled-document a[href^=\\\"https://\\\"]');if(!a)return JSON.stringify({found:false});var r=a.getBoundingClientRect(),q=document.createRange();q.selectNodeContents(a);var t=q.getBoundingClientRect();var x=r.left+r.width/2,y=r.top+r.height/2,n=document.elementFromPoint(x,y),hitX=NaN,hitY=NaN,hitNode=null;for(var yy=Math.ceil(r.top);yy<r.bottom&&!Number.isFinite(hitX);yy+=2){for(var xx=Math.ceil(r.left);xx<r.right;xx+=2){var candidate=document.elementFromPoint(xx,yy);if(candidate&&candidate.closest&&candidate.closest('a[href]')===a){hitX=xx;hitY=yy;hitNode=candidate;break;}}}var s=document.getElementById('scroller');var d=document.getElementById('scaled-document');return JSON.stringify({found:true,x:Number.isFinite(hitX)?hitX:x,y:Number.isFinite(hitY)?hitY:y,width:innerWidth,height:innerHeight,hit:Number.isFinite(hitX),topNode:n?n.outerHTML:null,hitNode:hitNode?hitNode.outerHTML:null,anchor:a.outerHTML,anchorWidth:r.width,anchorHeight:r.height,textWidth:t.width,textHeight:t.height,pointerEvents:getComputedStyle(a).pointerEvents,offsetLeft:visualViewport.offsetLeft,offsetTop:visualViewport.offsetTop,scrollTop:s.scrollTop,docTop:d.offsetTop,docRect:d.getBoundingClientRect().top,scale:visualViewport.scale,statusHidden:document.getElementById('status').hidden});})()")
            assertTrue("DOCX fixture hyperlink must be visible at a real hit-test point: $anchor", anchor.optBoolean("found") && anchor.optBoolean("hit"))
            instrumentation.runOnMainSync {
                val density = web.resources.displayMetrics.density
                val pageScale = anchor.optDouble("scale", 1.0).toFloat()
                dispatchTap(web, SystemClock.uptimeMillis(),
                    (anchor.optDouble("x") * density * pageScale).toFloat(),
                    (anchor.optDouble("y") * density * pageScale).toFloat())
            }
            assertTrue("Hyperlink tap must resolve without toggling focus: confirmed=${confirmedTaps.get()} resolved=${resolvedTapCount.get()} anchor=$anchor",
                linkTapResolved.await(3, TimeUnit.SECONDS) && confirmedTaps.get() == 1)
            assertTrue("DOCX HTTPS link must dispatch ACTION_VIEW", linkIntentDispatched.await(5, TimeUnit.SECONDS))
            assertTrue("External intent must carry HTTPS ACTION_VIEW and the hyperlink URL",
                launchedIntent.get()?.action == Intent.ACTION_VIEW && launchedIntent.get()?.dataString == "https://example.com/docx-test")
            val zoomInProgress = mutableListOf<Float>()
            repeat(8) {
                instrumentation.runOnMainSync {
                    dispatchPinch(web, SystemClock.uptimeMillis(), 40f * web.resources.displayMetrics.density, 160f * web.resources.displayMetrics.density)
                }
                awaitVisualState(instrumentation, web, "native zoom-in pinch")
                zoomInProgress += readVisualViewportScale(instrumentation, web)
            }
            val maxZoom = readVisualViewportScale(instrumentation, web)
            assertTrue("Native page zoom is capped at 1000% of fit: $maxZoom after $zoomInProgress fit=$fitScale",
                maxZoom <= 10.02f && maxZoom >= 9.8f)
            val zoomOutProgress = mutableListOf<Float>()
            repeat(6) {
                instrumentation.runOnMainSync {
                    dispatchPinch(web, SystemClock.uptimeMillis(), 160f * web.resources.displayMetrics.density, 40f * web.resources.displayMetrics.density)
                }
                awaitVisualState(instrumentation, web, "native zoom-out pinch")
                zoomOutProgress += readVisualViewportScale(instrumentation, web)
            }
            val minZoom = readVisualViewportScale(instrumentation, web)
            val pinchBounds = evaluateJson(instrumentation, web,
                "(function(){var s=document.getElementById('scroller'),d=document.getElementById('scaled-document');return JSON.stringify({viewport:document.getElementById('docx-viewport').content,fit:DocxViewer.getState().fitScale,scale:visualViewport.scale,visualViewportWidth:visualViewport.width,layoutViewportWidth:document.documentElement.clientWidth,htmlScrollWidth:document.documentElement.scrollWidth,bodyClientWidth:document.body.clientWidth,bodyScrollWidth:document.body.scrollWidth,bodyComputedWidth:getComputedStyle(document.body).width,scrollerClientWidth:s.clientWidth,scrollerScrollWidth:s.scrollWidth,documentClientWidth:d.clientWidth,documentScrollWidth:d.scrollWidth});})()")
            assertTrue("Native page zoom is floored at 50% of fit: $minZoom after $zoomOutProgress fit=$fitScale bounds=$pinchBounds",
                minZoom >= .49f && minZoom <= .52f)
            repeat(4) {
                instrumentation.runOnMainSync {
                    dispatchPinch(web, SystemClock.uptimeMillis(), 40f * web.resources.displayMetrics.density, 160f * web.resources.displayMetrics.density)
                }
                awaitVisualState(instrumentation, web, "native zoom recovery pinch")
            }
            val recoveredZoom = readVisualViewportScale(instrumentation, web)
            assertTrue("Native pinch must recover toward fit from the 50% floor: $recoveredZoom fit=$fitScale",
                recoveredZoom >= .9f && recoveredZoom <= 10.02f)

            val panRead = CountDownLatch(1)
            val panJson = AtomicReference<String?>(null)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("(function(){var style=document.createElement('style');style.textContent='#scaled-document{width:1800px!important;max-width:none!important;flex:0 0 1800px!important}';document.head.appendChild(style);var s=document.getElementById('scroller');s.scrollLeft=0;s.scrollTop=0;var left=s.scrollLeft;var top=s.scrollTop;s.scrollLeft=Number.MAX_SAFE_INTEGER;s.scrollTop=Number.MAX_SAFE_INTEGER;var right=s.scrollLeft;var bottom=s.scrollTop;s.scrollLeft=right+100;s.scrollTop=bottom+100;var p=document.querySelector('#scaled-document .docx, #scaled-document section.docx');return JSON.stringify({left:left,right:right,maxX:right,top:top,bottom:bottom,maxY:bottom,afterRight:s.scrollLeft,afterBottom:s.scrollTop,zoom:visualViewport.scale,fitTransform:document.getElementById('scaled-document').style.transform,transform:getComputedStyle(document.getElementById('scaled-document')).transform})})()") {
                    panJson.set(it); panRead.countDown()
                }
            }
            assertTrue("Could not read native pan boundaries", panRead.await(5, TimeUnit.SECONDS))
            val pan = JSONObject(org.json.JSONTokener(panJson.get()).nextValue().toString())
            assertTrue("Fixture must provide horizontal overflow at native zoom", pan.optDouble("maxX") > 0)
            assertTrue("Native pan must reach the far-left edge: $pan", pan.optDouble("left") == 0.0)
            assertTrue("Native pan must reach the far-right edge: $pan", pan.optDouble("right") >= pan.optDouble("maxX") - 1)
            assertTrue("Native pan must stop at the far-right limit: $pan", pan.optDouble("afterRight") <= pan.optDouble("right") + 1)
            assertTrue("Native pan must work downward as well as upward: $pan", pan.optDouble("maxY") > 0 && pan.optDouble("bottom") >= pan.optDouble("maxY") - 1)
            assertTrue("Native pan must stop at the bottom limit: $pan", pan.optDouble("afterBottom") <= pan.optDouble("bottom") + 1)
            assertTrue("One-time fit scale must remain on the document root: $pan", pan.optString("fitTransform").contains("scale(") && pan.optString("fitTransform").isNotEmpty())
            assertTrue("Document fit transform must be present and matrix-valued: $pan", pan.optString("transform").startsWith("matrix("))

            val midpoint = evaluateJson(instrumentation, web,
                "(function(){var s=document.getElementById('scroller');s.scrollLeft=s.scrollWidth/2;s.scrollTop=s.scrollHeight/2;return JSON.stringify({left:s.scrollLeft,top:s.scrollTop});})()")
            instrumentation.runOnMainSync {
                dispatchPan(web, web.width * .7f, web.height * .6f, web.width * .3f, web.height * .3f)
            }
            awaitVisualState(instrumentation, web, "native one-finger pan toward right and bottom")
            val draggedTowardRightBottom = evaluateJson(instrumentation, web,
                "(function(){var s=document.getElementById('scroller');return JSON.stringify({left:s.scrollLeft,top:s.scrollTop});})()")
            assertTrue("One-finger drag must pan right and down from the midpoint: start=$midpoint end=$draggedTowardRightBottom",
                draggedTowardRightBottom.optDouble("left") > midpoint.optDouble("left") + 1 &&
                    draggedTowardRightBottom.optDouble("top") > midpoint.optDouble("top") + 1)
            instrumentation.runOnMainSync {
                dispatchPan(web, web.width * .3f, web.height * .3f, web.width * .7f, web.height * .6f)
            }
            awaitVisualState(instrumentation, web, "native one-finger pan toward left and top")
            val draggedTowardLeftTop = evaluateJson(instrumentation, web,
                "(function(){var s=document.getElementById('scroller');return JSON.stringify({left:s.scrollLeft,top:s.scrollTop});})()")
            assertTrue("Reverse one-finger drag must pan left and up: start=$draggedTowardRightBottom end=$draggedTowardLeftTop",
                draggedTowardLeftTop.optDouble("left") < draggedTowardRightBottom.optDouble("left") - 1 &&
                    draggedTowardLeftTop.optDouble("top") < draggedTowardRightBottom.optDouble("top") - 1)

        } finally {
            destroyWebView(instrumentation, activity, web)
        }
    }

    @Test fun nativeViewportMetaAllowsZoomOutForScrollableHtmlContent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = launchTestActivity(instrumentation)
        val ready = CountDownLatch(1)
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            web = WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) { ready.countDown() }
                }
                val density = resources.displayMetrics.density
                attachWebView(activity, this, (412 * density).toInt(), (915 * density).toInt())
                loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/",
                    """<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=0.5, maximum-scale=10.0, user-scalable=yes"><style>*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%}#rootExtent{width:200vw;height:200vh;visibility:hidden}#scroller{position:absolute;inset:0;overflow:auto}</style><div id="rootExtent"></div><main id="scroller"><div style="width:200vw;height:200vh"></div></main>""",
                    "text/html", "UTF-8", null
                )
            }
        }
        try {
            assertTrue("Minimal viewport fixture did not load", ready.await(10, TimeUnit.SECONDS))
            awaitVisualState(instrumentation, web, "minimal fixture initial load")
            val initial = readVisualViewportScale(instrumentation, web)
            assertTrue("Minimal viewport fixture must start at 100%: $initial", kotlin.math.abs(initial - 1f) < .03f)
            repeat(4) {
                instrumentation.runOnMainSync {
                    dispatchPinch(web, SystemClock.uptimeMillis(), 40f * web.resources.displayMetrics.density, 160f * web.resources.displayMetrics.density)
                }
                awaitVisualState(instrumentation, web, "minimal fixture native zoom-in pinch")
            }
            val zoomedIn = readVisualViewportScale(instrumentation, web)
            assertTrue("Minimal fixture must allow native pinch-in: $zoomedIn", zoomedIn >= 4.9f)
            repeat(6) {
                instrumentation.runOnMainSync {
                    dispatchPinch(web, SystemClock.uptimeMillis(), 160f * web.resources.displayMetrics.density, 40f * web.resources.displayMetrics.density)
                }
                awaitVisualState(instrumentation, web, "minimal fixture native zoom-out pinch")
            }
            val zoomedOut = readVisualViewportScale(instrumentation, web)
            val geometry = evaluateJson(instrumentation, web,
                "JSON.stringify({scale:visualViewport.scale,htmlWidth:document.documentElement.scrollWidth,htmlHeight:document.documentElement.scrollHeight,bodyWidth:document.body.scrollWidth,bodyHeight:document.body.scrollHeight,rootScrollX:window.scrollX,rootScrollY:window.scrollY,viewportWidth:visualViewport.width})")
            assertTrue("Native pinch-out should reach the declared 50% viewport minimum: $zoomedOut geometry=$geometry",
                zoomedOut >= .49f && zoomedOut <= .52f)
        } finally {
            destroyWebView(instrumentation, activity, web)
        }
    }

    @Test fun fitReflowsAfterContainerResizeAndPreservesScrollRatio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = launchTestActivity(instrumentation)
        val fixture = fixtureDocx()
        val ready = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
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
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                addJavascriptInterface(object {
                    @JavascriptInterface @Suppress("UNUSED_PARAMETER")
                    fun onDocumentVisuallyRendered(payload: String) { signalAfterVisualState(web, ready) }
                    @JavascriptInterface fun onFailure(payload: String) { failure.set(payload); ready.countDown() }
                }, "DocxBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                        request?.url?.let(loader::shouldInterceptRequest)
                }
                val density = resources.displayMetrics.density
                attachWebView(activity, this, (412 * density).toInt(), (915 * density).toInt())
                loadUrl("https://appassets.androidplatform.net/assets/docx_viewer/index.html?generation=38&file=" +
                    android.net.Uri.encode("https://appassets.androidplatform.net/docx-src/document.docx"))
            }
        }
        try {
            assertTrue("DOCX render timed out before orientation resize: ${failure.get()}", ready.await(45, TimeUnit.SECONDS))
            assertTrue("DOCX renderer failed before orientation resize: ${failure.get()}", failure.get() == null)
            val density = web.resources.displayMetrics.density
            val portraitWidth = 412
            val landscapeWidth = 915
            val portraitStart = evaluateJson(instrumentation, web,
                "(function(){var s=document.getElementById('scroller'),v=visualViewport;return JSON.stringify({width:s.clientWidth,viewport:v.width,count:window.__docxFitReflowCount||0,range:s.scrollHeight-s.clientHeight});})()")
            assertTrue("Initial portrait width mismatch: $portraitStart", kotlin.math.abs(portraitStart.optDouble("width") - portraitWidth) < 3)
            evaluateJson(instrumentation, web,
                "(function(){var s=document.getElementById('scroller');s.scrollTop=(s.scrollHeight-s.clientHeight)*0.55;return JSON.stringify({ratio:s.scrollTop/Math.max(1,s.scrollHeight-s.clientHeight)});})()")
            awaitVisualState(instrumentation, web, "portrait reading position before container resize")
            val before = evaluateJson(instrumentation, web,
                "(function(){var s=document.getElementById('scroller'),ratio=s.scrollTop/Math.max(1,s.scrollHeight-s.clientHeight);return JSON.stringify({ratio:ratio,cachedRatio:DocxViewer.getState().scrollRatio,count:window.__docxFitReflowCount||0});})()")
            assertTrue("Scroll handler must remember reading progress before the viewport changes: $before",
                kotlin.math.abs(before.optDouble("cachedRatio") - before.optDouble("ratio")) < .04)
            resizeWebView(instrumentation, web, (landscapeWidth * density).toInt(), (412 * density).toInt())
            val landscape = awaitFitReflow(instrumentation, web, landscapeWidth, before.optInt("count") + 1)
            assertPageFillsViewport(landscape, landscapeWidth)
            assertTrue("Landscape reflow must preserve vertical reading progress: before=$before after=$landscape",
                kotlin.math.abs(landscape.optDouble("scrollRatio") - before.optDouble("ratio")) < .04)
            Thread.sleep(250)
            val stableLandscape = evaluateJson(instrumentation, web,
                "(function(){var s=document.getElementById('scroller'),p=document.querySelector('#scaled-document .docx, #scaled-document section.docx'),r=p.getBoundingClientRect();return JSON.stringify({count:window.__docxFitReflowCount||0,transform:document.getElementById('scaled-document').style.transform,pageWidth:r.width,scrollTop:s.scrollTop});})()")
            assertTrue("Resize observer must settle after one landscape reflow: $stableLandscape", stableLandscape.optInt("count") == landscape.optInt("count"))

            resizeWebView(instrumentation, web, (portraitWidth * density).toInt(), (915 * density).toInt())
            val portrait = awaitFitReflow(instrumentation, web, portraitWidth, stableLandscape.optInt("count") + 1)
            assertPageFillsViewport(portrait, portraitWidth)
            assertTrue("Returning to portrait must preserve the same reading progress: $before -> $portrait",
                kotlin.math.abs(portrait.optDouble("scrollRatio") - before.optDouble("ratio")) < .04)
        } finally {
            destroyWebView(instrumentation, activity, web)
        }
    }

    @Test fun initialPageFitsAt360x800And412x915() {
        verifyInitialPageFitsViewport(widthDp = 360, heightDp = 800)
        verifyInitialPageFitsViewport(widthDp = 412, heightDp = 915)
    }

    private fun resizeWebView(
        instrumentation: android.app.Instrumentation,
        web: WebView,
        widthPx: Int,
        heightPx: Int
    ) {
        instrumentation.runOnMainSync {
            val params = web.layoutParams as FrameLayout.LayoutParams
            params.width = widthPx
            params.height = heightPx
            web.layoutParams = params
            web.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            )
            web.layout(0, 0, widthPx, heightPx)
            web.requestLayout()
        }
    }

    private fun awaitFitReflow(
        instrumentation: android.app.Instrumentation,
        web: WebView,
        expectedWidth: Int,
        expectedCount: Int
    ): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        var metrics = JSONObject()
        do {
            metrics = evaluateJson(instrumentation, web,
                "(function(){var s=document.getElementById('scroller'),p=document.querySelector('#scaled-document .docx, #scaled-document section.docx'),r=p.getBoundingClientRect(),v=visualViewport,range=s.scrollHeight-s.clientHeight;return JSON.stringify({width:s.clientWidth,viewport:v.width,count:window.__docxFitReflowCount||0,pageLeft:(r.left-v.offsetLeft)*v.scale,pageRight:(r.right-v.offsetLeft)*v.scale,pageWidth:r.width*v.scale,viewportWidth:v.width*v.scale,fitTransform:document.getElementById('scaled-document').style.transform,scrollRatio:s.scrollTop/Math.max(1,range)});})()")
            if (kotlin.math.abs(metrics.optDouble("width") - expectedWidth) < 3 &&
                kotlin.math.abs(metrics.optDouble("viewport") - expectedWidth) < 3 &&
                metrics.optInt("count") == expectedCount
            ) return metrics
            Thread.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Fit did not settle at $expectedWidth px in one reflow: $metrics")
    }

    private fun assertPageFillsViewport(metrics: JSONObject, expectedWidth: Int) {
        assertTrue("Reflowed page must fill available landscape/portrait width with balanced gutters: $metrics",
            kotlin.math.abs(metrics.optDouble("pageLeft") - 12.0) <= 2.0 &&
                kotlin.math.abs((metrics.optDouble("viewportWidth") - metrics.optDouble("pageRight")) - 12.0) <= 2.0 &&
                kotlin.math.abs(metrics.optDouble("pageWidth") - (expectedWidth - 24.0)) <= 4.0)
    }

    private fun verifyInitialPageFitsViewport(widthDp: Int, heightDp: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = launchTestActivity(instrumentation)
        val fixture = fixtureDocx()
        val ready = CountDownLatch(1)
        val failure = AtomicReference<String?>(null)
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(activity))
                .addPathHandler("/docx-src/", object : WebViewAssetLoader.PathHandler {
                    override fun handle(path: String): WebResourceResponse? = if (path == "document.docx")
                        WebResourceResponse(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", null,
                            200, "OK", mapOf("Content-Length" to fixture.size.toString()), ByteArrayInputStream(fixture)
                        ) else null
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
                addJavascriptInterface(object {
                    @JavascriptInterface
                    @Suppress("UNUSED_PARAMETER")
                    fun onDocumentVisuallyRendered(payload: String) {
                        signalAfterVisualState(web, ready)
                    }
                    @JavascriptInterface fun onFailure(payload: String) {
                        failure.set(payload)
                        ready.countDown()
                    }
                }, "DocxBridge")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                        request?.url?.let(loader::shouldInterceptRequest)
                    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean =
                        reportRendererGone(failure, ready, detail)
                }
                val density = resources.displayMetrics.density
                attachWebView(activity, this, (widthDp * density).toInt(), (heightDp * density).toInt())
                loadUrl("https://appassets.androidplatform.net/assets/docx_viewer/index.html?generation=41&file=" +
                    android.net.Uri.encode("https://appassets.androidplatform.net/docx-src/document.docx"))
            }
        }
        try {
            assertTrue("${widthDp}x${heightDp} render timed out: ${failure.get()}", ready.await(45, TimeUnit.SECONDS))
            assertTrue("${widthDp}x${heightDp} renderer failed: ${failure.get()}", failure.get() == null)
            val layout = evaluateJson(
                instrumentation,
                web,
                "(function(){var s=document.getElementById('scroller'),p=document.querySelector('#scaled-document .docx, #scaled-document section.docx'),r=p.getBoundingClientRect(),v=visualViewport,z=v.scale;return JSON.stringify({clientWidth:s.clientWidth*z,scrollWidth:s.scrollWidth*z,visualViewportWidth:v.width*z,pageCardWidth:r.width*z,pageCardHeight:r.height*z,pageCardLeft:(r.left-v.offsetLeft)*z,pageCardRight:(r.right-v.offsetLeft)*z,pageClientWidth:p.clientWidth,pageScrollWidth:p.scrollWidth,viewportWidth:v.width*z,layoutViewportWidth:document.documentElement.clientWidth,visualViewportWidth:v.width,fitTransform:document.getElementById('scaled-document').style.transform,computedTransform:getComputedStyle(document.getElementById('scaled-document')).transform,scale:z,fitScale:DocxViewer.getState().fitScale,paper:getComputedStyle(document.documentElement).getPropertyValue('--paper').trim(),pagePaper:getComputedStyle(document.documentElement).getPropertyValue('--page-paper').trim(),shadow:getComputedStyle(p).boxShadow});})()"
            )
            val pageLeft = layout.optDouble("pageCardLeft")
            val pageRight = layout.optDouble("pageCardRight")
            val viewportWidth = layout.optDouble("viewportWidth")
            val leftMargin = pageLeft
            val rightMargin = viewportWidth - pageRight
            assertTrue("${widthDp}x${heightDp} must start at measured fit-to-width baseline: $layout",
                kotlin.math.abs(layout.optDouble("scale") - layout.optDouble("fitScale")) < .03)
            assertTrue("Light canvas must be light grey: $layout", layout.optString("paper") == "#f0f0f0")
            assertTrue("Document cards must be white: $layout", layout.optString("pagePaper") == "#ffffff")
            assertTrue("${widthDp}x${heightDp} page card must have positive dimensions: $layout",
                layout.optDouble("pageCardWidth") > 0 && layout.optDouble("pageCardHeight") > 0)
            assertTrue("${widthDp}x${heightDp} pageCardLeft must be >= 0: $layout", pageLeft >= 0)
            assertTrue("${widthDp}x${heightDp} pageCardRight must be <= viewportWidth: $layout",
                pageRight <= viewportWidth)
            assertTrue("${widthDp}x${heightDp} page card must retain the A4 page ratio: $layout",
                kotlin.math.abs(layout.optDouble("pageCardWidth") / layout.optDouble("pageCardHeight") - 11906.0 / 16838.0) < .03)
            assertTrue("${widthDp}x${heightDp} page content must not be horizontally clipped: $layout",
                layout.optDouble("pageScrollWidth") <= layout.optDouble("pageClientWidth") + 1)
            assertTrue("${widthDp}x${heightDp} page card must have a soft shadow: $layout",
                layout.optString("shadow") != "none")
            assertTrue("${widthDp}x${heightDp} page card must have 12px outer margins: $layout",
                kotlin.math.abs(leftMargin - 12.0) <= 2 && kotlin.math.abs(rightMargin - 12.0) <= 2)
            assertTrue("${widthDp}x${heightDp} page content must remain in the visible visual viewport: $layout",
                layout.optDouble("scrollWidth") <= layout.optDouble("visualViewportWidth") + 1)
        } finally {
            destroyWebView(instrumentation, activity, web)
        }
    }

    @Test fun missingExternalAppIsReportedToThePreview() {
        var unavailableReported = false
        val context = object : android.content.ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent: Intent) {
                throw android.content.ActivityNotFoundException("No resolver in test")
            }
        }
        val launched = launchDocxExternalLink(
            context,
            android.net.Uri.parse("https://example.com/unhandled"),
            onUnavailable = { unavailableReported = true }
        )
        assertFalse("External link must report a missing handler instead of claiming launch", launched)
        assertTrue("The UI fallback must be invoked when Android has no browser app", unavailableReported)
    }

    private fun dispatchTap(web: WebView, downTime: Long, x: Float = 100f, y: Float = 100f) {
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

    private fun dispatchPan(web: WebView, startX: Float, startY: Float, endX: Float, endY: Float) {
        val downTime = SystemClock.uptimeMillis()
        var eventTime = downTime
        fun send(action: Int, fraction: Float) {
            val x = startX + (endX - startX) * fraction
            val y = startY + (endY - startY) * fraction
            val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
            try { web.dispatchTouchEvent(event) } finally { event.recycle() }
            eventTime += 16
        }
        send(MotionEvent.ACTION_DOWN, 0f)
        for (step in 1..8) send(MotionEvent.ACTION_MOVE, step / 8f)
        send(MotionEvent.ACTION_UP, 1f)
    }

    private fun assertSingleWrapperAndStableIdleLayout(
        instrumentation: android.app.Instrumentation,
        web: WebView
    ) {
        val observations = mutableListOf<JSONObject>()
        val deadline = SystemClock.elapsedRealtime() + 2_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            val layout = evaluateJson(instrumentation, web,
                "(function(){var p=document.querySelector('#scaled-document .docx, #scaled-document section.docx'),r=p&&p.getBoundingClientRect();return JSON.stringify({wrappers:document.querySelectorAll('.docx-wrapper').length,scale:visualViewport.scale,fitTransform:document.getElementById('scaled-document').style.transform,left:r&&r.left,top:r&&r.top,width:r&&r.width,height:r&&r.height});})()")
            observations += layout
            Thread.sleep(50)
        }
        assertTrue("two-second idle observation must collect repeated layout samples: ${observations.size}", observations.size >= 20)
        assertTrue("Exactly one rendered DOCX wrapper must remain in the DOM: ${observations.map { it.optInt("wrappers") }.distinct()}",
            observations.all { it.optInt("wrappers") == 1 })
        val baseline = observations.first()
        val maxVisualScaleDrift = observations.maxOf { kotlin.math.abs(it.optDouble("scale") - baseline.optDouble("scale")) }
        val fitTransformStable = observations.all { it.optString("fitTransform") == baseline.optString("fitTransform") }
        val maxBoundsDrift = observations.maxOf { layout ->
            listOf("left", "top", "width", "height").maxOf { key ->
                kotlin.math.abs(layout.optDouble(key) - baseline.optDouble(key))
            }
        }
        assertTrue(
            "native page zoom and page bounds must remain stable during two-second idle observation: " +
                "visualScaleDrift=$maxVisualScaleDrift fitTransformStable=$fitTransformStable " +
                "boundsDrift=$maxBoundsDrift baseline=$baseline",
            maxVisualScaleDrift < .001 && fitTransformStable && maxBoundsDrift < .1
        )
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

    private fun evaluateJson(instrumentation: android.app.Instrumentation, web: WebView, script: String): JSONObject {
        val value = AtomicReference<String?>(null)
        val completed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            web.evaluateJavascript(script) { result -> value.set(result); completed.countDown() }
        }
        assertTrue("WebView JavaScript result timed out", completed.await(5, TimeUnit.SECONDS))
        val decoded = org.json.JSONTokener(value.get()).nextValue().toString()
        return JSONObject(decoded)
    }

    private fun awaitVisualViewportScale(
        instrumentation: android.app.Instrumentation,
        web: WebView,
        expected: Float
    ) {
        val deadline = SystemClock.elapsedRealtime() + 4_000L
        var actual = readVisualViewportScale(instrumentation, web)
        while (kotlin.math.abs(actual - expected) >= .08f && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(50)
            actual = readVisualViewportScale(instrumentation, web)
        }
        val state = evaluateJson(instrumentation, web,
            "(function(){var s=window.DocxViewer.getState();return JSON.stringify({scale:visualViewport.scale,fitScale:s.fitScale,nativeFitScale:s.nativeFitScale,request:window.__docxZoomRequest||null,scrollLeft:document.getElementById('scroller').scrollLeft,scrollTop:document.getElementById('scroller').scrollTop});})()")
        assertTrue("Expected native zoom $expected, observed $actual, state=$state", kotlin.math.abs(actual - expected) < .08f)
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

    @Test fun printPreparationResetsPreviewFitAndUsesFullPageWidth() {
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
            val prepared = evaluateJson(instrumentation, web,
                "(function(){DocxViewer.setTheme(true,'#181715','#252320','#faf9f5','#2d2c2a');var d=document.getElementById('scaled-document');d.style.transform='translateX(12px) scale(0.5)';var state=DocxViewer.prepareForPrint();return JSON.stringify({ready:state.ready,printScale:state.scale,printPreparedWidth:state.rootWidth,pageWidth:state.pageWidth,viewportWidth:state.viewportWidth,transform:state.transform,inlineTransform:d.style.transform,prepared:document.documentElement.classList.contains('print-prepared'),paper:getComputedStyle(document.documentElement).getPropertyValue('--paper').trim(),pagePaper:getComputedStyle(document.documentElement).getPropertyValue('--page-paper').trim()});})()")
            assertTrue("Print preparation should remove mobile scale and retain measurable page geometry: $prepared",
                prepared.optBoolean("ready") && kotlin.math.abs(prepared.optDouble("printScale") - 1.0) < .01 &&
                    prepared.optDouble("printPreparedWidth") > 0.0 && prepared.optDouble("pageWidth") > 0.0 &&
                    prepared.optString("transform") == "none" && prepared.optString("inlineTransform") == "none" &&
                    prepared.optBoolean("prepared"))
            assertTrue("Print preparation must retain the active theme and print paper tokens: $prepared",
                prepared.optString("paper") == "#181715" && prepared.optString("pagePaper") == "#252320")
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
                    @JavascriptInterface
                    @Suppress("UNUSED_PARAMETER")
                    fun onDocumentVisuallyRendered(payload: String) { ready.countDown() }
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
                setBackgroundColor(Color.rgb(240, 240, 240))
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
            val expectedCanvas = Color.rgb(240, 240, 240)
            for (y in 0 until captured.height step 3) for (x in 0 until captured.width step 3) {
                val pixel = captured.getPixel(x, y)
                if (Color.rgb(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) != expectedCanvas) changedPixels++
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
                val background = Color.rgb(240, 240, 240)
                var changed = 0
                for (y in 0 until bitmap.height step 3) for (x in 0 until bitmap.width step 3) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.rgb(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) != background) changed++
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
                setBackgroundColor(Color.rgb(240, 240, 240))
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
                val expectedCanvas = Color.rgb(240, 240, 240)
                for (y in 0 until bitmap.height step 3) for (x in 0 until bitmap.width step 3) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.rgb(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) != expectedCanvas) {
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

    private class RecordingContext(
        base: android.content.Context,
        private val launchedIntent: AtomicReference<Intent?>,
        private val dispatched: CountDownLatch
    ) : android.content.ContextWrapper(base) {
        override fun startActivity(intent: Intent) {
            launchedIntent.set(Intent(intent))
            dispatched.countDown()
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
            <w:p><w:r><w:t>styled </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>phrase</w:t></w:r><w:hyperlink r:id="rIdLink"><w:r><w:rPr><w:color w:val="0563C1"/><w:u w:val="single"/></w:rPr><w:t>open test link</w:t></w:r></w:hyperlink></w:p>
            <w:p><w:r><w:t>हिंदी पाठ</w:t></w:r></w:p>
            <w:p><w:r><w:rPr><w:color w:val="B40000"/></w:rPr><w:t>colored marker</w:t></w:r></w:p>
            <w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>fixture list item</w:t></w:r></w:p>
            <w:tbl><w:tblPr><w:tblW w:w="0" w:type="auto"/></w:tblPr><w:tr><w:tc><w:p><w:r><w:t>visible table cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
            <w:p><w:r><w:drawing><wp:inline><wp:extent cx="914400" cy="914400"/><wp:docPr id="1" name="fixture image"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="0" name="fixture.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdImg"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="914400" cy="914400"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
            <w:p><w:r><w:br w:type="page"/><w:t>Second page marker</w:t></w:r></w:p>
            <w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
            </w:body></w:document>""".trimIndent()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) { zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("[Content_Types].xml", """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry("word/_rels/document.xml.rels", """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdImg" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/fixture.png"/><Relationship Id="rIdNum" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/><Relationship Id="rIdLink" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="https://example.com/docx-test" TargetMode="External"/></Relationships>""")
            entry("word/numbering.xml", """<?xml version="1.0"?><w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="singleLevel"/><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num></w:numbering>""")
            entry("word/document.xml", xml)
            zip.putNextEntry(ZipEntry("word/media/fixture.png"))
            zip.write(Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jE9sAAAAASUVORK5CYII=", Base64.DEFAULT))
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
