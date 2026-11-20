package com.example.core.hex

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HexDumpTest {
    private lateinit var dir: File

    @Before fun setUp() { dir = Files.createTempDirectory("hex").toFile() }
    @After fun tearDown() { dir.deleteRecursively() }

    private fun file(name: String, bytes: ByteArray) = File(dir, name).also { it.writeBytes(bytes) }

    private fun patternedBytes(size: Int): ByteArray =
        ByteArray(size) { i -> ((i * 31 + 7) and 0xFF).toByte() }

    @Test fun parseOffsetAcceptsHexAndDecimalAndRejectsJunk() {
        assertEquals(0x1A00L, HexDump.parseOffset("0x1a00"))
        assertEquals(0x1A00L, HexDump.parseOffset("0X1A00"))
        assertEquals(0x1A00L, HexDump.parseOffset("1A00h"))
        assertEquals(66048L, HexDump.parseOffset("66048"))
        assertEquals(0L, HexDump.parseOffset("0"))
        assertEquals(0L, HexDump.parseOffset("  0x0  "))
        assertEquals(null, HexDump.parseOffset(""))
        assertEquals(null, HexDump.parseOffset("   "))
        assertEquals(null, HexDump.parseOffset("zz"))
        assertEquals(null, HexDump.parseOffset("-5"))
        assertEquals(null, HexDump.parseOffset("0x"))
        assertEquals(null, HexDump.parseOffset("9999999999999999999999"))
    }

    @Test fun rendersFirstWindowWithAbsoluteOffsetsAndAsciiGutter() {
        val bytes = patternedBytes(48)
        val w = HexDump.render(bytes, 0L, totalBytes = 48L)
        assertEquals(0, w.index)
        assertEquals(listOf(0, 16, 32), w.hexRows.map { it.substringBefore(":").toInt(16) })
        assertTrue(w.hexRows[0].startsWith("00000000: "))
        assertTrue(w.hexRows[1].startsWith("00000010: "))
        assertTrue(w.isLast)
        assertEquals(48, w.bytesShown)
        // Every printable byte shows as itself; non-printables become '.'
        assertEquals(16, w.asciiRows[0].length)
    }

    @Test fun renderPadsShortTailRow() {
        val w = HexDump.render(byteArrayOf(0x41, 0x42), 0L, totalBytes = 2L)
        assertEquals(1, w.hexRows.size)
        assertEquals("AB", w.asciiRows[0])
        val hexBody = w.hexRows[0].substringAfter(": ")
        assertTrue(hexBody.startsWith("41 42 "))
        // Offset alignment: rows are fixed width regardless of tail
        assertEquals(w.hexRows[0].length, HexDump.render(patternedBytes(16), 0L, 16).hexRows[0].length)
    }

    @Test fun loadWindowReturnsFirstWindowForSmallFile() {
        val bytes = patternedBytes(1000)
        val f = file("small.bin", bytes)
        val w = HexDump.loadWindow(f, 0)
        assertEquals(0, w.index)
        assertEquals(1000, w.bytesShown)
        assertEquals(1000L, w.totalBytes)
        assertTrue(w.isLast)
        assertEquals(HexDump.WINDOW_BYTES.toLong().coerceAtMost(1000L), w.bytesShown)
    }

    @Test fun loadWindowClampsOutOfRangeIndex() {
        val bytes = patternedBytes(10)
        val f = file("clamp.bin", bytes)
        val hi = HexDump.loadWindow(f, 999)
        assertEquals(0, hi.index)
        val lo = HexDump.loadWindow(f, -3)
        assertEquals(0, lo.index)
    }

    @Test fun loadWindowSecondWindowHasAbsoluteOffsetAndIsLastFlag() {
        val size = HexDump.WINDOW_BYTES + 17
        val bytes = patternedBytes(size)
        val f = file("big.bin", bytes)
        val first = HexDump.loadWindow(f, 0)
        val second = HexDump.loadWindow(f, 1)
        assertFalse(first.isLast)
        assertEquals(1, second.index)
        assertEquals(HexDump.WINDOW_BYTES.toLong(), second.offset)
        assertEquals(17L, second.bytesShown)
        assertTrue(second.isLast)
        assertEquals(2, HexDump.windowCount(f.length()))
        assertEquals("%08X:".format(HexDump.WINDOW_BYTES), second.hexRows[0].substringBefore(":") + ":")
    }

    @Test fun emptyFileYieldsSingleEmptyWindow() {
        val f = file("empty.bin", ByteArray(0))
        val w = HexDump.loadWindow(f, 0)
        assertEquals(0, w.index)
        assertEquals(0, w.hexRows.size)
        assertEquals(0L, w.totalBytes)
        assertTrue(w.isLast)
        assertEquals(1, HexDump.windowCount(0L))
    }

    @Test fun offsetJumpTargetsRightWindow() {
        val size = HexDump.WINDOW_BYTES * 3 + 5
        val bytes = patternedBytes(size)
        val f = file("jump.bin", bytes)
        // Full middle window is 64 KB; the 5-byte tail lives in window 3.
        val mid = HexDump.loadWindow(f, 2)
        assertEquals(HexDump.WINDOW_BYTES * 2L, mid.offset)
        assertEquals(HexDump.WINDOW_BYTES.toLong(), mid.bytesShown)
        assertEquals(4, HexDump.windowCount(f.length()))
        val target = HexDump.WINDOW_BYTES * 3L
        val w = HexDump.loadWindow(f, 3)
        assertEquals(target, w.offset)
        assertEquals(5L, w.bytesShown)
        assertTrue(w.hexRows[0].startsWith("%08X:".format(target)))
        assertTrue(w.isLast)
    }

    @Test fun asciiGutterKeepsPrintableAndMasksTheRest() {
        val bytes = byteArrayOf(0x41, 0x00, 0x7E, 0x7F, 0x20, 0x41)
        val w = HexDump.render(bytes, 0L, 6L)
        assertEquals("A.~. A", w.asciiRows[0])
    }

}
