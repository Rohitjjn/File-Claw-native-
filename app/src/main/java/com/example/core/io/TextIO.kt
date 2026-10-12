package com.example.core.io

import com.example.core.format.TextHeuristics
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

data class DetectedEncoding(val charset: Charset, val bomLength: Int)

object TextEncoding {

    private val CP1252: Charset = try {
        Charset.forName("windows-1252")
    } catch (e: Exception) {
        Charsets.ISO_8859_1
    }

    /** Maps a settings value ("Auto", "UTF-8", ...) to a charset; null means "detect". */
    fun resolve(requested: String): Charset? {
        if (requested.equals("Auto", ignoreCase = true)) return null
        return try {
            Charset.forName(requested)
        } catch (e: Exception) {
            null
        }
    }

    /** Detects encoding from the first bytes of a file: BOM, then UTF-8 validity, then UTF-16 pattern. */
    fun detect(head: ByteArray, n: Int = head.size): DetectedEncoding {
        val len = minOf(n, head.size)
        fun at(i: Int) = head[i].toInt() and 0xFF
        if (len >= 4 && at(0) == 0xFF && at(1) == 0xFE && at(2) == 0 && at(3) == 0) return DetectedEncoding(Charset.forName("UTF-32LE"), 4)
        if (len >= 4 && at(0) == 0 && at(1) == 0 && at(2) == 0xFE && at(3) == 0xFF) return DetectedEncoding(Charset.forName("UTF-32BE"), 4)
        if (len >= 3 && at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF) return DetectedEncoding(Charsets.UTF_8, 3)
        if (len >= 2 && at(0) == 0xFE && at(1) == 0xFF) return DetectedEncoding(Charsets.UTF_16BE, 2)
        if (len >= 2 && at(0) == 0xFF && at(1) == 0xFE) return DetectedEncoding(Charsets.UTF_16LE, 2)
        if (len >= 4) {
            var evenNul = 0
            var oddNul = 0
            for (i in 0 until len / 2) {
                if (head[2 * i].toInt() == 0) evenNul++
                if (head[2 * i + 1].toInt() == 0) oddNul++
            }
            val pairs = len / 2
            if (oddNul > pairs * 0.3 && evenNul == 0) return DetectedEncoding(Charsets.UTF_16LE, 0)
            if (evenNul > pairs * 0.3 && oddNul == 0) return DetectedEncoding(Charsets.UTF_16BE, 0)
        }
        return if (TextHeuristics.isValidUtf8(head, len)) DetectedEncoding(Charsets.UTF_8, 0) else DetectedEncoding(CP1252, 0)
    }

    fun bomBytes(charset: Charset): ByteArray = when (charset.name().uppercase()) {
        "UTF-8" -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        "UTF-16LE" -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        "UTF-16BE" -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        else -> ByteArray(0)
    }
}

/**
 * Result of loading a text file for display or editing.
 * [truncated] is a separate flag: the text itself never contains a "[truncated]" marker, so
 * saving what was loaded cannot silently destroy the rest of the file.
 */
data class TextLoadResult(
    val text: String,
    val charset: Charset,
    val hadBom: Boolean,
    val truncated: Boolean,
    val totalBytes: Long
) {
    /** Editing a truncated view would overwrite the file with a fragment, so it is never allowed. */
    val editable: Boolean get() = !truncated
}

class TextLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

object TextLoader {

    const val DEFAULT_MAX_CHARS = 2_000_000
    private const val HEAD_BYTES = 8192

    /** Loads at most [maxChars] characters. Throws [TextLoadException] instead of returning error text. */
    fun load(file: File, requested: String = "Auto", maxChars: Int = DEFAULT_MAX_CHARS): TextLoadResult {
        if (!file.isFile) throw TextLoadException("Not a readable file")
        try {
            val total = file.length()
            val head = ByteArray(HEAD_BYTES)
            var n = 0
            file.inputStream().use { input ->
                while (n < head.size) {
                    val r = input.read(head, n, head.size - n)
                    if (r < 0) break
                    n += r
                }
            }
            val detected = TextEncoding.detect(head, n)
            val explicit = TextEncoding.resolve(requested)
            val charset = explicit ?: detected.charset
            val hadBom = detected.bomLength > 0 && (explicit == null || explicit == detected.charset)

            val sb = StringBuilder(minOf(maxChars, (total.coerceAtMost(Int.MAX_VALUE.toLong())).toInt() + 16))
            var truncated = false
            InputStreamReader(file.inputStream().buffered(64 * 1024), charset).use { reader ->
                val buf = CharArray(16 * 1024)
                while (true) {
                    val r = reader.read(buf)
                    if (r < 0) break
                    val room = maxChars - sb.length
                    if (r > room) {
                        sb.append(buf, 0, room)
                        truncated = true
                        break
                    }
                    sb.append(buf, 0, r)
                }
            }
            if (sb.isNotEmpty() && sb[0] == '\uFEFF') sb.deleteCharAt(0)
            if (truncated && sb.isNotEmpty() && Character.isHighSurrogate(sb[sb.length - 1])) sb.setLength(sb.length - 1)
            return TextLoadResult(sb.toString(), charset, hadBom, truncated, total)
        } catch (e: TextLoadException) {
            throw e
        } catch (e: Exception) {
            throw TextLoadException("Could not read file: ${e.javaClass.simpleName}", e)
        }
    }
}

/** Atomic text saving that keeps the original charset and BOM. */
object TextSaver {
    fun save(file: File, text: String, charset: Charset = Charsets.UTF_8, writeBom: Boolean = false) {
        val body = text.toByteArray(charset)
        AtomicFileWriter.write(file) { out ->
            if (writeBom) out.write(TextEncoding.bomBytes(charset))
            out.write(body)
        }
    }
}

/** One page of a very large text file, aligned to character and (when possible) line boundaries. */
data class TextWindow(val text: String, val startByte: Long, val endByte: Long, val hasMore: Boolean, val hasPrevious: Boolean)

/**
 * Reads a byte range of a huge file without loading the rest. Intended for UTF-8 / single-byte text:
 * the start is advanced past any UTF-8 continuation bytes and the end is trimmed back to the last
 * newline so pages never split a character or (normally) a line.
 */
object TextWindowReader {

    fun read(file: File, startByte: Long, maxBytes: Int, charset: Charset = Charsets.UTF_8): TextWindow {
        require(startByte >= 0 && maxBytes > 0) { "bad window" }
        val total = file.length()
        if (startByte >= total) return TextWindow("", total, total, hasMore = false, hasPrevious = total > 0)
        val want = minOf(maxBytes.toLong() + 4, total - startByte).toInt()
        val buf = ByteArray(want)
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(startByte)
            raf.readFully(buf)
        }
        var from = 0
        if (charset == Charsets.UTF_8) {
            while (from < buf.size && from < 4 && startByte > 0 && (buf[from].toInt() and 0xC0) == 0x80) from++
        }
        var to = minOf(buf.size, from + maxBytes)
        val reachedEof = startByte + to >= total
        if (!reachedEof) {
            var nl = to - 1
            while (nl > from && buf[nl] != '\n'.code.toByte()) nl--
            if (nl > from) to = nl + 1 else if (charset == Charsets.UTF_8) {
                while (to > from && (buf[to - 1].toInt() and 0xC0) == 0x80) to--
                if (to > from && (buf[to - 1].toInt() and 0xC0) == 0xC0) to--
            }
        }
        val text = String(buf, from, to - from, charset)
        val start = startByte + from
        val end = startByte + to
        return TextWindow(text, start, end, hasMore = end < total, hasPrevious = start > 0)
    }
}
