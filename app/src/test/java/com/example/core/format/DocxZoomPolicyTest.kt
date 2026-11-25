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
    fun `WebView scale converts density baseline into user zoom`() {
        assertEquals(1f, DocxZoomPolicy.fromWebViewScale(2.625f, 2.625f), 0.001f)
        assertEquals(5f, DocxZoomPolicy.fromWebViewScale(13.125f, 2.625f), 0.001f)
        assertEquals(0.5f, DocxZoomPolicy.fromWebViewScale(1.3125f, 2.625f), 0.001f)
        assertEquals(1f, DocxZoomPolicy.fromWebViewScale(2f, 0f), 0.001f)
    }

    @Test
    fun `WebView scale normalizes to the measured fit-width baseline`() {
        assertEquals(1f, DocxZoomPolicy.fromWebViewScale(1.11f, 2.625f, 0.423f), 0.01f)
        assertEquals(2f, DocxZoomPolicy.fromWebViewScale(2.22f, 2.625f, 0.423f), 0.02f)
        assertEquals(0.5f, DocxZoomPolicy.fromWebViewScale(0.555f, 2.625f, 0.423f), 0.01f)
        assertEquals(1f, DocxZoomPolicy.fromWebViewScale(2.625f, 2.625f, 0f), 0.001f)
    }

    @Test
    fun `double tap toggles between fit-width and 200 percent steps`() {
        assertEquals(2f, DocxZoomPolicy.doubleTapTarget(1f))
        assertEquals(1f, DocxZoomPolicy.doubleTapTarget(1.5f))
        assertEquals(1f, DocxZoomPolicy.doubleTapTarget(2f))
        assertEquals(1f, DocxZoomPolicy.doubleTapTarget(5f))
        assertEquals(2f, DocxZoomPolicy.doubleTapTarget(0.5f))
    }

    @Test
    fun `percent conversions round trip`() {
        assertEquals(1.00f, DocxZoomPolicy.fromPercent(100))
        assertEquals(125, DocxZoomPolicy.toPercent(1.25f))
        assertEquals(500, DocxZoomPolicy.toPercent(9f))
        assertTrue(DocxZoomPolicy.toPercent(DocxZoomPolicy.fromPercent(137)) == 137)
    }
}
