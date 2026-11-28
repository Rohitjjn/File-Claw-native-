package com.example.utils

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.core.format.DocxPrintLayoutPolicy
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.launch

/**
 * Native Print and Export helper using Android's PrintManager & PrintDocumentAdapter.
 * Works 100% offline to print or save documents as PDF.
 * Supports PDF, DOCX, TXT, and Markdown files.
 */
object FilePrintHelper {

    // Keep active webview references to prevent GC before print job is received
    private val activeWebViews = mutableSetOf<WebView>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    fun printPdf(
        context: Context,
        pdfFile: File,
        jobName: String = "FileClaw_Document"
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            android.widget.Toast.makeText(context, "Print service is not available on this device", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            android.widget.Toast.makeText(context, "Cannot print: PDF file is empty or missing", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        mainHandler.post {
            try {
                val printAdapter = object : PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: Bundle?
                    ) {
                        if (cancellationSignal?.isCanceled == true) {
                            callback?.onLayoutCancelled()
                            return
                        }
                        val info = PrintDocumentInfo.Builder(jobName)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                            .build()
                        callback?.onLayoutFinished(info, true)
                    }

                    override fun onWrite(
                        pages: Array<out PageRange>?,
                        destination: ParcelFileDescriptor?,
                        cancellationSignal: CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        if (destination == null) {
                            callback?.onWriteFailed("Print destination is null")
                            return
                        }
                        try {
                            FileInputStream(pdfFile).use { input ->
                                FileOutputStream(destination.fileDescriptor).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            callback?.onWriteFailed(e.message ?: "Print write error")
                        }
                    }
                }

                printManager.print(
                    jobName,
                    printAdapter,
                    PrintAttributes.Builder()
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build()
                )
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Print failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun printWebView(
        context: Context,
        webView: WebView,
        jobName: String = "FileClaw_Document"
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        mainHandler.post {
            val printAdapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(
                jobName,
                printAdapter,
                PrintAttributes.Builder()
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .build()
            )
        }
    }

    fun printDocx(
        context: Context,
        docxFile: File,
        title: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            android.widget.Toast.makeText(context, "Print service is not available on this device", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (!docxFile.exists() || docxFile.length() == 0L) {
            android.widget.Toast.makeText(context, "Cannot print: File is empty or missing", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        DocxPreviewDiagnostics.log(
            "file_route", "mode" to "print", "fileName" to docxFile.name,
            "fileSizeBytes" to docxFile.length(), "decision" to "print_webview"
        )

        mainHandler.post {
            var createdWebView: WebView? = null
            try {
                val tempWebView = WebView(context)
                // The print WebView is intentionally detached, but renderer readiness
                // now checks a real viewport. Give it the device display bounds before
                // loading so layout/visibility metrics are meaningful in print mode.
                val displayMetrics = context.resources.displayMetrics
                val viewportWidthPx = displayMetrics.widthPixels.coerceAtLeast(1)
                val viewportHeightPx = displayMetrics.heightPixels.coerceAtLeast(1)
                val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                    viewportWidthPx, android.view.View.MeasureSpec.EXACTLY
                )
                val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                    viewportHeightPx, android.view.View.MeasureSpec.EXACTLY
                )
                tempWebView.measure(widthSpec, heightSpec)
                tempWebView.layout(0, 0, viewportWidthPx, viewportHeightPx)
                val createdAtElapsedMs = android.os.SystemClock.elapsedRealtime()
                createdWebView = tempWebView
                activeWebViews.add(tempWebView)
                DocxPreviewDiagnostics.log(
                    "webview_created", "mode" to "print", "fileName" to docxFile.name,
                    "fileSizeBytes" to docxFile.length(),
                    "url" to "https://appassets.androidplatform.net/assets/docx_viewer/index.html?print=1&generation=1",
                    "widthPx" to tempWebView.width, "heightPx" to tempWebView.height
                )

                val docxAssetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context))
                    .addPathHandler("/docx-src/", object : androidx.webkit.WebViewAssetLoader.PathHandler {
                        override fun handle(path: String): android.webkit.WebResourceResponse? {
                            if (path != "document.docx") return null
                            val assetUrl = "https://appassets.androidplatform.net/docx-src/$path"
                            if (!docxFile.isFile) {
                                DocxPreviewDiagnostics.log(
                                    "asset_request", "mode" to "print", "assetRequestUrl" to assetUrl,
                                    "fileName" to docxFile.name, "responseStatus" to 404,
                                    "fileSizeBytes" to 0L
                                )
                                return null
                            }
                            val sizeBytes = docxFile.length()
                            DocxPreviewDiagnostics.log(
                                "asset_request", "mode" to "print", "assetRequestUrl" to assetUrl,
                                "fileName" to docxFile.name, "responseStatus" to 200,
                                "responseMime" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "responseLength" to sizeBytes
                            )
                            return runCatching {
                                android.webkit.WebResourceResponse(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    null,
                                    200,
                                    "OK",
                                    mapOf("Content-Length" to sizeBytes.toString()),
                                    DocxCountingInputStream(FileInputStream(docxFile), docxFile.name, sizeBytes)
                                )
                            }.onFailure { error ->
                                DocxPreviewDiagnostics.log(
                                    "asset_response_error", "mode" to "print", "fileName" to docxFile.name,
                                    "errorKind" to error::class.java.simpleName
                                )
                            }.getOrNull()
                        }
                    })
                    .build()

                tempWebView.setInitialScale(100)
                tempWebView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)
                    useWideViewPort = false
                    loadWithOverviewMode = false
                    allowFileAccess = false
                    allowContentAccess = false
                }
                DocxPreviewDiagnostics.log(
                    "webview_settings", "mode" to "print",
                    "javaScriptEnabled" to tempWebView.settings.javaScriptEnabled,
                    "domStorageEnabled" to tempWebView.settings.domStorageEnabled,
                    "useWideViewPort" to tempWebView.settings.useWideViewPort,
                    "loadWithOverviewMode" to tempWebView.settings.loadWithOverviewMode,
                    "allowFileAccess" to tempWebView.settings.allowFileAccess,
                    "allowContentAccess" to tempWebView.settings.allowContentAccess
                )

                var printSubmitted = false
                var printPreparing = false
                var fallbackPageLoaded = false
                var released = false
                var cleanupWatchdog: Runnable? = null
                val fallbackJob = kotlinx.coroutines.SupervisorJob()
                val fallbackScope = kotlinx.coroutines.CoroutineScope(
                    fallbackJob + kotlinx.coroutines.Dispatchers.Main.immediate
                )

                fun cleanupOnMain() {
                    if (released) return
                    released = true
                    cleanupWatchdog?.let(mainHandler::removeCallbacks)
                    fallbackJob.cancel()
                    activeWebViews.remove(tempWebView)
                    tempWebView.stopLoading()
            tempWebView.removeJavascriptInterface("DocxBridge")
            tempWebView.destroy()
                }

                fun cleanup() {
                    if (android.os.Looper.myLooper() == Looper.getMainLooper()) cleanupOnMain()
                    else mainHandler.post { cleanupOnMain() }
                }

                fun scheduleCleanupWatchdog() {
                    cleanupWatchdog?.let(mainHandler::removeCallbacks)
                    val watchdog = Runnable { cleanupOnMain() }
                    cleanupWatchdog = watchdog
                    // PrintDocumentAdapter.onFinish handles normal completion/cancel. This is
                    // only a final guard if Android never returns the adapter callback.
                    mainHandler.postDelayed(watchdog, 10 * 60 * 1000L)
                }

                fun submitWebViewForPrint() {
                    if (released || printSubmitted) return
                    printSubmitted = true
                    val delegate = tempWebView.createPrintDocumentAdapter(title)
                    val adapter = object : PrintDocumentAdapter() {
                        override fun onStart() = delegate.onStart()

                        override fun onLayout(
                            oldAttributes: PrintAttributes?,
                            newAttributes: PrintAttributes?,
                            cancellationSignal: CancellationSignal?,
                            callback: LayoutResultCallback?,
                            extras: Bundle?
                        ) = delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)

                        override fun onWrite(
                            pages: Array<out PageRange>?,
                            destination: ParcelFileDescriptor?,
                            cancellationSignal: CancellationSignal?,
                            callback: WriteResultCallback?
                        ) = delegate.onWrite(pages, destination, cancellationSignal, callback)

                        override fun onFinish() {
                            try {
                                delegate.onFinish()
                            } finally {
                                cleanup()
                            }
                        }
                    }
                    try {
                        printManager.print(
                            title,
                            adapter,
                            PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                                .build()
                        )
                        if (!released) scheduleCleanupWatchdog()
                    } catch (error: Exception) {
                        cleanup()
                        android.widget.Toast.makeText(
                            context, "Print failed: ${error.message}", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                var printVisualStateRequestId = 0L
                fun triggerPrint() {
                    if (printSubmitted || printPreparing || released) return
                    printPreparing = true
                    tempWebView.evaluateJavascript("JSON.stringify(DocxViewer.prepareForPrint())") { encoded ->
                        if (released) return@evaluateJavascript
                        val prepared = runCatching {
                            org.json.JSONObject(org.json.JSONTokener(encoded).nextValue().toString())
                        }.getOrNull()
                        val scale = prepared?.optDouble("scale", Double.NaN) ?: Double.NaN
                        val viewportWidth = prepared?.optDouble("viewportWidth", 0.0) ?: 0.0
                        val rootWidth = prepared?.optDouble("rootWidth", 0.0) ?: 0.0
                        val pageWidth = prepared?.optDouble("pageWidth", 0.0) ?: 0.0
                        val valid = prepared != null && prepared.optBoolean("ready") &&
                            DocxPrintLayoutPolicy.isReadyForPrint(
                                scale = scale,
                                viewportWidth = viewportWidth,
                                rootWidth = rootWidth,
                                pageWidth = pageWidth,
                                transform = prepared.optString("transform")
                            )
                        DocxPreviewDiagnostics.log(
                            "print_layout_prepared", "mode" to "print", "scale" to scale,
                            "viewportWidthCss" to viewportWidth, "rootWidthCss" to rootWidth,
                            "pageWidthCss" to pageWidth, "transform" to (prepared?.optString("transform") ?: "missing"),
                            "layoutReady" to valid
                        )
                        if (!valid) {
                            printPreparing = false
                            fallbackTextPrint("Print layout did not reach full-width 100% scale")
                            return@evaluateJavascript
                        }
                        tempWebView.postVisualStateCallback(
                            ++printVisualStateRequestId,
                            object : WebView.VisualStateCallback() {
                                override fun onComplete(requestId: Long) {
                                    printPreparing = false
                                    submitWebViewForPrint()
                                }
                            }
                        )
                    }
                }

                fun fallbackTextPrint(reason: String) {
                    if (printSubmitted || released) return
                    printSubmitted = true
                    fallbackScope.launch {
                        val extracted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching { com.example.core.format.DocxExtractor.extractText(docxFile) }
                                .getOrElse {
                                    com.example.core.format.DocxExtractor.ExtractedDocx(
                                        docxFile.name, emptyList(), "", 0, 0
                                    )
                                }
                        }
                        if (released) return@launch
                        val paragraphsHtml = if (extracted.paragraphs.isEmpty()) {
                            "<p>No text could be recovered within the DOCX recovery safety limits.</p>"
                        } else {
                            extracted.paragraphs.joinToString("\n") { p ->
                                val escaped = escapeHtml(p)
                                if (p.startsWith("#") || p.length < 50 && !p.endsWith(".")) {
                                    "<h3>$escaped</h3>"
                                } else {
                                    "<p>$escaped</p>"
                                }
                            }
                        }
                        val truncationNotice = if (extracted.truncated) {
                            "<p><em>Recovered text was truncated at the safety limit.</em></p>"
                        } else ""
                        val escapedTitle = escapeHtml(title)
                        val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="utf-8">
                                <style>
                                    body { font-family: sans-serif; font-size: 13px; line-height: 1.6; padding: 24px; color: #1a1a1a; }
                                    h2 { margin-top: 0; color: #111; font-size: 20px; border-bottom: 2px solid #ccc; padding-bottom: 8px; }
                                    h3 { color: #222; margin-top: 16px; margin-bottom: 8px; font-size: 15px; }
                                    p { margin: 8px 0; }
                                </style>
                            </head>
                            <body>
                                <h2>$escapedTitle</h2>
                                $truncationNotice
                                $paragraphsHtml
                            </body>
                            </html>
                        """.trimIndent()

                        tempWebView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (fallbackPageLoaded || released) return
                                fallbackPageLoaded = true
                                submitWebViewForPrint()
                            }
                        }
                        tempWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        android.widget.Toast.makeText(
                            context,
                            "Printed with bounded text recovery ($reason)",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                tempWebView.addJavascriptInterface(object : Any() {
                    @android.webkit.JavascriptInterface
                    fun onDocumentVisuallyRendered(payload: String) {
                        mainHandler.post {
                            val metrics = runCatching { org.json.JSONObject(payload) }.getOrNull()
                            val pages = metrics?.optInt("pageCount", 0) ?: 0
                            val contentHeight = metrics?.optInt("contentHeight", 0) ?: 0
                            val visiblePages = metrics?.optInt("visiblePageCount", 0) ?: 0
                            DocxPreviewDiagnostics.log(
                                "android_document_visually_rendered", "mode" to "print", "pages" to pages,
                                "contentHeight" to contentHeight, "visiblePages" to visiblePages,
                                "callbackElapsedMs" to (android.os.SystemClock.elapsedRealtime() - createdAtElapsedMs),
                                "webViewWidthPx" to tempWebView.width,
                                "webViewHeightPx" to tempWebView.height,
                                "documentReady" to (pages > 0 && contentHeight > 0 && visiblePages > 0)
                            )
                            if (pages > 0 && contentHeight > 0 && visiblePages > 0) {
                                triggerPrint()
                            } else {
                                fallbackTextPrint("No visible pages")
                            }
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun onFailure(payload: String) {
                        mainHandler.post {
                            val error = runCatching { org.json.JSONObject(payload).optString("code", "render failed") }
                                .getOrDefault("render failed")
                            DocxPreviewDiagnostics.log("document_error", "mode" to "print", "reason" to error)
                            fallbackTextPrint(error)
                        }
                    }
                }, "DocxBridge")

                tempWebView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        DocxPreviewDiagnostics.log(
                            "webview_lifecycle", "mode" to "print", "lifecycle" to "page_started",
                            "url" to (url ?: "")
                        )
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val uri = request?.url ?: return null
                        if (uri.path?.startsWith("/docx-src/") == true) {
                            DocxPreviewDiagnostics.log(
                                "asset_intercept", "mode" to "print", "assetRequestUrl" to uri.toString(),
                                "lifecycle" to "shouldInterceptRequest"
                            )
                        }
                        val local = uri.scheme == "https" && uri.host == "appassets.androidplatform.net"
                        val response = if (local) docxAssetLoader.shouldInterceptRequest(uri) else null
                        return response ?: android.webkit.WebResourceResponse(
                            "text/plain", "utf-8", 403, "Forbidden", emptyMap(),
                            java.io.ByteArrayInputStream(ByteArray(0))
                        )
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        DocxPreviewDiagnostics.log(
                            "webview_lifecycle", "mode" to "print", "lifecycle" to "page_finished",
                            "url" to (url ?: "")
                        )
                        view?.evaluateJavascript("DocxViewer.setTheme(false);", null)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val uri = request?.url ?: return true
                        return !(uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
                            (uri.path?.startsWith("/assets/docx_viewer/") == true || uri.path?.startsWith("/docx-src/") == true))
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        DocxPreviewDiagnostics.log(
                            "webview_resource_error", "mode" to "print",
                            "url" to (request?.url?.toString() ?: ""),
                            "errorCode" to (error?.errorCode ?: 0),
                            "isMainFrame" to (request?.isForMainFrame == true)
                        )
                        if (request?.isForMainFrame == true) fallbackTextPrint("Editor page load error")
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        errorResponse: android.webkit.WebResourceResponse?
                    ) {
                        DocxPreviewDiagnostics.log(
                            "webview_http_error", "mode" to "print",
                            "url" to (request?.url?.toString() ?: ""),
                            "responseStatus" to (errorResponse?.statusCode ?: 0)
                        )
                    }
                }
                tempWebView.webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                        val level = message?.messageLevel()
                        if (level == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                            val source = message?.sourceId()?.substringAfterLast('/')?.take(100).orEmpty()
                            android.util.Log.e(
                                DocxPreviewDiagnostics.TAG,
                                "mode=print javascript_console_error source=$source line=${message?.lineNumber()}"
                            )
                        }
                        return true
                    }
                }

                mainHandler.postDelayed({
                    if (!printSubmitted) fallbackTextPrint("Format timeout")
                }, 12000)
                tempWebView.loadUrl(
                    "https://appassets.androidplatform.net/assets/docx_viewer/index.html?print=1&generation=1&file=" +
                        android.net.Uri.encode("https://appassets.androidplatform.net/docx-src/document.docx")
                )
            } catch (e: Exception) {
                createdWebView?.let { webView ->
                    activeWebViews.remove(webView)
                    runCatching { webView.stopLoading() }
                    runCatching { webView.destroy() }
                }
                android.widget.Toast.makeText(context, "Print initialization error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun printMarkdown(
        context: Context,
        markdownContent: String,
        title: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            android.widget.Toast.makeText(context, "Print service is not available on this device", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        mainHandler.post {
            try {
                val parser = org.intellij.markdown.parser.MarkdownParser(org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor())
                val parsedTree = parser.buildMarkdownTreeFromString(markdownContent)
                val rawHtml = com.example.core.text.HtmlSanitizer.sanitize(
                    org.intellij.markdown.html.HtmlGenerator(markdownContent, parsedTree, org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor()).generateHtml(),
                    keepImages = true
                )

                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="utf-8">
                        <style>
                            body {
                                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                                font-size: 13px;
                                line-height: 1.6;
                                padding: 24px;
                                color: #1a1a1a;
                            }
                            h1, h2, h3 { color: #111; margin-top: 16px; margin-bottom: 8px; }
                            h1 { font-size: 22px; border-bottom: 2px solid #ddd; padding-bottom: 6px; }
                            h2 { font-size: 18px; }
                            p { margin: 8px 0; }
                            pre { background: #f5f5f5; padding: 12px; border-radius: 6px; font-family: monospace; font-size: 12px; white-space: pre-wrap; }
                            code { font-family: monospace; background: #eee; padding: 2px 4px; border-radius: 3px; font-size: 12px; }
                            table { border-collapse: collapse; width: 100%; margin: 12px 0; }
                            th, td { border: 1px solid #ccc; padding: 8px; text-align: left; }
                            th { background: #f0f0f0; }
                            blockquote { border-left: 4px solid #cc785c; margin: 8px 0; padding-left: 12px; color: #555; }
                        </style>
                    </head>
                    <body>
                        $rawHtml
                    </body>
                    </html>
                """.trimIndent()

                val tempWebView = WebView(context)
                activeWebViews.add(tempWebView)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val printAdapter = tempWebView.createPrintDocumentAdapter(title)
                        printManager.print(
                            title,
                            printAdapter,
                            PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                                .build()
                        )
                        mainHandler.postDelayed({
                            activeWebViews.remove(tempWebView)
                            tempWebView.destroy()
                        }, 60000)
                    }
                }
                tempWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            } catch (e: Exception) {
                // Fallback to text
                printTextOrMarkdown(context, markdownContent, title)
            }
        }
    }

    fun printTextOrMarkdown(
        context: Context,
        content: String,
        title: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            android.widget.Toast.makeText(context, "Print service is not available on this device", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        mainHandler.post {
            val tempWebView = WebView(context)
            activeWebViews.add(tempWebView)

            val escaped = escapeHtml(content)
            val escapedTitle = escapeHtml(title)

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                            font-size: 13px;
                            line-height: 1.6;
                            padding: 24px;
                            color: #1a1a1a;
                        }
                        h2 {
                            margin-top: 0;
                            color: #111;
                            font-size: 18px;
                        }
                        hr {
                            border: none;
                            border-top: 1px solid #ddd;
                            margin: 12px 0 20px 0;
                        }
                        pre {
                            white-space: pre-wrap;
                            word-wrap: break-word;
                            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
                            font-size: 11px;
                            background: #f8f8f8;
                            padding: 12px;
                            border-radius: 6px;
                            line-height: 1.5;
                        }
                    </style>
                </head>
                <body>
                    <h2>$escapedTitle</h2>
                    <hr>
                    <pre>$escaped</pre>
                </body>
                </html>
            """.trimIndent()

            tempWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printAdapter = tempWebView.createPrintDocumentAdapter(title)
                    printManager.print(
                        title,
                        printAdapter,
                        PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                            .build()
                    )
                    mainHandler.postDelayed({
                        activeWebViews.remove(tempWebView)
                        tempWebView.destroy()
                    }, 60000)
                }
            }
            tempWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }

    fun printImage(
        context: Context,
        imageFile: File,
        title: String
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        mainHandler.post {
            val tempWebView = WebView(context)
            activeWebViews.add(tempWebView)
            val imageUri = android.net.Uri.fromFile(imageFile).toString()
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <style>
                        body {
                            margin: 0;
                            padding: 16px;
                            text-align: center;
                            font-family: sans-serif;
                        }
                        h3 {
                            margin-top: 0;
                            color: #333;
                        }
                        img {
                            max-width: 100%;
                            max-height: 90vh;
                            height: auto;
                            object-fit: contain;
                            display: block;
                            margin: 0 auto;
                        }
                    </style>
                </head>
                <body>
                    <h3>$title</h3>
                    <img src="$imageUri" />
                </body>
                </html>
            """.trimIndent()

            tempWebView.settings.apply {
                allowFileAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
            }

            tempWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printAdapter = tempWebView.createPrintDocumentAdapter(title)
                    printManager.print(
                        title,
                        printAdapter,
                        PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                            .build()
                    )
                    mainHandler.postDelayed({
                        activeWebViews.remove(tempWebView)
                        tempWebView.destroy()
                    }, 60000)
                }
            }
            tempWebView.loadDataWithBaseURL("file:///", html, "text/html", "UTF-8", null)
        }
    }
}
