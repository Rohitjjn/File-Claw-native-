package com.example.ui.preview.docx

import com.example.core.format.DocxPreviewPolicy
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/** Prepares and validates a DOCX off the main thread before the WebView receives its local stream. */
class DocxDocumentLoader(private val cacheManager: DocxCacheManager) {
    data class PreparedDocument(
        val file: File,
        val sourceBytes: Long,
        val cacheHit: Boolean,
        val largeFileWarning: Boolean
    )

    enum class FailureCode { MISSING_FILE, EMPTY_FILE, FILE_TOO_LARGE, INVALID_OR_ENCRYPTED_PACKAGE, RESOURCE_LIMIT }

    class LoadException(val code: FailureCode, message: String) : IOException(message)

    fun prepare(source: File): PreparedDocument {
        if (!source.isFile || !source.canRead()) {
            throw LoadException(FailureCode.MISSING_FILE, "The DOCX file is missing or cannot be read.")
        }
        val sourceBytes = source.length()
        if (sourceBytes <= 0L) throw LoadException(FailureCode.EMPTY_FILE, "The DOCX file is empty.")
        if (DocxPreviewPolicy.route(sourceBytes) is DocxPreviewPolicy.Route.LimitExceeded) {
            throw LoadException(FailureCode.FILE_TOO_LARGE, "This DOCX exceeds the 25 MiB formatted-preview limit.")
        }

        val cached = runCatching { cacheManager.getOrCopy(source) }
            .getOrElse { DocxCacheManager.CachedDocument(source, cacheHit = false) }
        try {
            validatePackage(cached.file)
        } catch (error: LoadException) {
            if (cached.file != source) cacheManager.remove(source)
            throw error
        }
        return PreparedDocument(
            file = cached.file,
            sourceBytes = sourceBytes,
            cacheHit = cached.cacheHit,
            largeFileWarning = hasLargeFileWarning(sourceBytes)
        )
    }

    private fun validatePackage(file: File) {
        try {
            ZipFile(file).use { archive ->
                var entryCount = 0
                var declaredExpandedBytes = 0L
                val entries = archive.entries()
                var hasContentTypes = false
                var documentEntry: java.util.zip.ZipEntry? = null
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++
                    if (entryCount > MAX_ENTRY_COUNT) {
                        throw LoadException(FailureCode.RESOURCE_LIMIT, "DOCX package has too many ZIP entries.")
                    }
                    if (entry.size > 0L) {
                        declaredExpandedBytes += entry.size
                        if (declaredExpandedBytes > MAX_DECLARED_EXPANDED_BYTES) {
                            throw LoadException(FailureCode.RESOURCE_LIMIT, "DOCX package exceeds the expanded-size safety limit.")
                        }
                    }
                    if (entry.name == "[Content_Types].xml") hasContentTypes = true
                    if (entry.name == "word/document.xml") documentEntry = entry
                }
                val document = documentEntry
                if (!hasContentTypes || document == null || document.isDirectory) {
                    throw LoadException(
                        FailureCode.INVALID_OR_ENCRYPTED_PACKAGE,
                        "This file is not a valid Word document package."
                    )
                }
                if (document.size > MAX_DOCUMENT_XML_BYTES) {
                    throw LoadException(FailureCode.RESOURCE_LIMIT, "The main DOCX XML part exceeds the safety limit.")
                }
                archive.getInputStream(document).use { input ->
                    val buffer = ByteArray(READ_BUFFER_BYTES)
                    var expanded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        expanded += count
                        if (expanded > MAX_DOCUMENT_XML_BYTES) {
                            throw LoadException(FailureCode.RESOURCE_LIMIT, "The main DOCX XML part exceeds the safety limit.")
                        }
                    }
                }
            }
        } catch (error: LoadException) {
            throw error
        } catch (error: Exception) {
            throw LoadException(
                FailureCode.INVALID_OR_ENCRYPTED_PACKAGE,
                "This DOCX is damaged, unsupported, or password protected."
            )
        }
    }

    companion object {
        const val LARGE_FILE_WARNING_BYTES = 15L * 1024 * 1024
        const val MAX_DOCUMENT_XML_BYTES = 16L * 1024 * 1024
        const val MAX_DECLARED_EXPANDED_BYTES = 96L * 1024 * 1024
        const val MAX_ENTRY_COUNT = 20_000
        private const val READ_BUFFER_BYTES = 16 * 1024

        fun hasLargeFileWarning(sourceBytes: Long): Boolean = sourceBytes > LARGE_FILE_WARNING_BYTES
    }
}
