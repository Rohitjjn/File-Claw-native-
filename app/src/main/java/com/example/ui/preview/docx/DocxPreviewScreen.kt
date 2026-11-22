package com.example.ui.preview.docx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.format.DocxExtractor
import com.example.ui.theme.ClaudeAlabaster
import com.example.ui.theme.ClaudeCharcoal
import com.example.ui.theme.ClaudeWhite
import com.example.ui.theme.ClaudeDarkBorder
import com.example.ui.theme.ClaudeDarkSurface
import com.example.ui.theme.ClaudeDarkText
import com.example.ui.theme.ClaudeOnyx
import com.example.ui.theme.ClaudeSandBorder
import androidx.compose.ui.graphics.toArgb
import com.example.data.RecentFileEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Color.toCssHex(): String = "#" + toArgb().toUInt().toString(16).takeLast(6)

/** DOCX-only compose surface. Toolbar, print and the other file viewers remain owned by the caller. */
@Composable
fun DocxPreviewWebView(
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
    val reducer = remember(fileEntity.path) { DocxPreviewReducer() }
    var generation by remember(fileEntity.path) { mutableLongStateOf(reducer.begin()) }
    var state by remember(fileEntity.path) { mutableStateOf(reducer.state) }
    var extracted by remember(fileEntity.path) { mutableStateOf<DocxExtractor.ExtractedDocx?>(null) }
    var reload by remember(fileEntity.path) { mutableIntStateOf(0) }
    var recoveringText by remember(fileEntity.path) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fail(code: String) {
        if (!reducer.fail(generation, classifyDocxFailure(code), code)) return
        state = reducer.state
        viewerState.setError(code)
    }

    fun recoverTextExplicitly() {
        if (recoveringText || state.stage != DocxPreviewStage.FAILED) return
        recoveringText = true
        val failedGeneration = generation
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DocxExtractor.extractText(File(fileEntity.path)) }
                    .getOrElse { DocxExtractor.ExtractedDocx(fileEntity.name, emptyList(), "", 0, 0) }
            }
            if (generation != failedGeneration || state.stage != DocxPreviewStage.FAILED) return@launch
            extracted = result
            recoveringText = false
            onRendererFallback(true)
        }
    }

    val paper = if (isNightMode) ClaudeOnyx else ClaudeAlabaster
    val pagePaper = if (isNightMode) ClaudeDarkSurface else ClaudeWhite
    val ink = if (isNightMode) ClaudeDarkText else ClaudeCharcoal
    val edge = if (isNightMode) ClaudeDarkBorder else ClaudeSandBorder
    Box(modifier.fillMaxSize().background(paper)) {
        if (extracted != null) {
            val doc = extracted!!
            Column(Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.padding(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Formatted preview unavailable — showing bounded text recovery.", fontSize = 13.sp)
                        Text("Reason: ${state.detailCode ?: "render failed"}", fontSize = 11.sp)
                        if (doc.truncated) Text("Recovered text was truncated at the safety limit.", fontSize = 11.sp)
                        TextButton(onClick = {
                            extracted = null
                            val nextGeneration = reducer.begin()
                            generation = nextGeneration
                            state = reducer.state
                            reload++
                        }) { Text("Retry formatted view") }
                    }
                }
                if (doc.paragraphs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No text content could be recovered from this DOCX file.")
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                        items(doc.paragraphs.size) { index ->
                            Text(doc.paragraphs[index], modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        } else if (state.stage == DocxPreviewStage.FAILED) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Formatted DOCX preview unavailable", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Reason: ${state.detailCode ?: "render failed"}. The document has not been converted to plain text.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = recoverTextExplicitly, enabled = !recoveringText) {
                    Text(if (recoveringText) "Recovering text…" else "Show recovered text")
                }
                TextButton(onClick = {
                    val nextGeneration = reducer.begin()
                    generation = nextGeneration
                    state = reducer.state
                    recoveringText = false
                    reload++
                }) { Text("Retry formatted view") }
            }
        } else {
            androidx.compose.runtime.key(fileEntity.path, reload) {
                DocxWebViewHost(
                    file = File(fileEntity.path), generation = generation, dark = isNightMode,
                    paper = paper.toCssHex(), pagePaper = pagePaper.toCssHex(), ink = ink.toCssHex(), edge = edge.toCssHex(),
                    zoom = zoomFactor, searchQuery = searchQuery, next = searchTriggerNext, previous = searchTriggerPrev,
                    scrollToPage = scrollToPage,
                    onReady = { pages ->
                        listOf(DocxPreviewStage.VALIDATING, DocxPreviewStage.RENDERING, DocxPreviewStage.LAYOUT).forEach {
                            reducer.transition(generation, it)
                        }
                        if (reducer.ready(generation, pages, visibleContent = true)) {
                            state = reducer.state
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
                    onFailure = ::fail,
                    onSearch = { total, current, truncated ->
                        viewerState.setSearchResults(total, current)
                        onSearchResultCount(total, current, truncated)
                    },
                    onZoom = { factor ->
                        viewerState.setZoom(factor)
                        onZoomChanged(factor)
                    },
                    onTap = onSingleTap,
                    onDoubleTap = onDoubleTap,
                    onCreated = onWebViewCreated,
                    modifier = Modifier.fillMaxSize()
                )
            }

        }
    }

    LaunchedEffect(fileEntity.path, reload) {
        if (state.stage != DocxPreviewStage.FAILED) viewerState.setLoading()
    }
    LaunchedEffect(searchQuery) { viewerState.setSearch(searchQuery) }
    LaunchedEffect(zoomFactor) { viewerState.setZoom(zoomFactor) }

    LaunchedEffect(scrollToPage, state.stage) {
        if (scrollToPage != null && state.stage == DocxPreviewStage.READY) onScrollProgressHandled()
    }
}
