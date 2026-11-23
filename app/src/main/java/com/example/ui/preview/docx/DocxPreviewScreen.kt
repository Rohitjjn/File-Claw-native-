package com.example.ui.preview.docx

import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.format.DocxExtractor
import com.example.data.RecentFileEntity
import com.example.ui.theme.ClaudeAlabaster
import com.example.ui.theme.ClaudeCharcoal
import com.example.ui.theme.ClaudeDarkBorder
import com.example.ui.theme.ClaudeDarkSurface
import com.example.ui.theme.ClaudeDarkText
import com.example.ui.theme.ClaudeOnyx
import com.example.ui.theme.ClaudeSandBorder
import com.example.ui.theme.ClaudeWhite
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Color.toCssHex(): String = "#" + toArgb().toUInt().toString(16).takeLast(6)

/** DOCX-only Compose surface. Shared toolbar actions remain coordinated by FilePreviewScreen. */
@Composable
fun DocxPreviewScreen(
    fileEntity: RecentFileEntity,
    viewerState: DocxViewerState,
    isNightMode: Boolean,
    modifier: Modifier = Modifier,
    onPageChanged: (Int, Int) -> Unit = { _, _ -> },
    scrollToPage: Int? = null,
    onScrollProgressHandled: () -> Unit = {},
    searchQuery: String = "",
    searchTriggerNext: Int = 0,
    searchTriggerPrev: Int = 0,
    onSearchResultCount: (Int, Int, Boolean) -> Unit = { _, _, _ -> },
    zoomFactor: Float = 1f,
    onWebViewCreated: (WebView?) -> Unit = {},
    onDocumentReady: () -> Unit = {},
    onSingleTap: () -> Unit = {},
    onZoomChanged: (Float) -> Unit = {},
    onDoubleTap: (Float) -> Unit = {},
    onRendererFallback: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel = remember(fileEntity.path) { DocxPreviewViewModel() }
    val cacheManager = remember(context) { DocxCacheManager(File(context.cacheDir, "docx_cache")) }
    val documentLoader = remember(cacheManager) { DocxDocumentLoader(cacheManager) }
    val scope = rememberCoroutineScope()
    val previewState = viewModel.state
    val preparedDocument = viewModel.preparedDocument
    var reload by remember(fileEntity.path) { mutableIntStateOf(0) }
    var extracted by remember(fileEntity.path) { mutableStateOf<DocxExtractor.ExtractedDocx?>(null) }
    var recoveringText by remember(fileEntity.path) { mutableStateOf(false) }
    var dismissLargeFileBanner by remember(fileEntity.path) { mutableStateOf(false) }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.invalidate()
            onWebViewCreated(null)
        }
    }

    LaunchedEffect(fileEntity.path, reload) {
        val generation = viewModel.beginLoading()
        viewerState.setLoading()
        extracted = null
        recoveringText = false
        onRendererFallback(false)
        val result = runCatching {
            withContext(Dispatchers.IO) { documentLoader.prepare(File(fileEntity.path)) }
        }
        if (generation != viewModel.state.generation) return@LaunchedEffect
        result.onSuccess { prepared ->
            viewModel.acceptPrepared(generation, prepared)
        }.onFailure { error ->
            val loadError = error as? DocxDocumentLoader.LoadException
            val code = loadError?.code?.name ?: "LOAD_FAILED"
            viewModel.fail(generation, code)
            viewerState.reportError(code)
        }
    }

    val paper = if (isNightMode) ClaudeOnyx else ClaudeAlabaster
    val pagePaper = if (isNightMode) ClaudeDarkSurface else ClaudeWhite
    val ink = if (isNightMode) ClaudeDarkText else ClaudeCharcoal
    val edge = if (isNightMode) ClaudeDarkBorder else ClaudeSandBorder

    Box(modifier.fillMaxSize().background(paper)) {
        if (extracted != null) {
            val document = extracted!!
            Column(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.padding(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Formatted preview unavailable — showing bounded text recovery.", fontSize = 13.sp)
                        Text("Reason: ${previewState.detailCode ?: "render failed"}", fontSize = 11.sp)
                        if (document.truncated) Text("Recovered text was truncated at the safety limit.", fontSize = 11.sp)
                        TextButton(onClick = { reload++ }) { Text("Retry formatted view") }
                    }
                }
                if (document.paragraphs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No text content could be recovered from this DOCX file.")
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(document.paragraphs) { paragraph ->
                            Text(paragraph, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        } else {
            if (preparedDocument != null && previewState.stage != DocxPreviewStage.FAILED) {
                DocxWebViewHost(
                    file = preparedDocument.file,
                    generation = previewState.generation,
                    dark = isNightMode,
                    paper = paper.toCssHex(),
                    pagePaper = pagePaper.toCssHex(),
                    ink = ink.toCssHex(),
                    edge = edge.toCssHex(),
                    zoom = zoomFactor,
                    searchQuery = searchQuery,
                    next = searchTriggerNext,
                    previous = searchTriggerPrev,
                    scrollToPage = scrollToPage,
                    onVisualRender = { generation, pages, height, width, visiblePages ->
                        if (viewModel.onDocumentVisuallyRendered(generation, pages, height, width, visiblePages)) {
                            viewerState.setReady(pages)
                            viewerState.setPage(1, pages)
                            onPageChanged(1, pages)
                            onDocumentReady()
                            onRendererFallback(false)
                        }
                    },
                    onPageChanged = { page, pages ->
                        viewerState.setPage(page, pages)
                        onPageChanged(page, pages)
                    },
                    onProgress = viewModel::reportProgress,
                    onFailure = { generation, code ->
                        if (viewModel.fail(generation, code)) viewerState.reportError(code)
                    },
                    onSearch = { total, current, truncated ->
                        viewerState.setSearchResults(total, current)
                        onSearchResultCount(total, current, truncated)
                    },
                    onZoom = { factor ->
                        viewerState.updateZoom(factor)
                        onZoomChanged(factor)
                    },
                    onTap = onSingleTap,
                    onDoubleTap = onDoubleTap,
                    onCreated = onWebViewCreated,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (previewState.stage == DocxPreviewStage.FAILED) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Formatted DOCX preview unavailable", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = failureMessage(previewState),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = {
                            if (recoveringText) return@TextButton
                            recoveringText = true
                            val failedGeneration = previewState.generation
                            scope.launch {
                                val recovery = withContext(Dispatchers.IO) {
                                    runCatching { DocxExtractor.extractText(File(fileEntity.path)) }
                                        .getOrElse { DocxExtractor.ExtractedDocx(fileEntity.name, emptyList(), "", 0, 0) }
                                }
                                if (viewModel.state.generation == failedGeneration &&
                                    viewModel.state.stage == DocxPreviewStage.FAILED
                                ) {
                                    extracted = recovery
                                    onRendererFallback(true)
                                }
                                recoveringText = false
                            }
                        },
                        enabled = !recoveringText
                    ) {
                        Text(if (recoveringText) "Recovering text…" else "Show recovered text")
                    }
                    TextButton(onClick = { reload++ }) { Text("Retry formatted view") }
                }
            }

            AnimatedVisibility(
                visible = viewModel.isLoading,
                modifier = Modifier.fillMaxSize()
            ) {
                DocxLoadingOverlay(
                    state = previewState,
                    largeFileWarning = preparedDocument?.largeFileWarning == true
                )
            }

            if (previewState.stage == DocxPreviewStage.READY && previewState.largeFileWarning &&
                !dismissLargeFileBanner
            ) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Large DOCX: rendering may take longer.", modifier = Modifier.weight(1f), fontSize = 12.sp)
                        TextButton(onClick = { dismissLargeFileBanner = true }) { Text("Dismiss") }
                    }
                }
            }
        }
    }

    LaunchedEffect(searchQuery) { viewerState.setSearch(searchQuery) }
    LaunchedEffect(zoomFactor) { viewerState.updateZoom(zoomFactor) }
    LaunchedEffect(scrollToPage, previewState.stage) {
        if (scrollToPage != null && previewState.stage == DocxPreviewStage.READY) onScrollProgressHandled()
    }
}

