package com.example.core.format

/**
 * Zoom policy for the DOCX preview engine.
 *
 * Native Android WebView page-scale bounds. Viewport metadata and Chromium
 * own pinch, double-tap, and panning; this policy only sanitizes reported scale.
 * Values are relative factors (0.50 - 5.00), with 1.0 as the initial scale.
 */
object DocxZoomPolicy {

    const val DEFAULT_ZOOM = 1.00f
    const val MIN_ZOOM = 0.50f
    const val MAX_ZOOM = 5.00f
    fun coerce(zoom: Float): Float =
        if (zoom.isFinite()) zoom.coerceIn(MIN_ZOOM, MAX_ZOOM) else DEFAULT_ZOOM

    fun fromPercent(percent: Int): Float = coerce(percent / 100f)

    fun toPercent(zoom: Float): Int = Math.round(coerce(zoom) * 100f)

    /** Default display zoom percent. */
    fun defaultPercent(): Int = 100
}
