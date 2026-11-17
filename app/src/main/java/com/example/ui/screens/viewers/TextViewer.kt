package com.example.ui.screens.viewers

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.component.ViewerSyntaxTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.example.ui.screens.InViewerSearchCommandPolicy
import com.example.ui.screens.buildPlainTextPreviewHtml

@Composable
internal fun PlainTextPreview(
    content: String,
    isDarkMode: Boolean,
    fontSizeSetting: String,
    searchQuery: String,
    searchTriggerNext: Int,
    searchTriggerPrev: Int,
    onSearchResultCount: (Int, Int, Boolean) -> Unit,
    onSingleTap: () -> Unit,
    syntaxTheme: ViewerSyntaxTheme = ViewerSyntaxTheme.AUTO,
    viewerFontSizeSp: Int = 16,
    zoomPercent: Int = 100,
    scrollRequest: ViewerScrollRequest? = null,
    onScrollRequestHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var generatedHtml by remember(content, isDarkMode, viewerFontSizeSp, syntaxTheme) { mutableStateOf<String?>(null) }
    LaunchedEffect(content, isDarkMode, viewerFontSizeSp, syntaxTheme) {
        // Viewer Appearance drives the base font size; falls back to the settings mapping.
        val fontSizePx = viewerFontSizeSp.coerceIn(11, 22)
        generatedHtml = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val themeBg = if (syntaxTheme.isAuto) null else syntaxTheme.hexBg
            val themeFg = if (syntaxTheme.isAuto) null else syntaxTheme.hexText
            buildPlainTextPreviewHtml(content, isDarkMode, fontSizePx, themeBg, themeFg)
        }
    }
    SearchableDocumentWebView(
        html = generatedHtml,
        backgroundColor = if (syntaxTheme.isAuto) {
            if (isDarkMode) 0xFF151514.toInt() else 0xFFfaf9f5.toInt()
        } else {
            runCatching {
                android.graphics.Color.parseColor(syntaxTheme.hexBg)
            }.getOrDefault(if (isDarkMode) 0xFF151514.toInt() else 0xFFfaf9f5.toInt())
        },
        searchQuery = searchQuery,
        searchTriggerNext = searchTriggerNext,
        searchTriggerPrev = searchTriggerPrev,
        onSearchResultCount = onSearchResultCount,
        onSingleTap = onSingleTap,
        zoomPercent = zoomPercent,
        scrollRequest = scrollRequest,
        onScrollRequestHandled = onScrollRequestHandled,
        loadingMessage = "Preparing Text...",
        modifier = modifier
    )
}

