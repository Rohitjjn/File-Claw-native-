package com.example.ui.preview.docx

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Owns one DOCX render generation and rejects stale loader/WebView callbacks. */
@Stable
class DocxPreviewViewModel {
    private val reducer = DocxPreviewReducer()

    var state by mutableStateOf(reducer.state)
        private set
    var preparedDocument by mutableStateOf<DocxDocumentLoader.PreparedDocument?>(null)
        private set

    val isLoading: Boolean
        get() = state.stage in setOf(
            DocxPreviewStage.IDLE,
            DocxPreviewStage.LOADING,
            DocxPreviewStage.VALIDATING,
            DocxPreviewStage.RENDERING,
            DocxPreviewStage.LAYOUT
        )

    val progressFraction: Float
        get() = if (state.totalBytes <= 0L) 0f else
            (state.bytesLoaded.toDouble() / state.totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)

    fun beginLoading(): Long {
        preparedDocument = null
        val generation = reducer.begin()
        state = reducer.state
        return generation
    }

    fun acceptPrepared(generation: Long, document: DocxDocumentLoader.PreparedDocument): Boolean {
        if (generation != state.generation || !reducer.transition(generation, DocxPreviewStage.VALIDATING) ||
            !reducer.transition(generation, DocxPreviewStage.RENDERING)
        ) return false
        preparedDocument = document
        state = reducer.state.copy(
            bytesLoaded = 0L,
            totalBytes = document.sourceBytes,
            largeFileWarning = document.largeFileWarning,
            cacheHit = document.cacheHit
        )
        return true
    }

    fun reportProgress(generation: Long, loadedBytes: Long, totalBytes: Long) {
        if (generation != state.generation || state.stage != DocxPreviewStage.RENDERING) return
        state = state.copy(
            bytesLoaded = loadedBytes.coerceAtLeast(0L).coerceAtMost(totalBytes.coerceAtLeast(0L)),
            totalBytes = totalBytes.coerceAtLeast(state.totalBytes)
        )
    }

    fun onDocumentVisuallyRendered(
        generation: Long,
        pageCount: Int,
        contentHeightPx: Int,
        contentWidthPx: Int,
        visiblePageCount: Int
    ): Boolean {
        if (generation != state.generation || pageCount <= 0 || contentHeightPx <= 0 ||
            contentWidthPx <= 0 || visiblePageCount <= 0
        ) return false
        if (!reducer.transition(generation, DocxPreviewStage.LAYOUT)) return false
        if (!reducer.ready(
                generation = generation,
                pages = pageCount,
                contentHeightPx = contentHeightPx,
                contentWidthPx = contentWidthPx,
                visibleContent = true
            )
        ) return false
        state = reducer.state
        return true
    }

    fun fail(generation: Long, code: String, failure: DocxPreviewFailure = classifyDocxFailure(code)): Boolean {
        if (!reducer.fail(generation, failure, code)) return false
        preparedDocument = null
        state = reducer.state
        return true
    }

    /** Invalidates any late callback when Compose releases or switches this document host. */
    fun invalidate() {
        val generation = reducer.begin()
        preparedDocument = null
        state = DocxPreviewState(generation = generation, stage = DocxPreviewStage.IDLE)
    }
}
