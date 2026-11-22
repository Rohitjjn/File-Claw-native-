package com.example.ui.preview.docx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxPreviewStateTest {
    @Test fun reducerOnlyAcceptsCurrentGenerationAndReadyAfterVisibleLayout() {
        val reducer = DocxPreviewReducer()
        val first = reducer.begin()
        val second = reducer.begin()
        assertFalse(reducer.transition(first, DocxPreviewStage.VALIDATING))
        assertFalse(reducer.ready(second, 1, visibleContent = false))
        assertTrue(reducer.transition(second, DocxPreviewStage.VALIDATING))
        assertTrue(reducer.transition(second, DocxPreviewStage.RENDERING))
        assertTrue(reducer.transition(second, DocxPreviewStage.LAYOUT))
        assertTrue(reducer.ready(second, 3, visibleContent = true))
        assertEquals(DocxPreviewStage.READY, reducer.state.stage)
        assertEquals(3, reducer.state.estimatedPages)
        assertFalse(reducer.fail(second, DocxPreviewFailure.RENDER_FAILED, "late failure"))
    }

    @Test fun failureCodesAreCategorizedAndSanitized() {
        assertEquals(DocxPreviewFailure.RESOURCE_LIMIT_EXCEEDED, classifyDocxFailure("RESOURCE_LIMIT_EXCEEDED"))
        assertEquals(DocxPreviewFailure.INVALID_DOCUMENT, classifyDocxFailure("INVALID_PACKAGE"))
        assertEquals(DocxPreviewFailure.LAYOUT_FAILED, classifyDocxFailure("LAYOUT_FAILED_NOT_VISIBLE"))
        val reducer = DocxPreviewReducer()
        val id = reducer.begin()
        assertTrue(reducer.fail(id, DocxPreviewFailure.LOAD_FAILED, "bad\u0000source"))
        assertEquals("badsource", reducer.state.detailCode)
    }
}
