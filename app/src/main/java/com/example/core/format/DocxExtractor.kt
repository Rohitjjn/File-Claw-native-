package com.example.core.format

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Best-effort, offline text recovery for a failed formatted DOCX preview.
 * This is not a Word renderer and deliberately bounds both inflated XML and recovered text.
 */
object DocxExtractor {

    const val MAX_DOCUMENT_XML_BYTES = 8 * 1024 * 1024
    const val MAX_ZIP_SCAN_BYTES = 32 * 1024 * 1024
    const val MAX_RECOVERED_TEXT_CHARS = 1_000_000
    const val MAX_RECOVERED_PARAGRAPHS = 20_000

    data class ExtractedDocx(
        val title: String?,
        val paragraphs: List<String>,
        val plainText: String,
        val wordCount: Int,
        val paragraphCount: Int,
        val truncated: Boolean = false
    )

    class ExtractionLimitException(message: String) : IOException(message)

    fun extractText(file: File): ExtractedDocx {
        if (!file.exists() || !file.canRead()) {
            return ExtractedDocx(null, emptyList(), "", 0, 0)
        }
        return file.inputStream().use { extractText(it, file.name) }
    }

    fun extractText(bytes: ByteArray, fileName: String = "document.docx"): ExtractedDocx =
        ByteArrayInputStream(bytes).use { extractText(it, fileName) }

    fun extractText(inputStream: InputStream, fileName: String = "document.docx"): ExtractedDocx {
        val documentXmlBytes = ZipInputStream(inputStream).use { zip ->
            var scannedBytes = 0L
            var found: ByteArray? = null
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    found = readEntryBounded(zip, MAX_DOCUMENT_XML_BYTES)
                    break
                }
                scannedBytes += drainEntryBounded(zip, MAX_ZIP_SCAN_BYTES - scannedBytes)
                if (scannedBytes > MAX_ZIP_SCAN_BYTES) {
                    throw ExtractionLimitException("DOCX recovery stopped: ZIP scan exceeds the safety limit.")
                }
                entry = zip.nextEntry
            }
            found
        } ?: return ExtractedDocx(fileName, emptyList(), "", 0, 0)

        val xml = String(documentXmlBytes, Charsets.UTF_8)
        val (paragraphs, truncated) = parseParagraphsFromXml(xml)
        val plainText = paragraphs.joinToString("\n\n")
        val wordCount = plainText.split(Regex("\\s+")).count { it.isNotBlank() }
        val title = paragraphs.firstOrNull { it.isNotBlank() } ?: fileName

        return ExtractedDocx(
            title = title,
            paragraphs = paragraphs,
            plainText = plainText,
            wordCount = wordCount,
            paragraphCount = paragraphs.size,
            truncated = truncated
        )
    }

    /** Drain an unneeded ZIP entry without allowing ZipInputStream to inflate it unboundedly. */
    private fun drainEntryBounded(input: InputStream, limit: Long): Long {
        if (limit < 0) throw ExtractionLimitException("DOCX recovery stopped: ZIP scan exceeds the safety limit.")
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return total
            total += read
            if (total > limit) {
                throw ExtractionLimitException("DOCX recovery stopped: ZIP scan exceeds the safety limit.")
            }
        }
    }

    /** Read one inflated ZIP entry without ever retaining more than the configured limit + one chunk. */
    private fun readEntryBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size().toLong() + read > limit) {
                throw ExtractionLimitException(
                    "DOCX recovery stopped: word/document.xml exceeds the ${limit / (1024 * 1024)} MiB safety limit."
                )
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun parseParagraphsFromXml(xml: String): Pair<List<String>, Boolean> {
        val result = ArrayList<String>()
        val paragraphRegex = Regex("<w:p(?:\\s[^>]*)?>(.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)
        val tokenRegex = Regex(
            "<w:t(?:\\s[^>]*)?>(.*?)</w:t>|<w:br(?:\\s[^>]*)?/>|<w:tab(?:\\s[^>]*)?/>",
            RegexOption.DOT_MATCHES_ALL
        )
        var totalChars = 0
        var truncated = false

        for (paragraph in paragraphRegex.findAll(xml)) {
            if (result.size >= MAX_RECOVERED_PARAGRAPHS) {
                truncated = true
                break
            }
            val textBuilder = StringBuilder()
            for (token in tokenRegex.findAll(paragraph.groupValues[1])) {
                val raw = token.value
                val text = when {
                    token.groups[1] != null -> unescapeXml(token.groupValues[1])
                    raw.startsWith("<w:br") -> "\n"
                    else -> "\t"
                }
                val remaining = MAX_RECOVERED_TEXT_CHARS - totalChars - textBuilder.length
                if (text.length > remaining) {
                    if (remaining > 0) textBuilder.append(text, 0, remaining)
                    truncated = true
                    break
                }
                textBuilder.append(text)
            }
            val text = textBuilder.toString().trim()
            if (text.isNotEmpty()) {
                result.add(text)
                totalChars += text.length
            }
            if (truncated || totalChars >= MAX_RECOVERED_TEXT_CHARS) {
                truncated = true
                break
            }
        }
        return result to truncated
    }

    private fun unescapeXml(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}
