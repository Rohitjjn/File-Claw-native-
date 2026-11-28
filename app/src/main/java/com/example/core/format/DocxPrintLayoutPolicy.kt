package com.example.core.format

import kotlin.math.abs

/** Validates the renderer's preflight before handing DOCX HTML to Android PrintManager. */
object DocxPrintLayoutPolicy {
    private const val UNIT_SCALE_TOLERANCE = 0.01
    private const val WIDTH_TOLERANCE = 0.01

    fun isReadyForPrint(
        scale: Double,
        viewportWidth: Double,
        rootWidth: Double,
        pageWidth: Double,
        transform: String
    ): Boolean {
        if (!listOf(scale, viewportWidth, rootWidth, pageWidth).all { it.isFinite() }) return false
        if (abs(scale - 1.0) > UNIT_SCALE_TOLERANCE || viewportWidth <= 0.0) return false
        if (rootWidth < viewportWidth * (1.0 - WIDTH_TOLERANCE) ||
            pageWidth < viewportWidth * (1.0 - WIDTH_TOLERANCE)
        ) return false
        return transform == "none"
    }
}