@Composable
private fun DocxLoadingOverlay(state: DocxPreviewState, largeFileWarning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "docx-loading-icon")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(800), repeatMode = RepeatMode.Reverse),
        label = "docx-icon-pulse"
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.MenuBook,
                contentDescription = "Loading Word document",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }
            )
            CircularProgressIndicator(modifier = Modifier.padding(top = 20.dp))
            val progress = if (state.totalBytes > 0L) {
                "Loading document: ${(state.bytesLoaded * 100 / state.totalBytes).coerceIn(0, 100)}%"
            } else when (state.stage) {
                DocxPreviewStage.VALIDATING -> "Checking document package…"
                DocxPreviewStage.RENDERING -> "Rendering pages…"
                DocxPreviewStage.LAYOUT -> "Checking visible page layout…"
                else -> "Preparing offline preview…"
            }
            Text(progress, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
            if (state.totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = (state.bytesLoaded.toFloat() / state.totalBytes.toFloat()).coerceIn(0f, 1f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            if (largeFileWarning || state.largeFileWarning) {
                Text(
                    "This large document is still within the 25 MiB preview limit. Please wait while it is checked.",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun failureMessage(state: DocxPreviewState): String = when (state.failure) {
    DocxPreviewFailure.INVALID_DOCUMENT -> "The file is damaged, is not a valid Word document, or is password protected. Try an unencrypted DOCX."
    DocxPreviewFailure.RESOURCE_LIMIT_EXCEEDED -> "This document exceeds a safe preview limit. Large files are not opened as ZIP archives."
    DocxPreviewFailure.ENGINE_UNAVAILABLE -> "The offline document renderer could not start. Check the bundled viewer and retry."
    DocxPreviewFailure.EMPTY_DOCUMENT -> "No visible pages were produced from this document."
    DocxPreviewFailure.LAYOUT_FAILED -> "The document loaded but no visible page layout was confirmed. Retry the preview."
    DocxPreviewFailure.LOAD_FAILED, DocxPreviewFailure.RENDER_FAILED, null -> "The document could not be loaded. Your file has not been changed; you can retry or request text recovery."
}

@Composable
fun DocxPreviewInfoDialog(
    fileEntity: RecentFileEntity,
    estimatedPages: Int,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var extracted by remember(fileEntity.path) { mutableStateOf<DocxExtractor.ExtractedDocx?>(null) }
    var extractionFailed by remember(fileEntity.path) { mutableStateOf(false) }
    LaunchedEffect(fileEntity.path) {
        val result = withContext(Dispatchers.IO) {
            runCatching { DocxExtractor.extractText(File(fileEntity.path)) }
        }
        extracted = result.getOrNull()
        extractionFailed = result.isFailure
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("Close") } },
        title = { Text("Document information") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fileEntity.name, style = MaterialTheme.typography.titleSmall)
                Text("File size: ${android.text.format.Formatter.formatFileSize(context, fileEntity.size)}")
                Text("Estimated pages: ${estimatedPages.coerceAtLeast(0)}")
                Text(
                    text = when {
                        extracted != null -> "Approx. words: ${if (extracted!!.truncated) "at least " else ""}${extracted!!.wordCount}"
                        extractionFailed -> "Approx. word count unavailable"
                        else -> "Counting words…"
                    }
                )
                if (extracted?.truncated == true) {
                    Text("Word count uses the bounded recovery scan and may be incomplete.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    )
}
