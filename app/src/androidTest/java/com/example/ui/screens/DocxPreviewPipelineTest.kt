package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import com.example.utils.DocxCountingInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil

/**
 * Real WebView regression coverage for the bundled DOCX renderer. It exercises the
 * actual editor HTML and local asset-loader route at a 360 x 800 CSS-pixel viewport.
 */
@RunWith(AndroidJUnit4::class)
class DocxPreviewPipelineTest {

    companion object {
        private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }

    @Test
    fun previewAndPrintModeRenderVisibleContentAcrossFixtureSuite() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtures = fixtureSuite()
        val results = mutableMapOf<Pair<String, Boolean>, RenderResult>()

        fixtures.forEach { fixture ->
            listOf(false, true).forEach { printMode ->
                val result = render(context, fixture, printMode)
                results[fixture.name to printMode] = result
                val mode = if (printMode) "print-mode" else "normal-preview"
                val label = "${fixture.name} $mode"

                assertEquals("$label did not request exactly one local DOCX stream", 1, result.fetchRequestCount)
                assertEquals("$label JS fetch count diverged from the asset request", 1, result.fetchCount)
                assertEquals("$label fetch status", 200, result.fetchStatus)
                assertEquals("$label fetch bytes", result.fixtureBytes, result.fetchBytes)
                // Chromium may hide Content-Length from fetch() for synthetic WebResourceResponse
                // streams; when exposed it must agree, and fetched bytes are always exact below.
                assertTrue("$label response Content-Length disagreed: ${result.fetchContentLength}",
                    result.fetchContentLength == 0 || result.fixtureBytes == result.fetchContentLength)
                assertEquals("$label intercepted asset MIME", DOCX_MIME, result.assetResponseMime)
                assertEquals("$label intercepted asset length", result.fixtureBytes, result.assetResponseLength)
                assertTrue("$label returned no DOCX ZIP signature", result.docxMagic)
                assertTrue("$label did not initialize JSZip/docx-preview", result.rendererAvailable)
                assertTrue("$label raised a JavaScript console error: ${result.jsError}", result.jsError == null)
                assertTrue("$label renderer failed: ${result.error}", result.error == null)
                assertEquals("$label Android readiness callback page count diverged", result.pageCount, result.callbackPageCount)
                assertTrue("$label produced no page nodes: ${result.details}", result.pageCount >= fixture.minimumPages)
                assertTrue("$label expected content is absent from the DOM: ${result.text}",
                    fixture.expectedText.all { expected -> result.text.contains(expected) })
                assertTrue("$label has no visible page layout: ${result.details}", result.firstPageVisible)
                assertTrue("$label has no visible content in the viewport: ${result.details}", result.visibleContent)
                assertTrue("$label failed to report readiness timing: ${result.readyElapsedMs}", result.readyElapsedMs >= 0.0)
                assertTrue("$label is not actually at the ready diagnostic stage: ${result.readyStage}", result.readyStage == "render_visible_ready")
                assertTrue("$label first-page dimensions are empty: ${result.details}",
                    result.firstPageWidth > 0 && result.firstPageHeight > 0)
                assertTrue("$label final zoom is invalid: ${result.effectiveZoom}",
                    result.effectiveZoom.isFinite() && result.effectiveZoom > 0f)
                assertTrue("$label incorrectly reported ready with loading overlay visible", result.loadingOverlayHidden)
                assertTrue("$label did not use the requested CSS viewport: ${result.viewportWidth} x ${result.viewportHeight}",
                    result.viewportWidth in 340.0..380.0 && result.viewportHeight in 760.0..840.0)
                assertTrue("$label fetched more bytes than the fixture", result.fetchBytes <= result.fixtureBytes)
                if (fixture.hasImage) assertTrue("$label image failed to load", result.imageLoaded)
                if (fixture.requireLandscapePage) {
                    assertTrue("$label lost its landscape page", result.hasLandscapePage)
                }
            }
        }

