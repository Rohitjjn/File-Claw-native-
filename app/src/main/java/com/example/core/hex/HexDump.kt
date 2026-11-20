package com.example.core.hex

import java.io.File
import java.io.RandomAccessFile

/**
 * Paged hex dump renderer. Pure Kotlin (no Android imports) so it unit-tests on the plain JVM.
 *
 * The viewer used to render only the first 4 KB of a file with no way to see anything else.
 * This object renders fixed-size [WINDOW_BYTES] windows with absolute base offsets; callers stream
 * one window at a time, so even multi-gigabyte binaries stay bounded in memory.
 */
object HexDump {

    /** Bytes per rendered line. */
    const val ROW_BYTES = 16

    /** Window size in bytes: 64 KB = 4096 lines per window. */
    const val WINDOW_BYTES = 64 * 1024

    /**
     * One rendered window of a file.
     *
     * @param index       0-based window number
     * @param totalBytes  total size of the file in bytes (for the window counter)
     * @param offset      absolute offset of the first byte in this window
     * @param hexRows     one line per [ROW_BYTES] bytes, `"%08X:"` offset + 16 hex pairs
     * @param asciiRows   printable-ASCII gutter for each line; non-printable bytes become `.`
     */
    data class HexWindow(
        val index: Int,
        val totalBytes: Long,
        val offset: Long,
        val hexRows: List<String>,
        val asciiRows: List<String>,
        val byteCount: Int
    ) {
        val bytesShown: Long get() = byteCount.toLong()
        val endOffset: Long get() = offset + bytesShown
        val isLast: Boolean get() = endOffset >= totalBytes
    }

    fun windowCount(totalBytes: Long): Int =
        if (totalBytes <= 0L) 1 else ((totalBytes + WINDOW_BYTES - 1) / WINDOW_BYTES).toInt()

    fun clampIndex(index: Int, totalBytes: Long): Int =
        index.coerceIn(0, windowCount(totalBytes) - 1)

    /**
     * Parses a user-typed offset: `0x`/`0X` hex prefix, or decimal. Returns null on junk or a
     * negative result. Whitespace is tolerated.
     */
    fun parseOffset(raw: String): Long? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val parsed = if (s.startsWith("0x", ignoreCase = true)) {
            s.substring(2).toLongOrNull(16)
        } else {
            // Plain decimal by default; a trailing 'h' is accepted as hex too (e.g. "1A00h").
            if (s.length > 1 && s.endsWith("h", ignoreCase = true)) s.dropLast(1).toLongOrNull(16)
            else s.toLongOrNull(10)
        }
        return parsed?.takeIf { it >= 0L }
    }

    /** Renders [bytes] (already read, starting at absolute [baseOffset]) into a [HexWindow]. */
    fun render(bytes: ByteArray, baseOffset: Long, totalBytes: Long): HexWindow {
        require(baseOffset >= 0L) { "baseOffset must be >= 0" }
        val rows = (bytes.size + ROW_BYTES - 1) / ROW_BYTES
        val hexLines = ArrayList<String>(rows)
        val asciiLines = ArrayList<String>(rows)
        var rowStart = 0
        while (rowStart < bytes.size) {
            val rowLen = minOf(ROW_BYTES, bytes.size - rowStart)
            val hex = StringBuilder(12 + ROW_BYTES * 4)
            val ascii = StringBuilder(ROW_BYTES)
            hex.append(String.format("%08X: ", baseOffset + rowStart))
            for (i in 0 until ROW_BYTES) {
                if (i < rowLen) {
                    val b = bytes[rowStart + i].toInt() and 0xFF
                    hex.append(String.format("%02X ", b))
                    // Printable ASCII is 0x20 ' ' .. 0x7E '~'.
                    ascii.append(if (b in 0x20..0x7E) b.toChar() else '.')
                } else {
                    hex.append("   ")
                }
                if (i == 7) hex.append(' ')
            }
            hexLines.add(hex.toString())
            asciiLines.add(ascii.toString())
            rowStart += rowLen
        }
        val index = if (totalBytes <= 0L) 0 else (baseOffset / WINDOW_BYTES).toInt()
        return HexWindow(index, totalBytes, baseOffset, hexLines, asciiLines, bytes.size)
    }

    /** Loads one window of [file], clamping [index] to a valid range. Empty file → 0-row window. */
    fun loadWindow(file: File, index: Int): HexWindow {
        val total = file.length()
        val idx = clampIndex(index, total)
        val offset = idx.toLong() * WINDOW_BYTES
        val want = minOf(WINDOW_BYTES.toLong(), (total - offset).coerceAtLeast(0L)).toInt()
        val buf = ByteArray(want)
        var read = 0
        if (want > 0) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                while (read < want) {
                    val r = raf.read(buf, read, want - read)
                    if (r <= 0) break
                    read += r
                }
            }
        }
        val bytes = if (read < want) buf.copyOf(read) else buf
        return render(bytes, offset, total)
    }
}
