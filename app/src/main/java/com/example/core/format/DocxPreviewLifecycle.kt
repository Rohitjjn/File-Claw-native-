package com.example.core.format

/** Observable stages for the formatted DOCX preview. Renderer completion is not READY. */
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

data class DocxPreviewSnapshot(
    val generation: Long = 0,
    val stage: DocxPreviewStage = DocxPreviewStage.IDLE,
    val failure: DocxPreviewFailure? = null,
    val detailCode: String? = null,
    val estimatedPageCount: Int? = null
)

/**
 * Small deterministic reducer shared by the Android host and JVM tests. A new generation
 * invalidates prior callbacks; out-of-order, duplicate, and terminal events are ignored.
 */
class DocxPreviewLifecycle {
    var snapshot: DocxPreviewSnapshot = DocxPreviewSnapshot()
        private set

    fun begin(): Long {
        val next = snapshot.generation + 1
        snapshot = DocxPreviewSnapshot(generation = next, stage = DocxPreviewStage.LOADING)
        return next
    }

    fun advance(generation: Long, stage: DocxPreviewStage): Boolean {
        if (generation != snapshot.generation || snapshot.stage == DocxPreviewStage.FAILED ||
            snapshot.stage == DocxPreviewStage.READY
        ) return false
        val allowed = when (snapshot.stage) {
            DocxPreviewStage.IDLE -> stage == DocxPreviewStage.LOADING
            DocxPreviewStage.LOADING -> stage == DocxPreviewStage.VALIDATING
            DocxPreviewStage.VALIDATING -> stage == DocxPreviewStage.RENDERING
            DocxPreviewStage.RENDERING -> stage == DocxPreviewStage.LAYOUT
            DocxPreviewStage.LAYOUT -> stage == DocxPreviewStage.READY
            DocxPreviewStage.READY, DocxPreviewStage.FAILED -> false
        }
        if (!allowed) return false
        snapshot = snapshot.copy(stage = stage)
        return true
    }

    fun fail(generation: Long, failure: DocxPreviewFailure, detailCode: String): Boolean {
        if (generation != snapshot.generation || snapshot.stage == DocxPreviewStage.READY ||
            snapshot.stage == DocxPreviewStage.FAILED
        ) return false
        snapshot = snapshot.copy(
            stage = DocxPreviewStage.FAILED,
            failure = failure,
            detailCode = detailCode.take(80)
        )
        return true
    }

    fun markReady(generation: Long, estimatedPageCount: Int, visibleLayout: Boolean): Boolean {
        if (estimatedPageCount <= 0 || !visibleLayout) return false
        if (!advance(generation, DocxPreviewStage.READY)) return false
        snapshot = snapshot.copy(estimatedPageCount = estimatedPageCount)
        return true
    }
}

/** Maps bounded renderer error codes to user-actionable failure categories. */
fun classifyDocxPreviewFailure(code: String): DocxPreviewFailure = when {
    code.contains("LIMIT", ignoreCase = true) || code.contains("TOO_LARGE", ignoreCase = true) ->
        DocxPreviewFailure.RESOURCE_LIMIT_EXCEEDED
    code.contains("ENGINE", ignoreCase = true) || code.contains("RENDERER_UNAVAILABLE", ignoreCase = true) ->
        DocxPreviewFailure.ENGINE_UNAVAILABLE
    code.contains("INVALID", ignoreCase = true) || code.contains("ZIP", ignoreCase = true) ||
        code.contains("PACKAGE", ignoreCase = true) -> DocxPreviewFailure.INVALID_DOCUMENT
    code.contains("EMPTY", ignoreCase = true) || code.contains("NO_PAGES", ignoreCase = true) ->
        DocxPreviewFailure.EMPTY_DOCUMENT
    code.contains("LAYOUT", ignoreCase = true) || code.contains("VISIBLE", ignoreCase = true) ->
        DocxPreviewFailure.LAYOUT_FAILED
    code.contains("RENDER", ignoreCase = true) -> DocxPreviewFailure.RENDER_FAILED
    else -> DocxPreviewFailure.LOAD_FAILED
}
