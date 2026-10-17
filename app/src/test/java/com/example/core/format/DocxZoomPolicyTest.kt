package com.example.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxZoomPolicyTest {

    @Test
    fun `default zoom is 40 percent`() {
        assertEquals(0.40f, DocxZoomPolicy.DEFAULT_ZOOM)
        assertEquals(40, DocxZoomPolicy.defaultPercent())
    }

    @Test
    fun `coerce clamps to 25-500 percent`() {
        assertEquals(0.25f, DocxZoomPolicy.coerce(0.1f))
        assertEquals(5.0f, DocxZoomPolicy.coerce(9f))
        assertEquals(0.40f, DocxZoomPolicy.coerce(0.40f))
    }

    @Test
    fun `pinch multiplies and clamps`() {
        assertEquals(0.80f, DocxZoomPolicy.applyPinch(0.40f, 2.0f))
        assertEquals(0.25f, DocxZoomPolicy.applyPinch(0.40f, 0.2f))
        assertEquals(5.0f, DocxZoomPolicy.applyPinch(4.9f, 1.5f))
        // Degenerate deltas leave the value untouched (but coerced).
        assertEquals(0.40f, DocxZoomPolicy.applyPinch(0.40f, Float.NaN))
        assertEquals(0.25f, DocxZoomPolicy.applyPinch(0.10f, 0f))
    }

    @Test
    fun `pinch rounds away float drift`() {
        val zoomed = DocxZoomPolicy.applyPinch(0.40f, 1.3333f)
        assertEquals(0.533f, zoomed)
    }

    @Test
    fun `double tap toggles between fit and full size`() {
        assertEquals(DocxZoomPolicy.FULL_SIZE_ZOOM, DocxZoomPolicy.toggleDoubleTap(0.40f))
        assertEquals(DocxZoomPolicy.DEFAULT_ZOOM, DocxZoomPolicy.toggleDoubleTap(1.0f))
        assertEquals(DocxZoomPolicy.FULL_SIZE_ZOOM, DocxZoomPolicy.toggleDoubleTap(0.25f))
        assertEquals(DocxZoomPolicy.DEFAULT_ZOOM, DocxZoomPolicy.toggleDoubleTap(5.0f))
    }

    @Test
    fun `percent conversions round trip`() {
        assertEquals(0.40f, DocxZoomPolicy.fromPercent(40))
        assertEquals(125, DocxZoomPolicy.toPercent(1.25f))
        assertEquals(500, DocxZoomPolicy.toPercent(9f))
        assertTrue(DocxZoomPolicy.toPercent(DocxZoomPolicy.fromPercent(137)) == 137)
    }
}