        val basicPreview = results.getValue("basic" to false)
        assertTrue("Hindi text not rendered in normal preview", basicPreview.text.contains("हिंदी पाठ"))
        assertTrue("Mixed bold formatting missing in normal preview", basicPreview.hasBoldText)
        assertTrue("table contents not rendered in normal preview", basicPreview.text.contains("visible table cell"))
        assertTrue("mixed bold formatting missing in normal preview", basicPreview.hasBoldText)
        assertTrue("image fixture did not load an image in normal preview", results.getValue("image" to false).imageLoaded)
        assertTrue("image fixture did not load an image in print mode", results.getValue("image" to true).imageLoaded)
        assertTrue("portrait page dimensions were not rendered", basicPreview.hasPortraitPage)
        assertTrue("landscape page dimensions were not rendered", results.getValue("landscape" to false).hasLandscapePage)
        assertTrue("landscape print page dimensions were not rendered", results.getValue("landscape" to true).hasLandscapePage)
        assertTrue("long document did not produce multiple preview pages", results.getValue("long" to false).pageCount >= 2)
        assertTrue("long document did not produce multiple print-mode pages", results.getValue("long" to true).pageCount >= 2)
        assertTrue("long-document trailing content is missing", results.getValue("long" to false).text.contains("long-document-end-marker"))
        assertTrue("long-document trailing content is missing in print mode", results.getValue("long" to true).text.contains("long-document-end-marker"))

