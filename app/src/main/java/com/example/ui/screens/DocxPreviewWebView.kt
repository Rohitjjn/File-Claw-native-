package com.example.ui.screens

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
fun DocxPreviewWebView(
    fileEntity: RecentFileEntity,
    isNightMode: Boolean,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onPageChanged: (Int, Int) -> Unit = { _, _ -> },
    scrollToPage: Int? = null,
    onScrollProgressHandled: () -> Unit = {},
    searchQuery: String = "",
    searchTriggerNext: Int = 0,
    searchTriggerPrev: Int = 0,
    onSearchResultCount: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
    zoomFactor: Float = 1.0f,
    // Loop 12 (Bug A): true while a pinch gesture is physically in progress. While it
    // is set, zoom updates go to the cheap compositor-only preview path instead of
    // re-laying-out the whole document per frame; the settle transition commits once.
    pinchActive: Boolean = false,
    onWebViewCreated: (android.webkit.WebView?) -> Unit = {},
    onDocumentReady: () -> Unit = {},
    onSingleTap: () -> Unit = {},
    onZoomChanged: (Float) -> Unit = {},
    onPinchZoom: (Float) -> Unit = {},
    onPinchSettled: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onRendererFallback: (Boolean) -> Unit = {}
) {
    var isWebViewLoading by remember(fileEntity.path) { mutableStateOf(true) }
    val lifecycle = remember(fileEntity.path) { com.example.core.format.DocxPreviewLifecycle() }
    var renderGeneration by remember(fileEntity.path) { mutableLongStateOf(lifecycle.begin()) }
    var lifecycleSnapshot by remember(fileEntity.path) { mutableStateOf(lifecycle.snapshot) }
    fun advanceLifecycle(
        stage: com.example.core.format.DocxPreviewStage,
        generation: Long = renderGeneration
    ) {
        if (lifecycle.advance(generation, stage)) {
            lifecycleSnapshot = lifecycle.snapshot
            com.example.utils.DocxPreviewDiagnostics.log(
                "pipeline_stage", "stage" to stage.name, "generation" to generation
            )
        }
    }
    fun failLifecycle(code: String, generation: Long = renderGeneration) {
        if (lifecycle.fail(
                generation,
                com.example.core.format.classifyDocxPreviewFailure(code),
                code
            )
        ) {
            lifecycleSnapshot = lifecycle.snapshot
            com.example.utils.DocxPreviewDiagnostics.log(
                "pipeline_failed", "failure" to lifecycleSnapshot.failure?.name,
                "generation" to generation, "detailCode" to lifecycleSnapshot.detailCode
            )
        }
    }
    var webViewRef by remember(fileEntity.path) { mutableStateOf<android.webkit.WebView?>(null) }
    var pureKotlinFallback by remember(fileEntity.path) { mutableStateOf<com.example.core.format.DocxExtractor.ExtractedDocx?>(null) }
    // Loop 14: the plain-text view is an honest RECOVERY state, never a normal
    // preview. The renderer failure reason is surfaced verbatim (no document
    // content, no sensitive data) and Retry rebuilds the WebView from scratch.
    var fallbackReason by remember(fileEntity.path) { mutableStateOf<String?>(null) }
    var docxReloadKey by remember(fileEntity.path) { mutableIntStateOf(0) }
    // Ledger of what the renderer's search engine has already been told (Loop 12, Bug
    // B): navigation triggers now issue ONLY next/prev deltas; re-issuing searchWord()
    // per arrow press used to reset the match index to 0 so the counter stuck at 2/N.
    var issuedSearch by remember(fileEntity.path) { mutableStateOf(InViewerSearchCommandPolicy.Issued()) }
    fun retryDocxRender() {
        renderGeneration = lifecycle.begin()
        lifecycleSnapshot = lifecycle.snapshot
        pureKotlinFallback = null
        fallbackReason = null
        isWebViewLoading = true
        issuedSearch = InViewerSearchCommandPolicy.Issued()
        docxReloadKey++
    }
    // Display-frame throttle (~30/s) for the pinch preview path (Bug E: coalescing).
    var lastDisplayDispatchNanos by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val context = LocalContext.current
    val recoveryScope = rememberCoroutineScope()
    val latestSearchResultCallback = androidx.compose.runtime.rememberUpdatedState(onSearchResultCount)
    val latestPageChangedCallback = androidx.compose.runtime.rememberUpdatedState(onPageChanged)
    val latestDocumentReadyCallback = androidx.compose.runtime.rememberUpdatedState(onDocumentReady)
    val latestWebViewCreatedCallback = androidx.compose.runtime.rememberUpdatedState(onWebViewCreated)
    val latestSingleTapCallback = androidx.compose.runtime.rememberUpdatedState(onSingleTap)
    val latestZoomChangedCallback = androidx.compose.runtime.rememberUpdatedState(onZoomChanged)
    val latestPinchZoomCallback = androidx.compose.runtime.rememberUpdatedState(onPinchZoom)
    val latestPinchSettledCallback = androidx.compose.runtime.rememberUpdatedState(onPinchSettled)
    val latestDoubleTapCallback = androidx.compose.runtime.rememberUpdatedState(onDoubleTap)
    val latestRendererFallbackCallback = androidx.compose.runtime.rememberUpdatedState(onRendererFallback)
    fun recoverFromRenderFailure(
        sourceWebView: android.webkit.WebView,
        reason: String,
        generation: Long = renderGeneration
    ) {
        if (webViewRef !== sourceWebView || generation != renderGeneration) return
        failLifecycle(reason, generation)
        recoveryScope.launch {
            val recovered = withContext(Dispatchers.IO) {
                runCatching { com.example.core.format.DocxExtractor.extractText(File(fileEntity.path)) }
                    .getOrElse {
                        com.example.core.format.DocxExtractor.ExtractedDocx(
                            fileEntity.name, emptyList(), "", 0, 0
                        )
                    }
            }
            // Ignore late callbacks from a WebView that was replaced by retry/navigation.
            if (webViewRef !== sourceWebView || generation != renderGeneration) return@launch
            fallbackReason = reason.take(180)
            pureKotlinFallback = recovered
            isWebViewLoading = false
            latestRendererFallbackCallback.value(true)
        }
    }
    // Fresh values for the WebViewClient closures created once in the factory: reading
    // them at callback time prevents a stale initial zoom/theme being re-applied after
    // the document finishes loading (Loop 10).
    val latestZoomForPageLoad = androidx.compose.runtime.rememberUpdatedState(zoomFactor)
    val latestNightModeForPageLoad = androidx.compose.runtime.rememberUpdatedState(isNightMode)

    // Loop 12 (Bug B): query changes search exactly once; arrow presses issue only
    // next/prev deltas, so all N matches stay reachable in both directions.
    LaunchedEffect(webViewRef, isWebViewLoading, searchQuery, searchTriggerNext, searchTriggerPrev, pureKotlinFallback) {
        val webView = webViewRef
        if (webView != null && !isWebViewLoading && pureKotlinFallback == null) {
            val plan = InViewerSearchCommandPolicy.plan(issuedSearch, searchQuery, searchTriggerNext, searchTriggerPrev)
            if (!plan.isEmpty) {
                if (plan.search && issuedSearch.query != null) {
                    // Debounce rapid typing before re-highlighting the whole DOM.
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
            // Always advance the ledger — including no-op plans, so a trigger counter
            // reset (file change / find closed) is absorbed instead of replayed.
            issuedSearch = plan.issued
        }
    }
    LaunchedEffect(webViewRef, isWebViewLoading, isNightMode, pureKotlinFallback) {
        if (webViewRef != null && !isWebViewLoading && pureKotlinFallback == null) {
            webViewRef?.evaluateJavascript("setDarkMode($isNightMode);", null)
        }
    }
    // Loop 12 (Bug A): two-phase zoom pipeline.
    //  - pinchActive == true  -> setZoomDisplay(): compositor-only transform preview,
    //    throttled to ~30 dispatches/s so a large DOCX stops re-laying-out per frame.
    //  - pinchActive == false -> setZoom(): the authoritative commit (toolbar +/-,
    //    percentage dialog, page load, and the settle transition after a pinch).
    LaunchedEffect(webViewRef, isWebViewLoading, zoomFactor, pinchActive, pureKotlinFallback) {
        val webView = webViewRef
        if (webView != null && !isWebViewLoading && pureKotlinFallback == null) {
            if (pinchActive) {
                val now = android.os.SystemClock.elapsedRealtimeNanos()
                if (now - lastDisplayDispatchNanos < 33_000_000L) return@LaunchedEffect
                lastDisplayDispatchNanos = now
                webView.evaluateJavascript("setZoomDisplay($zoomFactor);", null)
            } else {
                lastDisplayDispatchNanos = 0L
                webView.evaluateJavascript("setZoom($zoomFactor);", null)
            }
        }
    }
    LaunchedEffect(webViewRef, isWebViewLoading, scrollToPage, pureKotlinFallback) {
        if (webViewRef != null && !isWebViewLoading && pureKotlinFallback == null && scrollToPage != null) {
            webViewRef?.evaluateJavascript("scrollToPage(${scrollToPage + 1});", null)
            onScrollProgressHandled()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5))) {
        if (pureKotlinFallback != null) {
            val extracted = pureKotlinFallback!!
            Column(modifier = Modifier.fillMaxSize()) {
                // Loop 14: labeled recovery/error state — the extracted text must never
                // masquerade as a successful formatted preview (the old "Clean Document
                // View" banner looked like a feature, not a failure).
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .5f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = com.example.ui.screens.DocxTextRecovery.bannerHeadline(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        com.example.ui.screens.DocxTextRecovery.displayReason(fallbackReason)?.let { reason ->
                            Text(
                                text = reason,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = .75f),
                                maxLines = 2
                            )
                        }
                        com.example.ui.screens.DocxTextRecovery
                            .statsLabel(extracted.wordCount, extracted.paragraphCount)?.let { stats ->
                                Text(
                                    text = stats,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = .75f)
                                )
                            }
                        if (extracted.truncated) {
                            Text(
                                text = "Recovered text was truncated at the safety limit.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = .75f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            // Retry rebuilds the WebView and re-runs the real renderer;
                            // the source document is never modified.
                            TextButton(
                                onClick = { retryDocxRender() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) { Text("Retry formatted view", fontSize = 12.sp) }
                        }
                    }
                }
                if (extracted.paragraphs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No text content could be recovered from this DOCX file.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                    }
                } else {
                    PlainTextPreview(
                        content = extracted.paragraphs.joinToString("\n\n"),
                        isDarkMode = isNightMode,
                        fontSizeSetting = "Default",
                        searchQuery = searchQuery,
                        searchTriggerNext = searchTriggerNext,
                        searchTriggerPrev = searchTriggerPrev,
                        onSearchResultCount = onSearchResultCount,
                        onSingleTap = onSingleTap,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            androidx.compose.runtime.key(fileEntity.path, docxReloadKey) {
                AndroidView(
                    factory = { viewContext ->
                        val ownerGeneration = renderGeneration
                        // Loop 12 (Bug E): the editor page is now served through the
                        // WebViewAssetLoader (same mechanism as the PDF reader) so the
                        // DOCX bytes can be streamed to docx-preview as an ArrayBuffer;
                        // no eager Base64 copy is retained in ViewModel state.
                        //
                        // Loop 14 REGRESSION FIX: the editor was previously registered under
                        // "/editor/" and loaded at /editor/docx_editor.html. WebViewAssetLoader
                        // hands each handler the path with the registered prefix STRIPPED, so
                        // AssetsPathHandler tried to open the asset "docx_editor.html" — which
                        // lives at "editor/docx_editor.html" — and every DOCX open failed the
                        // main-frame load, silently landing in the plain-text recovery view.
                        // Registering "/assets/" (the documented pattern, same as the PDF
                        // reader) makes /assets/editor/docx_editor.html resolve to the real
                        // asset editor/docx_editor.html. The /docx-src/ stream is unaffected:
                        // its custom handler receives the stripped suffix "document.docx",
                        // which is exactly what it matches on.
                        val docxAssetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context))
                            .addPathHandler("/docx-src/", object : androidx.webkit.WebViewAssetLoader.PathHandler {
                                override fun handle(path: String): android.webkit.WebResourceResponse? {
                                    if (path != "document.docx") return null
                                    val documentFile = File(fileEntity.path)
                                    val assetUrl = "https://appassets.androidplatform.net/docx-src/$path"
                                    if (!documentFile.isFile) {
                                        com.example.utils.DocxPreviewDiagnostics.log(
                                            "asset_request", "assetRequestUrl" to assetUrl,
                                            "fileName" to fileEntity.name, "responseStatus" to 404,
                                            "fileSizeBytes" to 0L
                                        )
                                        return null
                                    }
                                    val sizeBytes = documentFile.length()
                                    com.example.utils.DocxPreviewDiagnostics.log(
                                        "asset_request", "assetRequestUrl" to assetUrl,
                                        "fileName" to fileEntity.name, "responseStatus" to 200,
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
                                            com.example.utils.DocxCountingInputStream(
                                                java.io.FileInputStream(documentFile), documentFile.name, sizeBytes
                                            )
                                        )
                                    }.onFailure { error ->
                                        com.example.utils.DocxPreviewDiagnostics.log(
                                            "asset_response_error", "fileName" to documentFile.name,
                                            "errorKind" to (error::class.java.simpleName)
                                        )
                                    }.getOrNull()
                                }
                            })
                            .build()
                        android.webkit.WebView(viewContext).apply {
                            val ownerWebView = this
                            val createdAtElapsedMs = android.os.SystemClock.elapsedRealtime()
                            com.example.utils.DocxPreviewDiagnostics.log(
                                "webview_created", "fileName" to fileEntity.name,
                                "fileSizeBytes" to File(fileEntity.path).length(),
                                "url" to "https://appassets.androidplatform.net/assets/editor/docx_editor.html",
                                "widthPx" to width, "heightPx" to height
                            )
                            webViewRef = this
                            latestWebViewCreatedCallback.value(this)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                // The asset loader serves both the editor page and the
                                // document bytes; no raw file scheme access is needed.
                                allowFileAccess = false
                                allowContentAccess = false
                                @Suppress("DEPRECATION")
                                allowFileAccessFromFileURLs = false
                                @Suppress("DEPRECATION")
                                allowUniversalAccessFromFileURLs = false
                                builtInZoomControls = false
                                displayZoomControls = false
                                setSupportZoom(false)
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            setBackgroundColor(if (isNightMode) 0xFF151514.toInt() else 0xFFfaf9f5.toInt())
                            com.example.utils.DocxPreviewDiagnostics.log(
                                "webview_settings", "mode" to "normal_preview",
                                "javaScriptEnabled" to settings.javaScriptEnabled,
                                "domStorageEnabled" to settings.domStorageEnabled,
                                "useWideViewPort" to settings.useWideViewPort,
                                "loadWithOverviewMode" to settings.loadWithOverviewMode,
                                "allowFileAccess" to settings.allowFileAccess,
                                "allowContentAccess" to settings.allowContentAccess
                            )

                            val pageChangedCallback = latestPageChangedCallback
                            addJavascriptInterface(object : Any() {
                                private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

                                private fun postForCurrentWebView(action: () -> Unit) {
                                    mainHandler.post {
                                        if (webViewRef === ownerWebView) action()
                                    }
                                }

                                @android.webkit.JavascriptInterface
                                fun onDocxDiagnostic(payload: String) {
                                    com.example.utils.DocxPreviewDiagnostics.logJavascriptMetrics(payload)
                                    postForCurrentWebView {
                                        val stage = runCatching {
                                            org.json.JSONObject(payload).optString("event")
                                        }.getOrDefault("")
                                        when (stage) {
                                            "fetch_complete" -> advanceLifecycle(com.example.core.format.DocxPreviewStage.VALIDATING, ownerGeneration)
                                            "render_start" -> advanceLifecycle(com.example.core.format.DocxPreviewStage.RENDERING, ownerGeneration)
                                            "render_layout" -> advanceLifecycle(com.example.core.format.DocxPreviewStage.LAYOUT, ownerGeneration)
                                            "engine_unavailable" -> failLifecycle("DOCX_RENDERER_UNAVAILABLE", ownerGeneration)
                                            "load_failed" -> {
                                                val errorCode = runCatching {
                                                    org.json.JSONObject(payload).optString("errorKind", "DOCX_LOAD_FAILED")
                                                }.getOrDefault("DOCX_LOAD_FAILED")
                                                failLifecycle(errorCode, ownerGeneration)
                                            }
                                            "render_failed" -> {
                                                val errorCode = runCatching {
                                                    org.json.JSONObject(payload).optString("errorKind", "DOCX_RENDER_FAILED")
                                                }.getOrDefault("DOCX_RENDER_FAILED")
                                                failLifecycle(errorCode, ownerGeneration)
                                            }
                                        }
                                    }
                                }

                                @android.webkit.JavascriptInterface
                                fun onDocumentLoaded(totalPages: Int) {
                                    postForCurrentWebView {
                                        if (totalPages <= 0) {
                                            com.example.utils.DocxPreviewDiagnostics.log(
                                                "android_ready_rejected", "reason" to "zero_pages"
                                            )
                                            recoverFromRenderFailure(ownerWebView, "The renderer returned no visible pages.", ownerGeneration)
                                        } else {
                                            com.example.utils.DocxPreviewDiagnostics.log(
                                                "android_document_loaded", "pages" to totalPages,
                                                "callbackElapsedMs" to (android.os.SystemClock.elapsedRealtime() - createdAtElapsedMs),
                                                "webViewWidthPx" to ownerWebView.width,
                                                "webViewHeightPx" to ownerWebView.height,
                                                "documentReady" to true
                                            )
                                            if (lifecycle.markReady(ownerGeneration, totalPages, visibleLayout = true)) {
                                                lifecycleSnapshot = lifecycle.snapshot
                                                com.example.utils.DocxPreviewDiagnostics.log(
                                                    "pipeline_stage", "stage" to "READY",
                                                    "generation" to ownerGeneration,
                                                    "estimatedPages" to totalPages
                                                )
                                            }
                                            isWebViewLoading = false
                                            latestDocumentReadyCallback.value()
                                            pageChangedCallback.value(1, totalPages)
                                        }
                                    }
                                }

                                @android.webkit.JavascriptInterface
                                fun onPageChanged(currentPage: Int, totalPages: Int) {
                                    postForCurrentWebView { pageChangedCallback.value(currentPage, totalPages) }
                                }

                                @android.webkit.JavascriptInterface
                                fun onSearchResultCount(count: Int, currentIndex: Int, truncated: Boolean) {
                                    postForCurrentWebView { latestSearchResultCallback.value(count, currentIndex, truncated) }
                                }

                                @android.webkit.JavascriptInterface
                                fun onDocumentTap() {
                                    postForCurrentWebView { latestSingleTapCallback.value() }
                                }

                                @android.webkit.JavascriptInterface
                                fun onDocumentDoubleTap() {
                                    postForCurrentWebView { latestDoubleTapCallback.value() }
                                }

                                @android.webkit.JavascriptInterface
                                fun onZoomChanged(newZoom: Double) {
                                    postForCurrentWebView { latestZoomChangedCallback.value(newZoom.toFloat()) }
                                }

                                @android.webkit.JavascriptInterface
                                fun onDocumentError(error: String) {
                                    postForCurrentWebView {
                                        android.util.Log.w("WebViewDocx", "Switching to bounded text recovery: $error")
                                        recoverFromRenderFailure(ownerWebView, error, ownerGeneration)
                                    }
                                }

                                @android.webkit.JavascriptInterface
                                fun exitWithoutSaving() {
                                    postForCurrentWebView { viewModel.closeCurrentFile() }
                                }
                            }, "Android")

                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageStarted(
                                    view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?
                                ) {
                                    com.example.utils.DocxPreviewDiagnostics.log(
                                        "webview_lifecycle", "lifecycle" to "page_started",
                                        "url" to (url ?: ""), "isCurrent" to (view === ownerWebView)
                                    )
                                }

                                override fun onPageCommitVisible(view: android.webkit.WebView?, url: String?) {
                                    com.example.utils.DocxPreviewDiagnostics.log(
                                        "webview_lifecycle", "lifecycle" to "page_commit_visible",
                                        "url" to (url ?: ""), "isCurrent" to (view === ownerWebView)
                                    )
                                }

                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    com.example.utils.DocxPreviewDiagnostics.log(
                                        "webview_lifecycle", "lifecycle" to "page_finished",
                                        "url" to (url ?: ""), "isCurrent" to (view === ownerWebView)
                                    )
                                    if (view !== ownerWebView || webViewRef !== ownerWebView) return
                                    // Apply the CURRENT zoom factor (not a stale factory-time
                                    // capture) BEFORE loadDocx so the document never flashes at
                                    // 100% before settling on the preferred zoom. Bytes stream
                                    // from the local asset handler; text extraction is the fallback.
                                    val freshZoom = latestZoomForPageLoad.value
                                    val freshNightMode = latestNightModeForPageLoad.value
                                    view?.evaluateJavascript(
                                        "setZoom($freshZoom); loadDocx($freshNightMode);",
                                        null
                                    )
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): Boolean {
                                    val uri = request?.url ?: return true
                                    val localEditorAsset = uri.scheme == "https" &&
                                        uri.host == "appassets.androidplatform.net" &&
                                        (uri.path?.startsWith("/assets/editor/") == true || uri.path?.startsWith("/docx-src/") == true)
                                    return !localEditorAsset
                                }

                                override fun shouldInterceptRequest(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?
                                ): android.webkit.WebResourceResponse? {
                                    val uri = request?.url ?: return null
                                    if (uri.path?.startsWith("/docx-src/") == true) {
                                        com.example.utils.DocxPreviewDiagnostics.log(
                                            "asset_intercept", "assetRequestUrl" to uri.toString(),
                                            "lifecycle" to "shouldInterceptRequest"
                                        )
                                    }
                                    return docxAssetLoader.shouldInterceptRequest(uri)
                                }

                                override fun onReceivedError(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    error: android.webkit.WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    com.example.utils.DocxPreviewDiagnostics.log(
                                        "webview_resource_error", "url" to (request?.url?.toString() ?: ""),
                                        "errorCode" to (error?.errorCode ?: 0),
                                        "isMainFrame" to (request?.isForMainFrame == true),
                                        "descriptionKind" to "WebResourceError"
                                    )
                                    if (request?.isForMainFrame == true) {
                                        view?.let { failedWebView ->
                                            recoverFromRenderFailure(
                                                failedWebView,
                                                error?.description?.toString() ?: "The preview page could not be loaded.",
                                                ownerGeneration
                                            )
                                        }
                                    }
                                }

                                // Loop 12 (Bug C/E): a WebView renderer crash must degrade
                                // to the text-only fallback instead of killing the app or
                                // leaving a dead blank surface. Returning true tells Android
                                // this app handled the loss.
                                override fun onRenderProcessGone(
                                    view: android.webkit.WebView,
                                    detail: android.webkit.RenderProcessGoneDetail
                                ): Boolean {
                                    com.example.utils.DocxPreviewDiagnostics.log(
                                        "renderer_process_gone", "crashed" to detail.didCrash(),
                                        "webViewWidthPx" to view.width, "webViewHeightPx" to view.height
                                    )
                                    recoverFromRenderFailure(view, "The document renderer stopped unexpectedly.", ownerGeneration)
                                    return true
                                }

                                override fun onReceivedHttpError(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                    errorResponse: android.webkit.WebResourceResponse?
                                ) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    com.example.utils.DocxPreviewDiagnostics.log(
                                        "webview_http_error", "url" to (request?.url?.toString() ?: ""),
                                        "responseStatus" to (errorResponse?.statusCode ?: 0)
                                    )
                                }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                    val level = consoleMessage?.messageLevel()
                                    if (level == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                                        val source = consoleMessage.sourceId()?.substringAfterLast('/')?.take(100).orEmpty()
                                        android.util.Log.e(
                                            com.example.utils.DocxPreviewDiagnostics.TAG,
                                            "javascript_console_error source=$source line=${consoleMessage.lineNumber()}"
                                        )
                                    }
                                    return true
                                }
                            }
                            loadUrl("https://appassets.androidplatform.net/assets/editor/docx_editor.html")
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                        latestWebViewCreatedCallback.value(webView)
                        webView.setBackgroundColor(if (isNightMode) 0xFF151514.toInt() else 0xFFfaf9f5.toInt())
                    },
                    // Loop 12 (Bug E): destroying the WebView when its node leaves
                    // composition prevents stale instances (and their render processes)
                    // from retaining the file context after a file switch.
                    onRelease = { webView ->
                        if (webViewRef === webView) webViewRef = null
                        webView.stopLoading()
                        webView.removeJavascriptInterface("Android")
                        webView.destroy()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isWebViewLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5)),
                    contentAlignment = Alignment.Center
                ) {
                    val progressLabel = when (lifecycleSnapshot.stage) {
                        com.example.core.format.DocxPreviewStage.LOADING -> "Loading document…"
                        com.example.core.format.DocxPreviewStage.VALIDATING -> "Checking document…"
                        com.example.core.format.DocxPreviewStage.RENDERING -> "Rendering document…"
                        com.example.core.format.DocxPreviewStage.LAYOUT -> "Preparing visible pages…"
                        else -> "Opening document…"
                    }
                    com.example.ui.component.PremiumLoadingIndicator(text = progressLabel)
                }
            }
        }
    }
}
