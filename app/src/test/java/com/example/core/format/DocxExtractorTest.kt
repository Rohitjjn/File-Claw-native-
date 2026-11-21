package com.example.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxExtractorTest {

    @Test
    fun extractsParagraphsAndBasicXmlEntitiesFromSyntheticDocx() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p><w:r><w:t>File Claw Offline Document</w:t></w:r></w:p>
                    <w:p><w:r><w:t>This is a paragraph test for DOCX extraction &amp; viewing.</w:t></w:r></w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val result = DocxExtractor.extractText(zipOf("word/document.xml" to xml), "test.docx")

        assertEquals("File Claw Offline Document", result.title)
        assertEquals(2, result.paragraphs.size)
        assertTrue(result.plainText.contains("File Claw Offline Document"))
        assertTrue(result.plainText.contains("This is a paragraph test for DOCX extraction & viewing."))
        assertTrue(result.wordCount > 5)
        assertFalse(result.truncated)
    }

    @Test
    fun preservesInlineBreakAndTabTokensInRecoveryText() {
        val xml = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p><w:r><w:t>one</w:t><w:br/><w:t>two</w:t><w:tab/><w:t>three</w:t></w:r></w:p></w:body>
            </w:document>
        """.trimIndent()
        val result = DocxExtractor.extractText(zipOf("word/document.xml" to xml))
        assertEquals("one\ntwo\tthree", result.plainText)
    }

    @Test
    fun refusesInflatedDocumentXmlAboveTheHardLimit() {
        val oversizedXml = "<w:document><w:body><w:p><w:r><w:t>" +
            "x".repeat(DocxExtractor.MAX_DOCUMENT_XML_BYTES) +
            "</w:t></w:r></w:p></w:body></w:document>"
        val error = runCatching {
            DocxExtractor.extractText(zipOf("word/document.xml" to oversizedXml))
        }.exceptionOrNull()
        assertTrue(error is DocxExtractor.ExtractionLimitException)
    }

    @Test
    fun refusesExcessiveInflatedZipEntriesBeforeDocumentXml() {
        val largeSkippedEntry = "x".repeat(DocxExtractor.MAX_ZIP_SCAN_BYTES.toInt() + 1)
        val doc = "<w:document><w:body><w:p><w:r><w:t>text</w:t></w:r></w:p></w:body></w:document>"
        val error = runCatching {
            DocxExtractor.extractText(zipOf("word/media/large.bin" to largeSkippedEntry, "word/document.xml" to doc))
        }.exceptionOrNull()
        assertTrue(error is DocxExtractor.ExtractionLimitException)
    }

    @Test
    fun capsRecoveredTextAndMarksResultAsTruncated() {
        val text = "a".repeat(DocxExtractor.MAX_RECOVERED_TEXT_CHARS + 100)
        val xml = "<w:document><w:body><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:body></w:document>"
        val result = DocxExtractor.extractText(zipOf("word/document.xml" to xml))
        assertEquals(DocxExtractor.MAX_RECOVERED_TEXT_CHARS, result.plainText.length)
        assertTrue(result.truncated)
    }

    @Test
    fun missingDocumentXmlReturnsEmptyRecoveryResult() {
        val result = DocxExtractor.extractText(zipOf("something_else.txt" to "hello"), "empty.docx")
        assertEquals(0, result.paragraphs.size)
        assertEquals("", result.plainText)
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
