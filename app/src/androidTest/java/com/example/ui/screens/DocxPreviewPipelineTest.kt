package com.example.ui.screens

import android.content.Context
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DocxPreviewPipelineTest {

    @Test
    fun normalPreviewAndPrintPipelineRenderVisibleSameFixtureContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val normal = render(context, printMode = false)
        val printed = render(context, printMode = true)

        assertTrue("Normal preview failed: ${normal.error}", normal.error == null)
        assertTrue("Print preview failed: ${printed.error}", printed.error == null)
        assertTrue("Normal preview did not render the fixture text: ${normal.details}", normal.visible)
        assertTrue("Print preview did not render the fixture text: ${printed.details}", printed.visible)
        assertTrue("The normal preview and print path diverged: ${normal.details} / ${printed.details}",
            normal.text == printed.text)
        assertTrue("The fixture's Hindi text was not visible: ${normal.text}", normal.text.contains("हिंदी पाठ"))
        assertTrue("The table cell was not visible: ${normal.text}", normal.text.contains("visible table cell"))
        assertTrue("Normal preview reported no rendered page", normal.pageCount > 0)
        assertTrue("Print preview reported no rendered page", printed.pageCount > 0)
        assertEquals("Resize should preserve one renderer load", 1, normal.loadCount)
        assertEquals("Resize should preserve one print renderer load", 1, printed.loadCount)
        assertTrue("Cross-run search failed in normal preview", normal.crossRunMatches)
        assertTrue("Cross-run search failed in print WebView", printed.crossRunMatches)
    }

    private fun render(context: Context, printMode: Boolean): RenderResult {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val rendered = CountDownLatch(1)
        val evaluated = CountDownLatch(1)
        val error = AtomicReference<String?>(null)
        val details = AtomicReference<String>("")
        val pageCount = AtomicReference(0)
        val loadCount = java.util.concurrent.atomic.AtomicInteger(0)
        val started = AtomicBoolean(false)
        lateinit var webView: WebView
        val fixture = makeDocxFixture()
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/docx-src/", object : WebViewAssetLoader.PathHandler {
                override fun handle(path: String): WebResourceResponse? =
                    if (path == "document.docx") {
                        WebResourceResponse(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            null,
                            ByteArrayInputStream(fixture)
                        )
                    } else null
            })
            .build()

        instrumentation.runOnMainSync {
            webView = WebView(context)
            webView.setBackgroundColor(Color.WHITE)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.allowFileAccess = false
            webView.settings.allowContentAccess = false
            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun onDocumentLoaded(count: Int) {
                    loadCount.incrementAndGet()
                    pageCount.set(count)
                    rendered.countDown()
                }

                @JavascriptInterface
                fun onDocumentError(message: String) {
                    error.set(message)
                    rendered.countDown()
                }
            }, "Android")
            webView.webChromeClient = WebChromeClient()
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                    request?.url?.let(loader::shouldInterceptRequest)

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (started.compareAndSet(false, true)) {
                        view?.evaluateJavascript("setZoom(1); loadDocx(false);", null)
                    }
                }
            }
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, 1080, 1920)
            val url = "https://appassets.androidplatform.net/assets/editor/docx_editor.html" +
                if (printMode) "?print=1" else ""
            webView.loadUrl(url)
        }

        try {
            assertTrue("Timed out waiting for DOCX render (print=$printMode)", rendered.await(30, TimeUnit.SECONDS))
            if (error.get() == null) {
                instrumentation.runOnMainSync {
                    webView.evaluateJavascript(
                        """(function(){
                            window.dispatchEvent(new Event('resize'));
                            var page=document.querySelector('#output .docx');
                            if(!page) return JSON.stringify({visible:false,text:'',width:0,height:0});
                            var rect=page.getBoundingClientRect();
                            var text=(page.innerText||page.textContent||'').replace(/\\s+/g,' ').trim();
                            searchWord('DOCX SplitRun');
                            var crossRunMatches=searchMatches.length===1 && searchMatches[0].length>=2;
                            return JSON.stringify({visible:!!text && rect.width>0 && rect.height>0,text:text,width:rect.width,height:rect.height,crossRunMatches:crossRunMatches});
                        })()""".trimIndent()
                    ) { value ->
                        details.set(value ?: "")
                        evaluated.countDown()
                    }
                }
                assertTrue("Timed out reading rendered DOM", evaluated.await(5, TimeUnit.SECONDS))
            }
        } finally {
            instrumentation.runOnMainSync {
                webView.stopLoading()
                webView.removeJavascriptInterface("Android")
                webView.destroy()
            }
        }

        val value = details.get()
        val domResult = runCatching {
            org.json.JSONObject(org.json.JSONArray("[$value]").getString(0))
        }.getOrNull()
        val text = domResult?.optString("text").orEmpty()
        val visible = domResult?.optBoolean("visible") == true &&
            domResult.optDouble("width") > 0.0 && domResult.optDouble("height") > 0.0
        return RenderResult(
            visible, text, pageCount.get(), loadCount.get(), error.get(),
            domResult?.optBoolean("crossRunMatches") == true, value
        )
    }

    private fun makeDocxFixture(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                      <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                    </Types>""".trimIndent()
            )
            entry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>""".trimIndent()
            )
            entry(
                "word/styles.xml",
                """<?xml version="1.0" encoding="UTF-8"?><w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"/>"""
            )
            entry(
                "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>DOCX preview parity fixture</w:t></w:r></w:p>
                        <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>DOCX</w:t></w:r><w:r><w:t xml:space="preserve"> SplitRun</w:t></w:r></w:p>
                        <w:p><w:r><w:t>हिंदी पाठ Jaipur</w:t></w:r></w:p>
                        <w:tbl><w:tblPr/><w:tblGrid><w:gridCol w:w="2400"/></w:tblGrid>
                          <w:tr><w:tc><w:p><w:r><w:t>visible table cell</w:t></w:r></w:p></w:tc></w:tr>
                        </w:tbl>
                        <w:sectPr><w:pgSz w:w="12240" w:h="15840"/></w:sectPr>
                      </w:body>
                    </w:document>""".trimIndent()
            )
        }
        return output.toByteArray()
    }

    private data class RenderResult(
        val visible: Boolean,
        val text: String,
        val pageCount: Int,
        val loadCount: Int,
        val error: String?,
        val crossRunMatches: Boolean,
        val details: String
    )
}
