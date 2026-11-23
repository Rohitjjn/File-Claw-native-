package com.example.ui.preview.docx

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocxCacheManagerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun firstOpenIsAMissAndUnchangedReopenIsAHit() {
        val source = temporaryFolder.newFile("source.docx").apply { writeText("first contents") }
        val cache = DocxCacheManager(File(temporaryFolder.root, "docx_cache"))

        val miss = cache.getOrCopy(source)
        val hit = cache.getOrCopy(source)

        assertFalse(miss.cacheHit)
        assertTrue(hit.cacheHit)
        assertEquals(source.readText(), hit.file.readText())
    }

    @Test fun changedSourceInvalidatesItsPreviousCacheEntry() {
        val source = temporaryFolder.newFile("source.docx").apply { writeText("first") }
        val cache = DocxCacheManager(File(temporaryFolder.root, "docx_cache"))
        val first = cache.getOrCopy(source)
        source.writeText("changed")
        source.setLastModified(source.lastModified() + 2_000)

        val replacement = cache.getOrCopy(source)

        assertFalse(replacement.cacheHit)
        assertEquals("changed", replacement.file.readText())
        assertTrue(first.file.exists())
    }

    @Test fun cacheEvictsLeastRecentlyUsedEntriesToStayWithinItsByteLimit() {
        val sourceA = temporaryFolder.newFile("a.docx").apply { writeText("123456") }
        val sourceB = temporaryFolder.newFile("b.docx").apply { writeText("abcdef") }
        val cacheDirectory = File(temporaryFolder.root, "docx_cache")
        val cache = DocxCacheManager(cacheDirectory, maxCacheBytes = 10, maxEntryBytes = 10)
        val cachedA = cache.getOrCopy(sourceA).file
        cachedA.setLastModified(1_000)

        val cachedB = cache.getOrCopy(sourceB).file

        assertFalse(cachedA.exists())
        assertTrue(cachedB.exists())
        assertTrue(cacheDirectory.listFiles { file -> file.extension == "docx" }.orEmpty().sumOf { it.length() } <= 10)
    }
}