        val normal = results.getValue("basic" to false)
        val printed = results.getValue("basic" to true)
        assertEquals("Normal preview and print-mode fixture text diverged", normal.text, printed.text)
    }

    @Test
    fun pageNodesOutsideViewportCannotBeReportedAsDocumentReady() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = fixtureSuite().first()
        val result = render(context, fixture, printMode = false)
        assertTrue("Fixture must produce page nodes", result.pageCount > 0)
        assertTrue("Regression: a page node exists but its first page does not intersect the scroller viewport: ${result.details}",
            result.firstPageVisible)
        assertTrue("Regression: visible text must intersect the actual scroller viewport: ${result.details}",
            result.visibleContent)
        assertTrue("The ready callback must occur only after visible layout is validated", result.loadingOverlayHidden)
        assertTrue("The JS diagnostic must report a visible-ready stage", result.readyStage == "render_visible_ready")
    }

    @Test
    fun attachedPageNodesOutsideViewportShowActionableFailureAndNeverSignalReady() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = fixtureSuite().first()
        val result = render(context, fixture, printMode = false, forcePageOffscreen = true)
        assertTrue("Renderer should have created page nodes before the forced layout failure: ${result.details}",
            result.rendererPageCount > 0)
        assertTrue("Out-of-viewport page was incorrectly considered visible: ${result.details}",
            !result.firstPageVisible)
        assertTrue("Out-of-viewport page incorrectly displayed document text: ${result.details}",
            !result.visibleContent)
        assertTrue("Renderer incorrectly invoked Android ready callback", result.callbackPageCount == 0)
        assertTrue("Blank preview should show an actionable error: ${result.errorBannerText}",
            result.errorBannerText.contains("visible page", ignoreCase = true))
    }

    @Test
    fun layoutRemainsVisibleAtSecondAndroidViewportSize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = fixtureSuite().first()
        listOf(false, true).forEach { printMode ->
            val result = render(context, fixture, printMode, viewportWidthDp = 412, viewportHeightDp = 915)
            val mode = if (printMode) "print-mode" else "normal-preview"
            assertTrue("$mode lost page visibility at 412 x 915 CSS px: ${result.details}", result.firstPageVisible)
            assertTrue("$mode lost visible text at 412 x 915 CSS px: ${result.details}", result.visibleContent)
            assertTrue("$mode invalid zoom at 412 x 915 CSS px: ${result.effectiveZoom}",
                result.effectiveZoom.isFinite() && result.effectiveZoom > 0f)
            assertTrue("$mode CSS viewport mismatch: ${result.viewportWidth} x ${result.viewportHeight}",
                result.viewportWidth in 390.0..430.0 && result.viewportHeight in 875.0..955.0)
        }
    }

    private fun render(
        context: Context,
        fixture: DocxFixture,
        printMode: Boolean,
        viewportWidthDp: Int = 360,
        viewportHeightDp: Int = 800,
        forcePageOffscreen: Boolean = false
    ): RenderResult {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rendered = CountDownLatch(1)
        val evaluated = CountDownLatch(1)
        val renderError = AtomicReference<String?>(null)
        val jsError = AtomicReference<String?>(null)
        val resultValue = AtomicReference<String>("")
        val callbackPageCount = AtomicInteger(0)
        val fetchRequestCount = AtomicInteger(0)
        val interceptedMime = AtomicReference<String?>(null)
        val interceptedLength = AtomicInteger(0)
        val fixtureBytes = fixture.bytes
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/docx-src/", object : WebViewAssetLoader.PathHandler {
                override fun handle(path: String): WebResourceResponse? {
                    if (path != "document.docx") return null
                    fetchRequestCount.incrementAndGet()
                    interceptedMime.set(DOCX_MIME)
                    interceptedLength.set(fixtureBytes.size)
                    return WebResourceResponse(
                        DOCX_MIME,
                        null,
                        200,
                        "OK",
                        mapOf("Content-Length" to fixtureBytes.size.toString()),
                        DocxCountingInputStream(ByteArrayInputStream(fixtureBytes), fixture.name, fixtureBytes.size.toLong())
                    )
                }
            })
            .build()

        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(context)
            webView.setBackgroundColor(Color.WHITE)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.allowFileAccess = false
            webView.settings.allowContentAccess = false
            webView.settings.builtInZoomControls = false
            webView.settings.displayZoomControls = false
            webView.settings.setSupportZoom(false)
            // Match the real normal preview; the separate print helper leaves these defaults.
            webView.settings.useWideViewPort = !printMode
            webView.settings.loadWithOverviewMode = !printMode
            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun onDocxDiagnostic(payload: String) {
                    resultValue.set(payload)
                    runCatching {
                        val diagnostic = JSONObject(payload)
                        if (diagnostic.optString("event") == "javascript_error" ||
                            diagnostic.optString("event") == "unhandled_rejection") {
                            jsError.compareAndSet(null, diagnostic.optString("errorKind", "JavaScript error"))
                        }
                    }
                }

                @JavascriptInterface
                fun onDocumentLoaded(count: Int) {
                    callbackPageCount.set(count)
                    rendered.countDown()
                }

                @JavascriptInterface
                fun onDocumentError(message: String) {
                    renderError.set(message)
                    rendered.countDown()
                }
            }, "Android")
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                    if (message?.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                        jsError.set("${message.sourceId()}:${message.lineNumber()}")
                    }
                    return true
                }
            }
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                    request?.url?.let(loader::shouldInterceptRequest)

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (url?.contains("/assets/editor/docx_editor.html") == true) {
                        view?.evaluateJavascript(
                            (if (forcePageOffscreen) "var s=document.createElement('style'); s.textContent='#output .docx{transform:translateX(-5000px)!important}'; document.head.appendChild(s);" else "") +
                                if (printMode) "loadDocx(false);" else "setZoom(1); loadDocx(false);",
                            null
                        )
                    }
                }

                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        renderError.set("Editor page failed with code ${error?.errorCode ?: 0}")
                        rendered.countDown()
                    }
                }
            }

            val density = context.resources.displayMetrics.density
            val widthPx = ceil(viewportWidthDp * density).toInt()
            val heightPx = ceil(viewportHeightDp * density).toInt()
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, widthPx, heightPx)
            val url = "https://appassets.androidplatform.net/assets/editor/docx_editor.html" +
                if (printMode) "?print=1" else ""
            webView.loadUrl(url)
        }

        try {
            assertTrue("Timed out waiting for ${fixture.name} render (print=$printMode)", rendered.await(45, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                webView.evaluateJavascript(
                        """(function(){
                            var pages=Array.from(document.querySelectorAll('#output .docx'));
                            var page=pages[0];
                            var scroller=document.getElementById('document-scroller');
                            var overlay=document.getElementById('loading');
                            if(!page || !scroller) {
                                var failedDiag=window.__DOCX_DIAGNOSTICS||{};
                                return JSON.stringify({pageCount:pages.length,firstPageVisible:false,visibleText:false,
                                    rendererPageCount:failedDiag.pages||0,
                                    errorBannerText:document.querySelector('#output .error-banner')?.innerText||'',
                                    readyStage:failedDiag.event||'',readyElapsedMs:failedDiag.elapsedMs||-1});
                            }
                            var rect=page.getBoundingClientRect();
                            var viewport=scroller.getBoundingClientRect();
                            var style=getComputedStyle(page);
                            var intersects=rect.right>viewport.left && rect.left<viewport.right && rect.bottom>viewport.top && rect.top<viewport.bottom;
                            var visible=intersects && rect.width>0 && rect.height>0 && style.display!=='none' && style.visibility!=='hidden' && Number(style.opacity)>0;
                            var text=(document.querySelector('#output').innerText||'').replace(/\s+/g,' ').trim();
                            var visibleText=false;
                            var walker=document.createTreeWalker(page,NodeFilter.SHOW_TEXT);
                            while(walker.nextNode()) {
                                var node=walker.currentNode;
                                if(!(node.nodeValue||'').trim()) continue;
                                var range=document.createRange(); range.selectNodeContents(node);
                                var tr=range.getBoundingClientRect();
                                if(tr.width>0 && tr.height>0 && tr.right>viewport.left && tr.left<viewport.right && tr.bottom>viewport.top && tr.top<viewport.bottom) { visibleText=true; break; }
                            }
                            var image=document.querySelector('#output img');
                            var pagesData=pages.map(function(p){return {width:p.offsetWidth,height:p.offsetHeight,rect:p.getBoundingClientRect().toJSON()};});
                            var hasPortraitPage=pages.some(function(p){return p.offsetHeight>p.offsetWidth;});
                            var hasLandscapePage=pages.some(function(p){return p.offsetWidth>p.offsetHeight;});
                            var diag=window.__DOCX_DIAGNOSTICS||{};
                            return JSON.stringify({
                                pageCount:pages.length,visible:visible,visibleText:visibleText,text:text,
                                pageWidth:page.offsetWidth,pageHeight:page.offsetHeight,
                                viewportWidth:diag.windowWidth,viewportHeight:diag.windowHeight,
                                scrollerWidth:scroller.clientWidth,scrollerHeight:scroller.clientHeight,
                                firstPageVisible:visible,contentPresent:!!text || !!document.querySelector('#output img, #output svg, #output table'),
                                imageLoaded:!!image && image.complete && image.naturalWidth>0,
                                hasBoldText:!!Array.from(page.querySelectorAll('b,strong,span')).find(function(n){return /bold|700|800|900/.test(getComputedStyle(n).fontWeight);}),
                                loadingOverlayHidden:!!overlay && overlay.hidden,
                                fetchCount:diag.fetchCount,fetchStatus:diag.fetchStatus,fetchBytes:diag.fetchBytes,
                                docxMagic:diag.docxMagic,rendererAvailable:diag.rendererAvailable,
                                effectiveZoom:diag.effectiveZoom,readyStage:diag.event,readyElapsedMs:diag.elapsedMs,
                                rendererPageCount:diag.pages,errorBannerText:document.querySelector('#output .error-banner')?.innerText||'',
                                pagesData:pagesData,hasPortraitPage:hasPortraitPage,hasLandscapePage:hasLandscapePage
                            });
                        })()""".trimIndent()
                ) { value ->
                    resultValue.set(value ?: "")
                    evaluated.countDown()
                }
            }
            assertTrue("Timed out reading ${fixture.name} rendered DOM", evaluated.await(10, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync {
                webView.stopLoading()
                webView.removeJavascriptInterface("Android")
                webView.destroy()
            }
        }

        val rawValue = resultValue.get()
        val resultJson = runCatching { JSONObject(JSONArray("[$rawValue]").getString(0)) }.getOrNull()
        val text = resultJson?.optString("text").orEmpty()
        return RenderResult(
            fixtureName = fixture.name,
            fixtureBytes = fixtureBytes.size,
            fetchRequestCount = fetchRequestCount.get(),
            assetResponseMime = interceptedMime.get().orEmpty(),
            assetResponseLength = interceptedLength.get(),
            fetchCount = resultJson?.optInt("fetchCount") ?: 0,
            fetchStatus = resultJson?.optInt("fetchStatus") ?: 0,
            fetchBytes = resultJson?.optInt("fetchBytes") ?: 0,
            fetchContentLength = resultJson?.optInt("fetchContentLength") ?: 0,
            fetchMime = resultJson?.optString("fetchMime").orEmpty(),
            docxMagic = resultJson?.optBoolean("docxMagic") == true,
            rendererAvailable = resultJson?.optBoolean("rendererAvailable") == true,
            error = renderError.get(),
            jsError = jsError.get(),
            text = text,
            pageCount = resultJson?.optInt("pageCount") ?: callbackPageCount.get(),
            rendererPageCount = resultJson?.optInt("rendererPageCount") ?: 0,
            errorBannerText = resultJson?.optString("errorBannerText").orEmpty(),
            callbackPageCount = callbackPageCount.get(),
            firstPageWidth = resultJson?.optDouble("pageWidth")?.toFloat() ?: 0f,
            firstPageHeight = resultJson?.optDouble("pageHeight")?.toFloat() ?: 0f,
            firstPageVisible = resultJson?.optBoolean("firstPageVisible") == true,
            visibleContent = resultJson?.optBoolean("visibleText") == true,
            imageLoaded = resultJson?.optBoolean("imageLoaded") == true,
            hasPortraitPage = resultJson?.optBoolean("hasPortraitPage") == true,
            hasLandscapePage = resultJson?.optBoolean("hasLandscapePage") == true,
            hasBoldText = resultJson?.optBoolean("hasBoldText") == true,
            loadingOverlayHidden = resultJson?.optBoolean("loadingOverlayHidden") == true,
            effectiveZoom = resultJson?.optDouble("effectiveZoom")?.toFloat() ?: Float.NaN,
            viewportWidth = resultJson?.optDouble("viewportWidth") ?: 0.0,
            viewportHeight = resultJson?.optDouble("viewportHeight") ?: 0.0,
            readyStage = resultJson?.optString("readyStage").orEmpty(),
            readyElapsedMs = resultJson?.optDouble("readyElapsedMs") ?: -1.0,
            details = rawValue
        )
    }

    private fun fixtureSuite(): List<DocxFixture> {
        val basicXml = """
            <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>DOCX preview parity fixture</w:t></w:r></w:p>
            <w:p><w:r><w:t>DOCX</w:t></w:r><w:r><w:t xml:space="preserve"> SplitRun</w:t></w:r></w:p>
            <w:p><w:r><w:t>हिंदी पाठ Jaipur</w:t></w:r></w:p>
            <w:tbl><w:tblPr/><w:tblGrid><w:gridCol w:w="2400"/></w:tblGrid>
              <w:tr><w:tc><w:p><w:r><w:t>visible table cell</w:t></w:r></w:p></w:tc></w:tr>
            </w:tbl>
        """.trimIndent()
        val imageXml = """
            <w:p><w:r><w:t>Image fixture visible caption</w:t></w:r></w:p>
            <w:p><w:r><w:drawing>
              <wp:inline distT="0" distB="0" distL="0" distR="0">
                <wp:extent cx="914400" cy="914400"/><wp:docPr id="1" name="Fixture image"/>
                <a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
                  <pic:pic><pic:nvPicPr><pic:cNvPr id="0" name="Fixture image"/><pic:cNvPicPr/></pic:nvPicPr>
                    <pic:blipFill><a:blip r:embed="rIdImage"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>
                    <pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="914400" cy="914400"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>
                  </pic:pic>
                </a:graphicData></a:graphic>
              </wp:inline>
            </w:drawing></w:r></w:p>
        """.trimIndent()
        val longXml = buildString {
            repeat(80) { index ->
                val value = if (index == 79) "long-document-end-marker" else "Long paragraph number $index with enough words to exercise flowing DOCX content across several rendered pages."
                append("<w:p><w:r><w:t>").append(value).append("</w:t></w:r></w:p>")
                if (index < 79 && (index + 1) % 20 == 0) {
                    append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                }
            }
        }

        return listOf(
            DocxFixture("basic", makeDocx(basicXml), listOf("DOCX preview parity fixture", "हिंदी पाठ", "visible table cell")),
            DocxFixture(
                "image", makeDocx(imageXml, imagePng = makePng()),
                listOf("Image fixture visible caption"), hasImage = true
            ),
            DocxFixture(
                "landscape",
                makeDocx("<w:p><w:r><w:t>landscape-page-marker</w:t></w:r></w:p>", finalSection = landscapeSection()),
                listOf("landscape-page-marker"), requireLandscapePage = true
            ),
            DocxFixture(
                "long", makeDocx(longXml), listOf("long-document-end-marker"), minimumPages = 2
            )
        )
    }

    private fun makeDocx(
        body: String,
        imagePng: ByteArray? = null,
        finalSection: String = portraitSection()
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            fun entry(name: String, content: String) = entry(name, content.toByteArray(Charsets.UTF_8))

            val imageContentType = if (imagePng != null) "<Default Extension=\"png\" ContentType=\"image/png\"/>" else ""
            entry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      $imageContentType
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                      <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                    </Types>""".trimIndent()
            )
            entry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""".trimIndent()
            )
            entry(
                "word/styles.xml",
                """<?xml version="1.0" encoding="UTF-8"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"/>"""
            )
            if (imagePng != null) {
                entry(
                    "word/_rels/document.xml.rels",
                    """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rIdImage" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
                    </Relationships>""".trimIndent()
                )
                entry("word/media/image1.png", imagePng)
            }
            entry(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                      xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
                      xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                      xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                      xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
                      <w:body>$body$finalSection</w:body>
                    </w:document>""".trimIndent()
            )
        }
        return output.toByteArray()
    }

    private fun portraitSection(): String =
        "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>"

    private fun landscapeSection(): String =
        "<w:sectPr><w:pgSz w:w=\"15840\" w:h=\"12240\" w:orient=\"landscape\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>"

    private fun makePng(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private data class DocxFixture(
        val name: String,
        val bytes: ByteArray,
        val expectedText: List<String>,
        val minimumPages: Int = 1,
        val hasImage: Boolean = false,
        val requireLandscapePage: Boolean = false
    )

    private data class RenderResult(
        val fixtureName: String,
        val fixtureBytes: Int,
        val fetchRequestCount: Int,
        val assetResponseMime: String,
        val assetResponseLength: Int,
        val fetchCount: Int,
        val fetchStatus: Int,
        val fetchBytes: Int,
        val fetchContentLength: Int,
        val fetchMime: String,
        val docxMagic: Boolean,
        val rendererAvailable: Boolean,
        val error: String?,
        val jsError: String?,
        val text: String,
        val pageCount: Int,
        val rendererPageCount: Int,
        val errorBannerText: String,
        val callbackPageCount: Int,
        val firstPageWidth: Float,
        val firstPageHeight: Float,
        val firstPageVisible: Boolean,
        val visibleContent: Boolean,
        val imageLoaded: Boolean,
        val hasPortraitPage: Boolean,
        val hasLandscapePage: Boolean,
        val hasBoldText: Boolean,
        val loadingOverlayHidden: Boolean,
        val effectiveZoom: Float,
        val viewportWidth: Double,
        val viewportHeight: Double,
        val readyStage: String,
        val readyElapsedMs: Double,
        val details: String
    )
}
