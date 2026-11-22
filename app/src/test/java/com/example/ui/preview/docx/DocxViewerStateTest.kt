package com.example.ui.preview.docx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxViewerStateTest {
    @Test fun exposesRenderSearchNavigationZoomAndPrintState() {
        val state = DocxViewerState()
        state.setLoading()
        assertTrue(state.isLoading)
        state.setReady(4)
        assertTrue(state.isReady)
        state.setPage(3, 4)
        assertEquals(3, state.currentPage)
        assertEquals(4, state.pageCount)
        state.setZoom(9f)
        assertEquals(5f, state.zoom)
        state.setSearch("needle")
        state.setSearchResults(7, 5)
        state.findNext()
        state.findPrevious()
        state.goToPage(4)
        assertEquals(1, state.nextSearchRequest)
        assertEquals(1, state.previousSearchRequest)
        assertEquals(4, state.requestedPage)
        assertEquals("needle", state.searchQuery)
        assertEquals(7, state.totalMatches)
        assertEquals(5, state.currentMatch)
        var printed = false
        state.setPrintAction { printed = true }
        state.print()
        assertTrue(printed)
        state.setError("INVALID_PACKAGE")
        assertTrue(state.hasError)
        assertFalse(state.isLoading)
        assertEquals("INVALID_PACKAGE", state.error)
    }
}
