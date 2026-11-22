package com.example.ui.preview.docx

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import android.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class DocxPreviewInstrumentationTest {
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
                    @JavascriptInterface fun onReady(payload: String) { if (JSONObject(payload).optInt("pages") > 0) ready.countDown() }
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
            instrumentation.runOnMainSync { web.stopLoading(); web.removeJavascriptInterface("DocxBridge"); web.destroy() }
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
                    @JavascriptInterface fun onReady(payload: String) { ready.countDown() }
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
            instrumentation.runOnMainSync { web.stopLoading(); web.removeJavascriptInterface("DocxBridge"); web.destroy() }
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
                    @JavascriptInterface fun onReady(payload: String) { result.set(JSONObject(payload)); ready.countDown() }
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
            assertTrue("Expected render-ready callback with estimated pages", (result.get()?.optInt("pages") ?: 0) > 0)
            val evaluated = CountDownLatch(1)
            val dom = AtomicReference<String>()
            instrumentation.runOnMainSync {
                web.evaluateJavascript("DocxViewer.search('styled phrase'); JSON.stringify({text:document.body.innerText,pages:document.querySelectorAll('.docx,section.docx').length,image:(function(){var i=document.querySelector('#scaled-document img');return !!i&&i.complete&&i.naturalWidth>0})(),visible:(function(){var r=document.querySelector('.docx,section.docx').getBoundingClientRect();return r.width>10&&r.height>10&&r.bottom>0&&r.top<innerHeight})()})") { value -> dom.set(value); evaluated.countDown() }
            }
            assertTrue("DOM evaluation timed out", evaluated.await(10, TimeUnit.SECONDS))
            val jsonText = JSONObject(org.json.JSONTokener(dom.get()).nextValue().toString())
            assertTrue("Rendered text absent", jsonText.optString("text").contains("हिंदी पाठ"))
            assertTrue("Table content absent", jsonText.optString("text").contains("visible table cell"))
            assertTrue("Page is not in visible viewport", jsonText.optBoolean("visible"))
            assertTrue("Explicit page break did not create multiple page nodes", jsonText.optInt("pages") >= 2)
            assertTrue("Related image did not load", jsonText.optBoolean("image"))
            assertTrue("Cross-run search did not report its match", searched.await(10, TimeUnit.SECONDS))
            assertTrue("Expected styled phrase match", matchCount.get() > 0)

            instrumentation.runOnMainSync {
                web.evaluateJavascript("DocxViewer.search('styled phrase');", null)
                val bitmap = Bitmap.createBitmap(web.width, web.height, Bitmap.Config.ARGB_8888)
                web.draw(android.graphics.Canvas(bitmap))
                val bg = Color.rgb(250, 249, 245)
                var changed = 0
                for (y in 0 until bitmap.height step 3) for (x in 0 until bitmap.width step 3) {
                    if (Color.rgb(Color.red(bitmap.getPixel(x, y)), Color.green(bitmap.getPixel(x, y)), Color.blue(bitmap.getPixel(x, y))) != bg) changed++
                }
                bitmap.recycle()
                assertTrue("Rendered WebView pixels are indistinguishable from the blank background", changed > 50)
            }
        } finally {
            instrumentation.runOnMainSync { web.stopLoading(); web.removeJavascriptInterface("DocxBridge"); web.destroy() }
        }
    }

    private fun fixtureDocx(): ByteArray {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>
            <w:p><w:r><w:t>styled </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>phrase</w:t></w:r></w:p>
            <w:p><w:r><w:t>हिंदी पाठ</w:t></w:r></w:p>
            <w:tbl><w:tblPr><w:tblW w:w="0" w:type="auto"/></w:tblPr><w:tr><w:tc><w:p><w:r><w:t>visible table cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
            <w:p><w:r><w:drawing><wp:inline><wp:extent cx="914400" cy="914400"/><wp:docPr id="1" name="fixture image"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="0" name="fixture.png"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="rIdImg"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="914400" cy="914400"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
            <w:p><w:r><w:br w:type="page"/><w:t>Second page marker</w:t></w:r></w:p>
            <w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>
            </w:body></w:document>""".trimIndent()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) { zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("[Content_Types].xml", """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry("word/_rels/document.xml.rels", """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdImg" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/fixture.png"/></Relationships>""")
            entry("word/document.xml", xml)
            zip.putNextEntry(ZipEntry("word/media/fixture.png"))
            zip.write(Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jE9sAAAAASUVORK5CYII=", Base64.DEFAULT))
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
