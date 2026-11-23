package com.example.ui.preview.docx

import org.junit.Assert.assertEquals
import org.junit.Test

class DocxTrayActionTest {
    @Test fun readingTrayHasOnlyTheFiveRequestedActionsInOrder() {
        assertEquals(
            listOf("Page", "Rotate", "Search", "Print", "Edit"),
            DocxTrayAction.entries.map { it.label }
        )
    }
}
