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
    fun `coerce clamps to 50-1000 percent`() {
        assertEquals(0.50f, DocxZoomPolicy.coerce(0.1f))
        assertEquals(10.0f, DocxZoomPolicy.coerce(12f))
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
    fun `double tap toggles between fit-width and 400 percent steps`() {
        assertEquals(4f, DocxZoomPolicy.doubleTapTarget(1f))
        assertEquals(1f, DocxZoomPolicy.doubleTapTarget(1.5f))
        assertEquals(1f, DocxZoomPolicy.doubleTapTarget(2f))
        assertEquals(1f, DocxZoomPolicy.doubleTapTarget(5f))
        assertEquals(4f, DocxZoomPolicy.doubleTapTarget(0.5f))
    }

    @Test
    fun `double tap switches at the stable reading zoom threshold`() {
        assertEquals(4f, DocxZoomPolicy.doubleTapTarget(1.49f))
        assertEquals(1f, DocxZoomPolicy.doubleTapTarget(1.5f))
        assertEquals(4f, DocxZoomPolicy.doubleTapTarget(Float.NaN))
    }

    @Test
    fun `percent conversions round trip`() {
        assertEquals(1.00f, DocxZoomPolicy.fromPercent(100))
        assertEquals(125, DocxZoomPolicy.toPercent(1.25f))
        assertEquals(1000, DocxZoomPolicy.toPercent(12f))
        assertTrue(DocxZoomPolicy.toPercent(DocxZoomPolicy.fromPercent(137)) == 137)
    }
}
