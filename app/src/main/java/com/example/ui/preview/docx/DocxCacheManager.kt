package com.example.ui.preview.docx

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/** A bounded, atomic, least-recently-used cache of app-local DOCX copies. */
class DocxCacheManager(
    private val cacheDirectory: File,
    private val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
    private val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES
) {
    data class CachedDocument(val file: File, val cacheHit: Boolean)

    private val lock = Any()

    init {
        require(maxCacheBytes >= 0L)
        require(maxEntryBytes > 0L)
    }

    @Throws(IOException::class)
    fun getOrCopy(source: File): CachedDocument = synchronized(lock) {
        if (!source.isFile || !source.canRead()) throw IOException("DOCX source is missing or unreadable")
        val canonicalSource = source.canonicalFile
        if (isInsideCache(canonicalSource)) return@synchronized CachedDocument(canonicalSource, cacheHit = true)

        val size = canonicalSource.length()
        if (size <= 0L) throw IOException("DOCX source is empty")
        if (size > maxEntryBytes || size > maxCacheBytes) return@synchronized CachedDocument(canonicalSource, cacheHit = false)
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) throw IOException("DOCX cache could not be created")

        val key = sha256(canonicalSource.path)
        val target = File(cacheDirectory, "$key.docx")
        val metadata = File(cacheDirectory, "$key.meta")
        val fingerprint = "$size:${canonicalSource.lastModified()}"
        if (target.isFile && target.length() == size && metadata.isFile && metadata.readText() == fingerprint) {
            target.setLastModified(System.currentTimeMillis())
            return@synchronized CachedDocument(target, cacheHit = true)
        }

        target.delete()
        metadata.delete()
        val temporary = File(cacheDirectory, "$key.tmp")
        temporary.delete()
        var copied = 0L
        try {
            BufferedInputStream(FileInputStream(canonicalSource)).use { input ->
                FileOutputStream(temporary).use { rawOutput ->
                    val output = BufferedOutputStream(rawOutput)
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > maxEntryBytes || copied > maxCacheBytes) {
                            throw IOException("DOCX cache copy exceeded its size limit")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                    rawOutput.fd.sync()
                }
            }
            if (copied != size || canonicalSource.length() != size) {
                throw IOException("DOCX source changed while it was being cached")
            }
            if (!temporary.renameTo(target)) throw IOException("DOCX cache file could not be finalized")
            val metadataTemporary = File(cacheDirectory, "$key.meta.tmp")
            metadataTemporary.writeText(fingerprint)
            if (!metadataTemporary.renameTo(metadata)) {
                metadataTemporary.delete()
                target.delete()
                throw IOException("DOCX cache metadata could not be finalized")
            }
            target.setLastModified(System.currentTimeMillis())
            evictLeastRecentlyUsed(except = target)
            CachedDocument(target, cacheHit = false)
        } catch (error: Exception) {
            temporary.delete()
            target.delete()
            metadata.delete()
            if (error is IOException) throw error
            throw IOException("DOCX cache write failed", error)
        }
    }

    fun remove(source: File) = synchronized(lock) {
        val key = sha256(source.canonicalFile.path)
        File(cacheDirectory, "$key.docx").delete()
        File(cacheDirectory, "$key.meta").delete()
        File(cacheDirectory, "$key.tmp").delete()
        Unit
    }

    private fun isInsideCache(file: File): Boolean = runCatching {
        file.canonicalPath.startsWith(cacheDirectory.canonicalPath + File.separator)
    }.getOrDefault(false)

    private fun evictLeastRecentlyUsed(except: File) {
        val entries = cacheDirectory.listFiles { file -> file.isFile && file.extension == "docx" }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
        var bytes = entries.sumOf { it.length() }
        for (entry in entries) {
            if (bytes <= maxCacheBytes) break
            if (entry == except) continue
            val removed = entry.length()
            entry.delete()
            File(cacheDirectory, "${entry.nameWithoutExtension}.meta").delete()
            bytes -= removed
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        const val DEFAULT_MAX_CACHE_BYTES = 50L * 1024 * 1024
        const val DEFAULT_MAX_ENTRY_BYTES = 25L * 1024 * 1024
        private const val COPY_BUFFER_BYTES = 16 * 1024
    }
}
