package com.example.ui.preview.docx

import com.example.core.format.DocxZoomPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class DocxNativeZoomPolicyTest {
    @Test fun nativeScaleStartsAtOneHundredPercent() {
        assertEquals(1f, DocxZoomPolicy.DEFAULT_ZOOM)
        assertEquals(100, DocxZoomPolicy.defaultPercent())
        assertEquals(1f, DocxZoomPolicy.coerce(1f))
    }

    @Test fun nativeChromiumScalesAreSanitizedToViewportBounds() {
        assertEquals(.5f, DocxZoomPolicy.coerce(.1f), .001f)
        assertEquals(5f, DocxZoomPolicy.coerce(9f), .001f)
        assertEquals(1f, DocxZoomPolicy.coerce(Float.NaN), .001f)
        assertEquals(1f, DocxZoomPolicy.coerce(Float.POSITIVE_INFINITY), .001f)
    }
}
