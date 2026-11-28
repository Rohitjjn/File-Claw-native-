package com.example.core.format

import kotlin.math.abs

/** Validates that the isolated renderer is printable without confusing screen and paper widths. */
object DocxPrintLayoutPolicy {
    private const val UNIT_SCALE_TOLERANCE = 0.01

    fun isReadyForPrint(
        scale: Double,
        viewportWidth: Double,
        rootWidth: Double,
        pageWidth: Double,
        transform: String
    ): Boolean {
        if (!listOf(scale, viewportWidth, rootWidth, pageWidth).all { it.isFinite() }) return false
        if (abs(scale - 1.0) > UNIT_SCALE_TOLERANCE) return false
        if (viewportWidth <= 0.0 || rootWidth <= 0.0 || pageWidth <= 0.0) return false
        // The detached WebView is measured to the phone display, while PrintManager lays it out
        // against A4's printable area. Equality with the screen viewport is not a print invariant.
        return transform == "none"
    }
}
