package com.example.ui.preview.docx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxPreviewViewModelTest {
    @Test fun onlyAVisiblePositiveHeightCallbackCanCompleteTheCurrentLoad() {
        val viewModel = DocxPreviewViewModel()
        val generation = viewModel.beginLoading()
        val document = DocxDocumentLoader.PreparedDocument(
            file = java.io.File("fixture.docx"),
            sourceBytes = 120L,
            cacheHit = false,
            largeFileWarning = false
        )
        assertTrue(viewModel.acceptPrepared(generation, document))
        viewModel.reportProgress(generation, 60, 120)
        assertEquals(0.5f, viewModel.progressFraction)
        assertFalse(viewModel.onDocumentVisuallyRendered(generation, 2, 0, 600, 1))
        assertEquals(DocxPreviewStage.RENDERING, viewModel.state.stage)
        assertTrue(viewModel.onDocumentVisuallyRendered(generation, 2, 1800, 600, 1))
        assertEquals(DocxPreviewStage.READY, viewModel.state.stage)
        assertEquals(1800, viewModel.state.contentHeightPx)
    }

    @Test fun staleCallbacksAndFailedLoadsCannotBecomeReady() {
        val viewModel = DocxPreviewViewModel()
        val oldGeneration = viewModel.beginLoading()
        val activeGeneration = viewModel.beginLoading()
        assertFalse(viewModel.fail(oldGeneration, "old render failed"))
        assertTrue(viewModel.fail(activeGeneration, "INVALID_PACKAGE"))
        assertFalse(viewModel.isLoading)
        assertFalse(viewModel.onDocumentVisuallyRendered(activeGeneration, 1, 800, 400, 1))
        assertEquals(DocxPreviewStage.FAILED, viewModel.state.stage)
    }
}
