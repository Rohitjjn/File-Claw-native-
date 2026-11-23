package com.example.ui.preview.docx

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.io.FileInputStream

/** Local-only renderer host with an asset-loader stream for the selected DOCX. */
@Composable
internal fun DocxWebViewHost(
    file: File,
    generation: Long,
    dark: Boolean,
    paper: String,
    pagePaper: String,
    ink: String,
    edge: String,
    zoom: Float,
    searchQuery: String,
    next: Int,
    previous: Int,
    scrollToPage: Int?,
    onVisualRender: (Long, Int, Int, Int, Int) -> Unit,
    onPageChanged: (Int, Int) -> Unit,
    onProgress: (Long, Long, Long) -> Unit,
    onFailure: (Long, String) -> Unit,
    onSearch: (Int, Int, Boolean) -> Unit,
    onZoom: (Float) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: (Float) -> Unit,
    onCreated: (WebView?) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchCursor by remember(generation) { mutableStateOf(DocxSearchController.Cursor()) }
    var viewerReady by remember(generation) { mutableStateOf(false) }
    var lastPage by remember(generation) { mutableStateOf<Int?>(null) }
    val alive = remember(generation) { java.util.concurrent.atomic.AtomicBoolean(true) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            lateinit var web: WebView
            alive.set(true)
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/docx-src/", object : WebViewAssetLoader.PathHandler {
                    override fun handle(path: String): WebResourceResponse? {
                        if (path != "document.docx" || !file.isFile) return null
                        val size = file.length()
                        return runCatching {
                            WebResourceResponse(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                null, 200, "OK", mapOf("Content-Length" to size.toString()),
                                FileInputStream(file)
                            )
                        }.getOrNull()
                    }
                }).build()
            web = WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.parseColor(paper))
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)
                    useWideViewPort = false
                    loadWithOverviewMode = false
                }
                var visualStateRequestId = 0L
                val bridge = DocxJsBridge(
                    isCurrent = { alive.get() },
                    onVisualRender = { id, pages, height, width, visiblePages ->
                        if (id == generation && alive.get()) {
                            // DOM geometry is necessary but does not prove Chromium has committed
                            // the corresponding pixels. Hold Compose readiness until that frame.
                            web.postVisualStateCallback(++visualStateRequestId) {
                                if (alive.get() && id == generation) {
                                    viewerReady = true
                                    onVisualRender(id, pages, height, width, visiblePages)
                                }
                            }
                        }
                    },
                    onPageChanged = onPageChanged,
                    onProgress = { id, loaded, total -> if (id == generation) onProgress(id, loaded, total) },
                    onFailure = { id, code -> if (id == generation) onFailure(id, code) },
                    onSearch = onSearch,
                    onZoom = onZoom,
                    onTap = onTap,
                    onDoubleTap = onDoubleTap
                )
                addJavascriptInterface(bridge, "DocxBridge")
                webViewClient = DocxWebViewClient(
                    assets = loader,
                    onFailure = { code -> if (alive.get()) onFailure(generation, code) },
                    onPageFinished = { page ->
                        if (alive.get() && page === web) page.evaluateJavascript(
                            "DocxViewer.setZoom(${DocxZoomAndGesture.coerce(zoom)});DocxViewer.setTheme($dark,${org.json.JSONObject.quote(paper)},${org.json.JSONObject.quote(pagePaper)},${org.json.JSONObject.quote(ink)},${org.json.JSONObject.quote(edge)});", null
                        )
                    }
                )
                onCreated(this)
                val url = "https://appassets.androidplatform.net/assets/docx_viewer/index.html?file=" +
                    android.net.Uri.encode("https://appassets.androidplatform.net/docx-src/document.docx") +
                    "&generation=$generation&dark=${if (dark) 1 else 0}"
                loadUrl(url)
            }
            web
        },
        update = { web ->
            web.setBackgroundColor(android.graphics.Color.parseColor(paper))
            if (web.progress >= 100 && viewerReady) {
                val plan = DocxSearchController.plan(searchCursor, searchQuery, next, previous)
                searchCursor = plan.cursor
                val actions = buildString {
                    append("DocxViewer.setZoom(${DocxZoomAndGesture.coerce(zoom)});")
                    append("DocxViewer.setTheme($dark,${org.json.JSONObject.quote(paper)},${org.json.JSONObject.quote(pagePaper)},${org.json.JSONObject.quote(ink)},${org.json.JSONObject.quote(edge)});")
                    if (scrollToPage != null && scrollToPage != lastPage) {
                        append("DocxViewer.goToPage($scrollToPage);")
                        lastPage = scrollToPage
                    }
                    plan.commands.forEach { command ->
                        when (command) {
                            is DocxSearchController.Command.Search -> append("DocxViewer.search(${org.json.JSONObject.quote(command.query)});")
                            DocxSearchController.Command.Next -> append("DocxViewer.next();")
                            DocxSearchController.Command.Previous -> append("DocxViewer.previous();")
                            DocxSearchController.Command.Clear -> append("DocxViewer.clearSearch();")
                        }
                    }
                }
                if (actions.isNotEmpty()) web.evaluateJavascript(actions, null)
            }
        },
        onRelease = { web ->
            alive.set(false)
            onCreated(null)
            web.stopLoading()
            web.removeJavascriptInterface("DocxBridge")
            web.destroy()
        }
    )
}
