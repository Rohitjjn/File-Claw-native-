package com.example.core.format

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxPrintLayoutPolicyTest {
    @Test
    fun `accepts unit scale printable layout when screen and paper widths differ`() {
        assertTrue(DocxPrintLayoutPolicy.isReadyForPrint(
            scale = 1.0,
            viewportWidth = 400.0,
            rootWidth = 400.0,
            pageWidth = 280.0,
            transform = "none"
        ))
    }

    @Test
    fun `rejects preview zoom leaking into print`() {
        assertFalse(DocxPrintLayoutPolicy.isReadyForPrint(
            scale = 0.5,
            viewportWidth = 400.0,
            rootWidth = 400.0,
            pageWidth = 400.0,
            transform = "none"
        ))
    }

    @Test
    fun `rejects missing page geometry and residual transforms`() {
        assertFalse(DocxPrintLayoutPolicy.isReadyForPrint(
            scale = 1.0,
            viewportWidth = 400.0,
            rootWidth = 0.0,
            pageWidth = 100.0,
            transform = "none"
        ))
        assertFalse(DocxPrintLayoutPolicy.isReadyForPrint(
            scale = 1.0,
            viewportWidth = 400.0,
            rootWidth = 400.0,
            pageWidth = 400.0,
            transform = "matrix(0.5, 0, 0, 0.5, 0, 0)"
        ))
    }

    @Test
    fun `rejects non-finite and zero print dimensions`() {
        assertFalse(DocxPrintLayoutPolicy.isReadyForPrint(
            scale = Double.NaN,
            viewportWidth = 400.0,
            rootWidth = 400.0,
            pageWidth = 400.0,
            transform = "none"
        ))
        assertFalse(DocxPrintLayoutPolicy.isReadyForPrint(
            scale = 1.0,
            viewportWidth = 0.0,
            rootWidth = 400.0,
            pageWidth = 400.0,
            transform = "none"
        ))
    }
}
