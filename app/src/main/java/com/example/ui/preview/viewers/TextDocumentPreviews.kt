package com.example.ui.preview.viewers

import com.example.ui.screens.buildPlainTextPreviewHtml

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.component.DocumentOutlineBottomSheet
import com.example.ui.component.AppearanceBottomSheet
import com.example.ui.component.FilePropertiesBottomSheet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.io.TextLoader
import com.example.core.io.TextWindowReader
import com.example.core.text.TextFileSearchEngine
import com.example.core.text.TextFileSearchMatch
import com.example.ui.screens.DocumentPreviewScript
import com.example.ui.screens.InViewerSearchCommandPolicy
import com.example.data.RecentFileEntity
import com.example.services.FileManager
import com.example.ui.component.ClaudeAppBar
import com.example.ui.component.ClaudeCard
import com.example.ui.component.FileIcon
import com.example.ui.component.formatFileSize
import com.example.ui.component.formatElapsedTime
import com.example.viewmodel.MainViewModel
import java.io.File
import coil.compose.AsyncImage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FormatIndentIncrease
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.component.DocumentOutlineParser
import com.example.ui.component.ViewerSyntaxTheme
import com.example.ui.component.ViewerAppearancePrefs
import com.example.core.format.DocxZoomPolicy
import com.example.core.format.PdfRenderBudgetPolicy
import com.example.core.format.PinchGestureGate
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import android.media.MediaPlayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Slider
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode

@Composable
fun PropertyItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}


