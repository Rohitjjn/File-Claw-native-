package com.example.ui.preview.docx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxZoomAndGestureTest {
    @Test fun fitScaleAndUserZoomAreIndependentAndBounded() {
        assertEquals(.5f, DocxZoomAndGesture.fitScale(500f, 1000f), .001f)
        val scale = DocxZoomAndGesture.viewport(500f, 1000f, 2f)
        assertEquals(1f, scale.effectiveScale, .001f)
        assertEquals(1000f, scale.scaledPageWidth, .01f)
        assertEquals(5f, DocxZoomAndGesture.coerce(10f), .001f)
        assertEquals(1f, DocxZoomAndGesture.coerce(Float.NaN), .001f)
    }

    @Test fun focalAnchorRemainsAtSameViewportCoordinateAfterScaleAndCenterChange() {
        val nextScroll = DocxZoomAndGesture.anchoredScroll(
            oldScroll = 100f, viewportOffset = 30f, documentOriginBefore = 40f,
            oldScale = 2f, documentOriginAfter = 60f, newScale = 3f
        )
        assertEquals(165f, nextScroll, .001f)
        assertEquals(30f, 60f + ((100f + 30f - 40f) / 2f) * 3f - nextScroll, .001f)
    }

    @Test fun pinchAndDoubleTapRespectLimits() {
        assertEquals(1.5f, DocxZoomAndGesture.applyPinch(1f, 1.5f), .001f)
        assertEquals(5f, DocxZoomAndGesture.applyPinch(4f, 2f), .001f)
        assertEquals(2f, DocxZoomAndGesture.doubleTap(1f), .001f)
        assertEquals(1f, DocxZoomAndGesture.doubleTap(2f), .001f)
    }
}
