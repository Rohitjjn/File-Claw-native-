package com.example.ui.preview.docx

import kotlin.math.roundToInt

/** Pure math shared by toolbar zoom, fit layout and the JS gesture adapter. */
object DocxZoomAndGesture {
    const val MIN_USER_ZOOM = 0.5f
    const val MAX_USER_ZOOM = 5f
    const val DEFAULT_USER_ZOOM = 1f

    data class ViewportScale(
        val fitScale: Float,
        val userZoom: Float,
        val effectiveScale: Float,
        val scaledPageWidth: Float
    )

    fun fitScale(availableWidth: Float, pageWidth: Float): Float {
        if (!availableWidth.isFinite() || !pageWidth.isFinite() || availableWidth <= 0f || pageWidth <= 0f) return 1f
        return (availableWidth / pageWidth).coerceIn(0.05f, 1f)
    }

    fun viewport(availableWidth: Float, pageWidth: Float, userZoom: Float): ViewportScale {
        val fit = fitScale(availableWidth, pageWidth)
        val user = coerce(userZoom)
        val effective = fit * user
        return ViewportScale(fit, user, effective, pageWidth * effective)
    }

    fun coerce(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MIN_USER_ZOOM, MAX_USER_ZOOM) else DEFAULT_USER_ZOOM

    fun applyPinch(current: Float, delta: Float): Float =
        if (!delta.isFinite() || delta <= 0f) coerce(current) else coerce((current * delta * 1000f).roundToInt() / 1000f)

    fun anchoredScroll(
        oldScroll: Float,
        viewportOffset: Float,
        documentOriginBefore: Float,
        oldScale: Float,
        documentOriginAfter: Float,
        newScale: Float
    ): Float {
        if (!listOf(oldScroll, viewportOffset, documentOriginBefore, oldScale, documentOriginAfter, newScale).all { it.isFinite() } ||
            oldScale <= 0f || newScale <= 0f
        ) return oldScroll.coerceAtLeast(0f)
        val documentCoordinate = (oldScroll + viewportOffset - documentOriginBefore) / oldScale
        return (documentOriginAfter + documentCoordinate * newScale - viewportOffset).coerceAtLeast(0f)
    }

    fun doubleTap(current: Float): Float = if (coerce(current) > 1.4f) 1f else 2f

    fun percent(value: Float): Int = (coerce(value) * 100f).roundToInt()
}
