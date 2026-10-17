package com.example.core.format

/**
 * Zoom policy for the DOCX preview (v1.3.0).
 *
 * - Documents now open at [DEFAULT_ZOOM] (40%) instead of 100%.
 * - Pinch gestures multiply the current zoom by the gesture delta.
 * - Double-tap toggles between fit (40%) and full size (100%).
 * All values are expressed as CSS zoom factors (0.25 - 5.0).
 */
object DocxZoomPolicy {

    const val DEFAULT_ZOOM = 0.40f
    const val MIN_ZOOM = 0.25f
    const val MAX_ZOOM = 5.00f
    const val FULL_SIZE_ZOOM = 1.00f

    fun coerce(zoom: Float): Float =
        zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /** Applies a pinch gesture delta to the current zoom, clamped and de-noised. */
    fun applyPinch(current: Float, delta: Float): Float {
        if (!delta.isFinite() || delta <= 0f) return coerce(current)
        val next = current * delta
        val rounded = Math.round(next * 1000f) / 1000f
        return coerce(rounded)
    }

    /** Double-tap toggle: fit-width (40%) <-> actual size (100%). */
    fun toggleDoubleTap(current: Float): Float =
        if (current > (DEFAULT_ZOOM + FULL_SIZE_ZOOM) / 2f) DEFAULT_ZOOM else FULL_SIZE_ZOOM

    fun fromPercent(percent: Int): Float = coerce(percent / 100f)

    fun toPercent(zoom: Float): Int = Math.round(coerce(zoom) * 100f)

    /** Persisted zoom percent falls back to the shipped default (40%). */
    fun defaultPercent(): Int = Math.round(DEFAULT_ZOOM * 100f)
}
