package com.example.core.format

import org.junit.Assert.assertEquals
import org.junit.Test

class DocxPreviewPolicyTest {
    @Test
    fun `routes exactly at preview limit to renderer`() {
        assertEquals(
            DocxPreviewPolicy.Route.FormattedPreview,
            DocxPreviewPolicy.route(DocxPreviewPolicy.MAX_FORMATTED_PREVIEW_BYTES)
        )
    }

    @Test
    fun `routes below preview limit to renderer`() {
        assertEquals(
            DocxPreviewPolicy.Route.FormattedPreview,
            DocxPreviewPolicy.route(DocxPreviewPolicy.MAX_FORMATTED_PREVIEW_BYTES - 1)
        )
    }

    @Test
    fun `routes above preview limit to an explicit DOCX limit state`() {
        assertEquals(
            DocxPreviewPolicy.Route.LimitExceeded(DocxPreviewPolicy.MAX_FORMATTED_PREVIEW_BYTES),
            DocxPreviewPolicy.route(DocxPreviewPolicy.MAX_FORMATTED_PREVIEW_BYTES + 1)
        )
    }
}
