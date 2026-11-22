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
import com.example.data.RecentFileEntity
import com.example.viewmodel.MainViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** DOCX-only compose surface. Toolbar, print and the other file viewers remain owned by the caller. */
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
    val scope = rememberCoroutineScope()

    fun fail(code: String) {
        if (!reducer.fail(generation, classifyDocxFailure(code), code)) return
        state = reducer.state
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { DocxExtractor.extractText(File(fileEntity.path)) }
                    .getOrElse { DocxExtractor.ExtractedDocx(fileEntity.name, emptyList(), "", 0, 0) }
            }
            if (generation != state.generation || state.stage != DocxPreviewStage.FAILED) return@launch
            extracted = result
            onRendererFallback(true)
        }
    }

    Box(modifier.fillMaxSize().background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5))) {
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
        } else {
            androidx.compose.runtime.key(fileEntity.path, reload) {
                DocxWebViewHost(
                    file = File(fileEntity.path), generation = generation, dark = isNightMode,
                    zoom = zoomFactor, searchQuery = searchQuery, next = searchTriggerNext, previous = searchTriggerPrev,
                    scrollToPage = scrollToPage,
                    onReady = { pages ->
                        listOf(DocxPreviewStage.VALIDATING, DocxPreviewStage.RENDERING, DocxPreviewStage.LAYOUT).forEach {
                            reducer.transition(generation, it)
                        }
                        if (reducer.ready(generation, pages, visibleContent = true)) {
                            state = reducer.state
                            onPageChanged(1, pages)
                            onDocumentReady()
                            onRendererFallback(false)
                        }
                    },
                    onPageChanged = onPageChanged,
                    onFailure = ::fail,
                    onSearch = onSearchResultCount,
                    onZoom = onZoomChanged,
                    onTap = onSingleTap,
                    onDoubleTap = onDoubleTap,
                    onCreated = onWebViewCreated,
                    modifier = Modifier.fillMaxSize()
                )
            }

        }
    }

    LaunchedEffect(scrollToPage, state.stage) {
        if (scrollToPage != null && state.stage == DocxPreviewStage.READY) onScrollProgressHandled()
    }
}
