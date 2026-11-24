package com.example.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxZoomPolicyTest {

    @Test
    fun `default zoom is 100 percent fit-to-width`() {
        assertEquals(1.00f, DocxZoomPolicy.DEFAULT_ZOOM)
        assertEquals(100, DocxZoomPolicy.defaultPercent())
    }

    @Test
    fun `coerce clamps to 50-500 percent`() {
        assertEquals(0.50f, DocxZoomPolicy.coerce(0.1f))
        assertEquals(5.0f, DocxZoomPolicy.coerce(9f))
        assertEquals(1.00f, DocxZoomPolicy.coerce(1.00f))
    }

    @Test
    fun `percent conversions round trip`() {
        assertEquals(1.00f, DocxZoomPolicy.fromPercent(100))
        assertEquals(125, DocxZoomPolicy.toPercent(1.25f))
        assertEquals(500, DocxZoomPolicy.toPercent(9f))
        assertTrue(DocxZoomPolicy.toPercent(DocxZoomPolicy.fromPercent(137)) == 137)
    }
}
