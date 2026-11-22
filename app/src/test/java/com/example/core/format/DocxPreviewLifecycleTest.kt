package com.example.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxPreviewLifecycleTest {
    @Test
    fun readyRequiresOrderedStagesAndVisiblePositivePageCount() {
        val lifecycle = DocxPreviewLifecycle()
        val generation = lifecycle.begin()

        assertFalse(lifecycle.advance(generation, DocxPreviewStage.RENDERING))
        assertTrue(lifecycle.advance(generation, DocxPreviewStage.VALIDATING))
        assertTrue(lifecycle.advance(generation, DocxPreviewStage.RENDERING))
        assertTrue(lifecycle.advance(generation, DocxPreviewStage.LAYOUT))
        assertFalse(lifecycle.markReady(generation, estimatedPageCount = 1, visibleLayout = false))
        assertEquals(DocxPreviewStage.LAYOUT, lifecycle.snapshot.stage)
        assertTrue(lifecycle.markReady(generation, estimatedPageCount = 2, visibleLayout = true))
        assertEquals(DocxPreviewStage.READY, lifecycle.snapshot.stage)
        assertEquals(2, lifecycle.snapshot.estimatedPageCount ?: -1)
        assertFalse(lifecycle.fail(generation, DocxPreviewFailure.RENDER_FAILED, "late"))
    }

    @Test
    fun newGenerationRejectsStaleCallbacksAndRestartsPipeline() {
        val lifecycle = DocxPreviewLifecycle()
        val oldGeneration = lifecycle.begin()
        val newGeneration = lifecycle.begin()

        assertFalse(lifecycle.advance(oldGeneration, DocxPreviewStage.VALIDATING))
        assertEquals(DocxPreviewStage.LOADING, lifecycle.snapshot.stage)
        assertTrue(lifecycle.advance(newGeneration, DocxPreviewStage.VALIDATING))
    }

    @Test
    fun terminalFailureIsStableAndDetailIsBounded() {
        val lifecycle = DocxPreviewLifecycle()
        val generation = lifecycle.begin()
        val detail = "x".repeat(100)

        assertTrue(lifecycle.fail(generation, DocxPreviewFailure.INVALID_DOCUMENT, detail))
        assertEquals(80, lifecycle.snapshot.detailCode?.length)
        assertFalse(lifecycle.advance(generation, DocxPreviewStage.VALIDATING))
        assertEquals(DocxPreviewFailure.INVALID_DOCUMENT, lifecycle.snapshot.failure)
    }

    @Test
    fun errorsMapToExplicitCategories() {
        assertEquals(DocxPreviewFailure.ENGINE_UNAVAILABLE, classifyDocxPreviewFailure("DOCX_RENDERER_UNAVAILABLE"))
        assertEquals(DocxPreviewFailure.LAYOUT_FAILED, classifyDocxPreviewFailure("DOCX_PAGE_OUTSIDE_VIEWPORT"))
        assertEquals(DocxPreviewFailure.EMPTY_DOCUMENT, classifyDocxPreviewFailure("DOCX_RENDERED_WITHOUT_PAGES"))
        assertEquals(DocxPreviewFailure.RESOURCE_LIMIT_EXCEEDED, classifyDocxPreviewFailure("ZIP_RESOURCE_LIMIT"))
    }
}
