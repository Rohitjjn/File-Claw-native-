package com.example.ui.preview.docx

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocxDocumentLoaderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun validatesAndCachesAValidHindiDocxThenUsesTheCacheOnReopen() {
        val source = validDocx("नमस्ते दुनिया")
        val loader = DocxDocumentLoader(DocxCacheManager(File(temporaryFolder.root, "docx_cache")))

        val first = loader.prepare(source)
        val second = loader.prepare(source)

        assertFalse(first.cacheHit)
        assertTrue(second.cacheHit)
        assertTrue(first.file.path.contains("docx_cache"))
        assertEquals(source.length(), second.file.length())
    }

    @Test fun reportsCorruptAndEncryptedLikePackagesAsReadableErrors() {
        val source = temporaryFolder.newFile("broken.docx").apply { writeText("not a zip") }
        val loader = DocxDocumentLoader(DocxCacheManager(File(temporaryFolder.root, "docx_cache")))

        val error = runCatching { loader.prepare(source) }.exceptionOrNull()

        assertTrue(error is DocxDocumentLoader.LoadException)
        assertEquals(
            DocxDocumentLoader.FailureCode.INVALID_OR_ENCRYPTED_PACKAGE,
            (error as DocxDocumentLoader.LoadException).code
        )
    }

    @Test fun warnsAboveFifteenMiBAndStopsAboveTwentyFiveMiB() {
        assertFalse(DocxDocumentLoader.hasLargeFileWarning(DocxDocumentLoader.LARGE_FILE_WARNING_BYTES))
        assertTrue(DocxDocumentLoader.hasLargeFileWarning(DocxDocumentLoader.LARGE_FILE_WARNING_BYTES + 1))

        val source = temporaryFolder.newFile("large.docx")
        java.io.RandomAccessFile(source, "rw").use {
            it.setLength(DocxPreviewPolicy.MAX_FORMATTED_PREVIEW_BYTES + 1)
        }
        val loader = DocxDocumentLoader(DocxCacheManager(File(temporaryFolder.root, "docx_cache")))

        val error = runCatching { loader.prepare(source) }.exceptionOrNull()

        assertTrue(error is DocxDocumentLoader.LoadException)
        assertEquals(DocxDocumentLoader.FailureCode.FILE_TOO_LARGE, (error as DocxDocumentLoader.LoadException).code)
    }

    private fun validDocx(text: String): File {
        val source = temporaryFolder.newFile("valid.docx")
        ZipOutputStream(source.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("<Types/>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write("<w:document>$text</w:document>".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return source
    }
}