@Composable
internal fun SearchableDocumentWebView(
    html: String?,
    backgroundColor: Int,
    searchQuery: String,
    searchTriggerNext: Int,
    searchTriggerPrev: Int,
    onSearchResultCount: (Int, Int, Boolean) -> Unit,
    onSingleTap: () -> Unit,
    loadingMessage: String,
    zoomPercent: Int = 100,
    scrollRequest: ViewerScrollRequest? = null,
    onScrollRequestHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val latestSearchResultCallback = androidx.compose.runtime.rememberUpdatedState(onSearchResultCount)
    val latestSingleTapCallback = androidx.compose.runtime.rememberUpdatedState(onSingleTap)
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var isPageReady by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    // Ledger of what the generated document's search engine has already been told.
    // Loop 12 (Bug B): this used to be two ints plus an implicit "always search first"
    // rule, so every arrow press re-ran searchWord(), reset the renderer's index to 0
    // and advanced one step — the counter could never walk past 2 of N matches.
    var issuedSearch by remember { mutableStateOf(InViewerSearchCommandPolicy.Issued()) }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { viewContext ->
            android.webkit.WebView(viewContext).apply {
                webViewRef = this
                setBackgroundColor(backgroundColor)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                addJavascriptInterface(
                    WebAppInterface(
                        viewContext,
                        onSearchResultsChanged = { count, index, truncated -> latestSearchResultCallback.value(count, index, truncated) },
                        onTapRequested = { latestSingleTapCallback.value() }
                    ),
                    "Android"
                )
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.post {
                            isPageReady = true
                            isLoading = false
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        return !(url.startsWith("about:") || url.startsWith("data:"))
                    }
                }
            }
        },
        update = { webView ->
            if (html != null && webView.tag != html) {
                webView.tag = html
                isPageReady = false
                isLoading = true
                webView.setBackgroundColor(backgroundColor)
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        // Loop 12 (Bug E): regenerated HTML used to leak the previous WebView (and its
        // renderer process) until finalizer GC. Destroy it deterministically when the
        // node leaves composition (file switch / screen exit).
        onRelease = { webView ->
            webView.tag = null
            webView.destroy()
        },
        modifier = Modifier.fillMaxSize()
    )

    // Loop 12 (Bug B): the query step and the navigation step are now planned by
    // InViewerSearchCommandPolicy. Arrow presses issue ONLY next/prev deltas, so the
    // renderer walks the full navigable match list (0/1/2/7/100+ all reachable in both
    // directions with wrap-around). A changed query still searches exactly once, first.
    LaunchedEffect(webViewRef, isPageReady, searchQuery, searchTriggerNext, searchTriggerPrev) {
        val webView = webViewRef
        if (webView != null && isPageReady) {
            val plan = InViewerSearchCommandPolicy.plan(issuedSearch, searchQuery, searchTriggerNext, searchTriggerPrev)
            if (!plan.isEmpty) {
                if (plan.search && issuedSearch.query != null) {
                    // Debounce: typing re-highlights the whole DOM once the user pauses,
                    // instead of once per keystroke (big documents used to stutter).
                    kotlinx.coroutines.delay(150)
                    if (!kotlin.coroutines.coroutineContext.isActive) return@LaunchedEffect
                }
                val script = buildString {
                    if (plan.search) {
                        append("searchWord(${org.json.JSONObject.quote(searchQuery)});")
                    }
                    repeat(plan.nextSteps) { append("nextSearchMatch();") }
                    repeat(plan.prevSteps) { append("prevSearchMatch();") }
                }
                webView.evaluateJavascript(script, null)
            }
            // Always advance the ledger — including the no-op case, so a trigger counter
            // reset (file change) is absorbed instead of replayed as steps later.
            issuedSearch = plan.issued
        }
    }

    // Browser-style zoom (25% - 500%) applied live, mirroring the DOCX setZoom pipeline.
    LaunchedEffect(webViewRef, isPageReady, zoomPercent) {
        val webView = webViewRef
        if (webView != null && isPageReady) {
            webView.evaluateJavascript("setViewerZoom($zoomPercent);", null)
        }
    }

    // Outline / go-to-line jumps. Prefer exact heading anchoring when available.
    LaunchedEffect(webViewRef, isPageReady, scrollRequest) {
        val webView = webViewRef
        if (webView != null && isPageReady && scrollRequest != null) {
            val script = if (scrollRequest.headingIndex != null) {
                "var __ok = scrollToHeadingIndex(${scrollRequest.headingIndex}); if (!__ok) { scrollToLine(${scrollRequest.lineNumber}); }"
            } else {
                "scrollToLine(${scrollRequest.lineNumber});"
            }
            webView.evaluateJavascript(script, null)
            onScrollRequestHandled()
        }
    }

    if (html == null || isLoading) {
        Box(
            modifier = modifier.fillMaxSize().background(Color(backgroundColor)),
            contentAlignment = Alignment.Center
        ) {
            com.example.ui.component.PremiumLoadingIndicator(text = loadingMessage)
        }
    }
}

class WebAppInterface(
    private val context: android.content.Context,
    private val onSearchResultsChanged: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
    private val onTapRequested: () -> Unit = {}
) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Code", text))
        mainHandler.post {
            android.widget.Toast.makeText(context, "Code block copied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    @android.webkit.JavascriptInterface
    fun onSearchResultCount(count: Int, currentIndex: Int, truncated: Boolean) {
        mainHandler.post { onSearchResultsChanged(count, currentIndex, truncated) }
    }

    @android.webkit.JavascriptInterface
    fun onDocumentTap() {
        mainHandler.post { onTapRequested() }
    }
}