// Helper: Fast and robust code syntax highlighter
@Composable
fun MarkdownPreview(
    markdownText: String,
    fileEntity: RecentFileEntity,
    fontSizeSetting: String,
    isNightMode: Boolean,
    searchQuery: String = "",
    searchTriggerNext: Int = 0,
    searchTriggerPrev: Int = 0,
    onSearchResultCount: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
    onSingleTap: () -> Unit = {},
    syntaxTheme: ViewerSyntaxTheme = ViewerSyntaxTheme.AUTO,
    viewerFontSizeSp: Int = 16,
    zoomPercent: Int = 100,
    scrollRequest: ViewerScrollRequest? = null,
    onScrollRequestHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var generatedHtml by remember(markdownText, isNightMode, viewerFontSizeSp, syntaxTheme) { mutableStateOf<String?>(null) }

    LaunchedEffect(markdownText, isNightMode, viewerFontSizeSp, syntaxTheme) {
        val htmlContent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val parser = org.intellij.markdown.parser.MarkdownParser(org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor())
            val parsedTree = parser.buildMarkdownTreeFromString(markdownText)
            val rawHtml = com.example.core.text.HtmlSanitizer.sanitize(
                org.intellij.markdown.html.HtmlGenerator(markdownText, parsedTree, org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor()).generateHtml(),
                keepImages = true
            )

            // Viewer Appearance drives the base font size; falls back to the settings mapping.
            val fontSize = "${viewerFontSizeSp.coerceIn(11, 22)}px"

            // Claude Theme Colors (EXACT from File Claw - MD Preview Theme Fix Prompt)
            // A chosen syntax theme overrides the base canvas/ink/accent tokens; AUTO keeps
            // the light/dark palette derived from the app theme.
            val useThemeOverride = !syntaxTheme.isAuto
            val bgColor = if (useThemeOverride) syntaxTheme.hexBg else if (isNightMode) "#151514" else "#faf9f5" // canvas
            val textPrimary = if (useThemeOverride) syntaxTheme.hexText else if (isNightMode) "#f5f5f3" else "#141413" // ink
            val textBody = if (useThemeOverride) syntaxTheme.hexText else if (isNightMode) "#d1cfc7" else "#3d3d3a" // body
            val textBodyStrong = if (useThemeOverride) syntaxTheme.hexText else if (isNightMode) "#faf9f5" else "#252523" // body_strong
            val textSecondary = if (useThemeOverride) syntaxTheme.hexText else if (isNightMode) "#a39f93" else "#6c6a64" // muted
            val textSecondarySoft = if (useThemeOverride) syntaxTheme.hexText else if (isNightMode) "#8e8779" else "#8e8b82" // muted_soft
            val accent = if (useThemeOverride) syntaxTheme.hexAccent else if (isNightMode) "#e08567" else "#cc785c" // primary (coral)
            val accentActive = if (useThemeOverride) syntaxTheme.hexAccent else if (isNightMode) "#f09a7d" else "#a9583e" // primary_active
            val border = if (isNightMode) "#2d2b28" else "#e6dfd8" // hairline
            val borderSoft = if (isNightMode) "#211f1d" else "#ebe6df" // hairline_soft
            val cardBg = if (isNightMode) "#2a2723" else "#efe9de" // surface_card
            val quoteBg = if (isNightMode) "#1c1a18" else "#f5f0e8" // surface_soft
            val inlineCodeBg = if (isNightMode) "#2d2823" else "#e8e0d2" // surface_cream_strong
            val codeBlockOuterBg = if (isNightMode) "#0d0d0c" else "#181715" // surface_dark
            val codeBlockInnerBg = if (isNightMode) "#161513" else "#1f1e1b" // surface_dark_soft
            val codeBlockHeaderBg = if (isNightMode) "#1d1b19" else "#252320" // surface_dark_elevated
            val codeBlockText = if (isNightMode) "#f5f5f3" else "#faf9f5" // on_dark
            val codeBlockButtons = if (isNightMode) "#a09d96" else "#8e8b82" // on_dark_soft

            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=0.25, maximum-scale=5.0, user-scalable=yes">
                <style>
                    :root {
                        --bg-color: $bgColor;
                        --text-primary: $textPrimary;
                        --text-body: $textBody;
                        --text-body-strong: $textBodyStrong;
                        --text-secondary: $textSecondary;
                        --text-secondary-soft: $textSecondarySoft;
                        --accent: $accent;
                        --accent-active: $accentActive;
                        --border: $border;
                        --border-soft: $borderSoft;
                        --card-bg: $cardBg;
                        --quote-bg: $quoteBg;
                        --inline-code-bg: $inlineCodeBg;
                        --code-block-outer: $codeBlockOuterBg;
                        --code-block-inner: $codeBlockInnerBg;
                        --code-block-header: $codeBlockHeaderBg;
                        --code-text: $codeBlockText;
                        --code-buttons: $codeBlockButtons;
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background-color: var(--bg-color);
                        color: var(--text-body);
                        font-size: $fontSize;
                        line-height: 1.55;
                        padding: 16px;
                        margin: 0;
                        word-wrap: break-word;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        color: var(--text-primary);
                        margin-top: 24px;
                        margin-bottom: 16px;
                        font-weight: 700;
                    }
                    h1 {
                        font-size: 28px;
                        border-bottom: 2px solid var(--border);
                        padding-bottom: 8px;
                    }
                    h2 { font-size: 22px; }
                    h3 { font-size: 18px; font-weight: 600; }
                    p {
                        margin-top: 0;
                        margin-bottom: 16px;
                    }
                    li {
                        margin: 8px 0;
                    }
                    ul, ol {
                        padding-left: 24px;
                    }
                    a {
                        color: var(--accent);
                        text-decoration: none;
                    }
                    a:hover {
                        color: var(--accent-active);
                        text-decoration: underline;
                    }
                    code {
                        font-family: "JetBrains Mono", ui-monospace, Consolas, Monaco, monospace;
                        background-color: var(--inline-code-bg);
                        color: var(--accent);
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-size: 14px;
                    }
                    pre {
                        background-color: transparent;
                        margin: 0;
                        padding: 0;
                        overflow: visible;
                    }
                    .code-block-container {
                        background-color: var(--code-block-outer);
                        border-radius: 12px;
                        margin-bottom: 16px;
                        overflow: hidden;
                        border: 1px solid var(--border);
                    }
                    .code-header {
                        background-color: var(--code-block-header);
                        padding: 8px 16px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .code-lang {
                        color: var(--text-secondary-soft);
                        font-size: 12px;
                        font-family: monospace;
                        text-transform: uppercase;
                    }
                    .copy-btn {
                        background: transparent;
                        border: 1px solid var(--code-buttons);
                        color: var(--text-secondary-soft);
                        padding: 4px 12px;
                        border-radius: 6px;
                        font-size: 12px;
                        cursor: pointer;
                        transition: background 0.2s;
                    }
                    .copy-btn:hover {
                        background: rgba(255, 255, 255, 0.1);
                    }
                    .code-content {
                        background-color: var(--code-block-inner);
                        padding: 16px;
                        overflow-x: auto;
                    }
                    .code-content code {
                        background-color: transparent;
                        color: var(--code-text);
                        padding: 0;
                        font-size: 14px;
                        line-height: 1.6;
                    }
                    blockquote {
                        margin: 0 0 16px 0;
                        padding: 12px 20px;
                        color: var(--text-body);
                        border-left: 4px solid var(--accent);
                        background-color: var(--quote-bg);
                        border-radius: 0 8px 8px 0;
                    }
                    table {
                        border-collapse: collapse;
                        width: 100%;
                        margin-bottom: 16px;
                        display: block;
                        overflow-x: auto;
                        border: 1px solid var(--border);
                    }
                    th, td {
                        border: 1px solid var(--border);
                        padding: 12px;
                    }
                    th {
                        background-color: var(--quote-bg);
                        font-weight: 600;
                        color: var(--text-body-strong);
                    }
                    tr:nth-child(even) {
                        background-color: var(--bg-color);
                    }
                    tr:nth-child(odd) {
                        background-color: var(--bg-color);
                    }
                    hr {
                        border: 0;
                        border-top: 2px solid var(--border);
                        margin: 24px 0;
                    }
                    img {
                        max-width: 100%;
                        border-radius: 8px;
                    }
                    .search-highlight { background: rgba(204, 120, 92, 0.32); color: inherit; border-radius: 2px; }
                    .search-highlight-active { background: #cc785c; color: #ffffff; outline: 2px solid rgba(232, 165, 90, 0.9); }
                    .line-anchor-flash { background: rgba(204, 120, 92, 0.32); color: inherit; border-radius: 2px; outline: 2px solid #cc785c; transition: background 0.4s ease; }
                    @media print { .code-header, .copy-btn { display: none !important; } .code-block-container { break-inside: avoid; } }
                </style>
                $DocumentPreviewScript
            </head>
            <body>
                <main id="document-content">$rawHtml</main>
            </body>
            </html>
            """.trimIndent()
        }
        generatedHtml = htmlContent
    }

    SearchableDocumentWebView(
        html = generatedHtml,
        backgroundColor = if (syntaxTheme.isAuto) {
            if (isNightMode) 0xFF151514.toInt() else 0xFFfaf9f5.toInt()
        } else {
            runCatching {
                android.graphics.Color.parseColor(syntaxTheme.hexBg)
            }.getOrDefault(if (isNightMode) 0xFF151514.toInt() else 0xFFfaf9f5.toInt())
        },
        searchQuery = searchQuery,
        searchTriggerNext = searchTriggerNext,
        searchTriggerPrev = searchTriggerPrev,
        onSearchResultCount = onSearchResultCount,
        onSingleTap = onSingleTap,
        zoomPercent = zoomPercent,
        scrollRequest = scrollRequest,
        onScrollRequestHandled = onScrollRequestHandled,
        loadingMessage = "Preparing Document...",
        modifier = modifier
    )
}

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
private fun SearchableDocumentWebView(
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
