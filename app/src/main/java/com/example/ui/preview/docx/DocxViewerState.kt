package com.example.ui.preview.docx

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.core.format.DocxZoomPolicy

/** Compose-facing state contract for the DOCX preview and its Bottom Tray controls. */
@Stable
class DocxViewerState {
    var stage by mutableStateOf(DocxPreviewStage.IDLE)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var currentMatch by mutableIntStateOf(0)
        private set
    var totalMatches by mutableIntStateOf(0)
        private set
    var currentPage by mutableIntStateOf(1)
        private set
    var pageCount by mutableIntStateOf(0)
        private set
    var nextSearchRequest by mutableIntStateOf(0)
        private set
    var previousSearchRequest by mutableIntStateOf(0)
        private set
    var requestedPage by mutableIntStateOf(0)
        private set
    private var printAction by mutableStateOf<(() -> Unit)?>(null)

    val isLoading: Boolean get() = stage in setOf(
        DocxPreviewStage.LOADING, DocxPreviewStage.VALIDATING,
        DocxPreviewStage.RENDERING, DocxPreviewStage.LAYOUT
    )
    val isReady: Boolean get() = stage == DocxPreviewStage.READY
    val hasError: Boolean get() = stage == DocxPreviewStage.FAILED

    fun setLoading() { stage = DocxPreviewStage.LOADING; error = null }
    fun setReady(pages: Int) {
        stage = DocxPreviewStage.READY
        pageCount = pages.coerceAtLeast(0)
        currentPage = currentPage.coerceIn(1, pageCount.coerceAtLeast(1))
        error = null
    }
    fun reportError(message: String) { stage = DocxPreviewStage.FAILED; error = message }
    fun updateZoom(value: Float) { zoom = DocxZoomPolicy.coerce(value) }
    fun setSearch(query: String) { searchQuery = query; currentMatch = 0; totalMatches = 0 }
    fun setSearchResults(total: Int, current: Int) {
        totalMatches = total.coerceAtLeast(0)
        currentMatch = if (totalMatches == 0) 0 else current.coerceIn(1, totalMatches)
    }
    fun setPage(page: Int, pages: Int) {
        pageCount = pages.coerceAtLeast(0)
        currentPage = if (pageCount == 0) 1 else page.coerceIn(1, pageCount)
    }
    fun findNext() { nextSearchRequest++ }
    fun findPrevious() { previousSearchRequest++ }
    /** Requests a one-based page; the WebView adapter translates it to its zero-based index. */
    fun goToPage(page: Int) { requestedPage = page.coerceAtLeast(1) }
    fun installPrintAction(action: (() -> Unit)?) { printAction = action }
    fun print() { printAction?.invoke() }
}

/** Remembers an independent viewer state for each selected DOCX path. */
@Composable
fun rememberDocxViewerState(fileKey: Any? = null): DocxViewerState = remember(fileKey) { DocxViewerState() }
