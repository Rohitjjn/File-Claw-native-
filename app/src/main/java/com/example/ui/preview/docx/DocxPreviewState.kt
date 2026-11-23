package com.example.ui.preview.docx

/** Immutable state contract for the local DOCX preview pipeline. */
enum class DocxPreviewStage {
    IDLE,
    LOADING,
    VALIDATING,
    RENDERING,
    LAYOUT,
    READY,
    FAILED
}

enum class DocxPreviewFailure {
    INVALID_DOCUMENT,
    LOAD_FAILED,
    ENGINE_UNAVAILABLE,
    RENDER_FAILED,
    LAYOUT_FAILED,
    EMPTY_DOCUMENT,
    RESOURCE_LIMIT_EXCEEDED
}

data class DocxPreviewState(
    val generation: Long = 0,
    val stage: DocxPreviewStage = DocxPreviewStage.IDLE,
    val failure: DocxPreviewFailure? = null,
    val detailCode: String? = null,
    /** The renderer's page estimate, not Microsoft Word pagination. */
    val estimatedPages: Int? = null,
    val contentHeightPx: Int = 0,
    val contentWidthPx: Int = 0,
    val bytesLoaded: Long = 0L,
    val totalBytes: Long = 0L,
    val largeFileWarning: Boolean = false,
    val cacheHit: Boolean = false
)

/** Pure reducer: terminal/stale callbacks cannot turn a failed or replaced render into READY. */
class DocxPreviewReducer {
    var state: DocxPreviewState = DocxPreviewState()
        private set

    fun begin(): Long {
        val generation = state.generation + 1
        state = DocxPreviewState(generation = generation, stage = DocxPreviewStage.LOADING)
        return generation
    }

    fun transition(generation: Long, next: DocxPreviewStage): Boolean {
        if (generation != state.generation || state.stage == DocxPreviewStage.FAILED ||
            state.stage == DocxPreviewStage.READY
        ) return false
        val allowed = when (state.stage) {
            DocxPreviewStage.IDLE -> next == DocxPreviewStage.LOADING
            DocxPreviewStage.LOADING -> next == DocxPreviewStage.VALIDATING
            DocxPreviewStage.VALIDATING -> next == DocxPreviewStage.RENDERING
            DocxPreviewStage.RENDERING -> next == DocxPreviewStage.LAYOUT
            DocxPreviewStage.LAYOUT -> next == DocxPreviewStage.READY
            DocxPreviewStage.READY, DocxPreviewStage.FAILED -> false
        }
        if (!allowed) return false
        state = state.copy(stage = next)
        return true
    }

    fun ready(generation: Long, pages: Int, contentHeightPx: Int, contentWidthPx: Int, visibleContent: Boolean): Boolean {
        if (pages <= 0 || contentHeightPx <= 0 || contentWidthPx <= 0 || !visibleContent ||
            !transition(generation, DocxPreviewStage.READY)
        ) return false
        state = state.copy(
            estimatedPages = pages,
            contentHeightPx = contentHeightPx,
            contentWidthPx = contentWidthPx
        )
        return true
    }

    fun fail(generation: Long, category: DocxPreviewFailure, detailCode: String): Boolean {
        if (generation != state.generation || state.stage == DocxPreviewStage.READY ||
            state.stage == DocxPreviewStage.FAILED
        ) return false
        state = state.copy(
            stage = DocxPreviewStage.FAILED,
            failure = category,
            detailCode = detailCode.filterNot { it.isISOControl() }.take(80)
        )
        return true
    }
}

fun classifyDocxFailure(code: String): DocxPreviewFailure = when {
    code.contains("LIMIT", ignoreCase = true) || code.contains("TOO_LARGE", ignoreCase = true) ->
        DocxPreviewFailure.RESOURCE_LIMIT_EXCEEDED
    code.contains("ENGINE", ignoreCase = true) || code.contains("RENDERER_UNAVAILABLE", ignoreCase = true) ->
        DocxPreviewFailure.ENGINE_UNAVAILABLE
    code.contains("INVALID", ignoreCase = true) || code.contains("ZIP", ignoreCase = true) ||
        code.contains("PACKAGE", ignoreCase = true) -> DocxPreviewFailure.INVALID_DOCUMENT
    code.contains("EMPTY", ignoreCase = true) || code.contains("WITHOUT_PAGES", ignoreCase = true) ||
        code.contains("NO_PAGES", ignoreCase = true) -> DocxPreviewFailure.EMPTY_DOCUMENT
    code.contains("LAYOUT", ignoreCase = true) || code.contains("VISIBLE", ignoreCase = true) ||
        code.contains("VIEWPORT", ignoreCase = true) -> DocxPreviewFailure.LAYOUT_FAILED
    code.contains("RENDER", ignoreCase = true) -> DocxPreviewFailure.RENDER_FAILED
    else -> DocxPreviewFailure.LOAD_FAILED
}
