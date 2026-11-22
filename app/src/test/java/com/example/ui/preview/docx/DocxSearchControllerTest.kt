package com.example.ui.preview.docx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxSearchControllerTest {
    @Test fun queryChangeAndNavigationAreSeparateCommands() {
        val initial = DocxSearchController.Cursor()
        val search = DocxSearchController.plan(initial, " phrase ", 0, 0)
        assertEquals(listOf(DocxSearchController.Command.Search("phrase")), search.commands)
        val next = DocxSearchController.plan(search.cursor, "phrase", 2, 0)
        assertEquals(listOf(DocxSearchController.Command.Next, DocxSearchController.Command.Next), next.commands)
        val previous = DocxSearchController.plan(next.cursor, "phrase", 2, 1)
        assertEquals(listOf(DocxSearchController.Command.Previous), previous.commands)
    }

    @Test fun clearIsSentOnlyAfterARealQuery() {
        val active = DocxSearchController.plan(DocxSearchController.Cursor(), "foo", 0, 0)
        assertEquals(listOf(DocxSearchController.Command.Clear), DocxSearchController.plan(active.cursor, "  ", 0, 0).commands)
        assertTrue(DocxSearchController.plan(DocxSearchController.Cursor(), "", 0, 0).commands.isEmpty())
    }
}
