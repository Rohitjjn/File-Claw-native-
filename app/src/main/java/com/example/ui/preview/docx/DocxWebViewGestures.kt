package com.example.ui.preview.docx

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.GestureDetector
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import com.example.core.format.DocxZoomPolicy

/** Detects confirmed taps and double taps while leaving multi-touch streams with Chromium. */
internal fun WebView.installDocxGestureListeners(
    isActive: () -> Boolean = { true },
    onSingleTapConfirmed: (MotionEvent) -> Unit,
    onDoubleTap: (MotionEvent, (MotionEvent) -> Unit) -> Unit
) {
    var consumeDoubleTapSequence = false
    var dispatchingSyntheticZoom = false
    val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            if (isActive()) onSingleTapConfirmed(event)
            return true
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            if (isActive()) {
                consumeDoubleTapSequence = true
                onDoubleTap(event) { syntheticEvent ->
                    dispatchingSyntheticZoom = true
                    try {
                        dispatchTouchEvent(syntheticEvent)
                    } finally {
                        dispatchingSyntheticZoom = false
                    }
                }
            }
            return true
        }
    })
    setOnTouchListener { _, event ->
        if (!dispatchingSyntheticZoom) detector.onTouchEvent(event)
        if (event.pointerCount > 1) {
            consumeDoubleTapSequence = false
            false
        } else {
            val consume = consumeDoubleTapSequence
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                consumeDoubleTapSequence = false
            }
            consume
        }
    }
}

/** A hyperlink tap stays clickable and never toggles the preview's focus mode. */
internal fun WebView.dispatchSingleTapIfNotLink(
    event: MotionEvent,
    onTap: () -> Unit,
    isActive: () -> Boolean = { true },
    onResolved: () -> Unit = {}
) {
    val density = resources.displayMetrics.density
    val pageZoom = DocxZoomPolicy.fromWebViewScale(scale, density)
    val x = (event.x / (density * pageZoom)).toString()
    val y = (event.y / (density * pageZoom)).toString()
    evaluateJavascript(
        "(function(){var node=document.elementFromPoint($x,$y);return !!(node&&node.closest&&node.closest('a[href]'));})()"
    ) { result ->
        if (isActive()) {
            if (result != "true") onTap()
            onResolved()
        }
    }
}

/** Toggles native Chromium zoom by dispatching a native pinch focused at the double-tap. */
internal fun WebView.animateDocxDoubleTapZoom(
    event: MotionEvent,
    isActive: () -> Boolean = { true },
    dispatchSyntheticEvent: (MotionEvent) -> Unit
) {
    val density = resources.displayMetrics.density
    val tapX = event.x
    val tapY = event.y
    evaluateJavascript("(window.visualViewport ? window.visualViewport.scale : 1).toString()") { result ->
        if (!isActive()) return@evaluateJavascript
        val current = result?.trim('"')?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val target = DocxZoomPolicy.doubleTapTarget(current)
        val factor = target / current
        if (!factor.isFinite() || factor <= 0f || kotlin.math.abs(target - current) < 0.01f) return@evaluateJavascript
        // Chromium's synthetic pinch recognizer applies only five-sixths of the gesture distance ratio.
        val gestureFactor = factor * 1.2f

        // Wait until the second real tap has ended before passing a separate pinch stream to Chromium.
        postDelayed({
            if (!isActive()) return@postDelayed
            val xClearance = minOf(tapX, width - tapX).coerceAtLeast(0f)
            val yClearance = minOf(tapY, height - tapY).coerceAtLeast(0f)
            val horizontalAxis = xClearance >= yClearance
            val clearance = maxOf(xClearance, yClearance)
            val maximumStartDistance = 2f * clearance / gestureFactor
            val startDistance = minOf(80f * density, maximumStartDistance * 0.9f)
                .coerceAtLeast(2f * density)
            val endDistance = startDistance * gestureFactor
            val properties = Array(2) { index ->
                MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER }
            }
            val downTime = SystemClock.uptimeMillis()
            var eventTime = downTime

            fun coordinates(distance: Float): Array<MotionEvent.PointerCoords> = Array(2) { index ->
                MotionEvent.PointerCoords().apply {
                    x = if (horizontalAxis) tapX + (if (index == 0) -distance / 2f else distance / 2f) else tapX
                    y = if (horizontalAxis) tapY else tapY + (if (index == 0) -distance / 2f else distance / 2f)
                    pressure = 1f
                    size = 1f
                }
            }

            fun dispatch(action: Int, count: Int, points: Array<MotionEvent.PointerCoords>) {
                eventTime += 16L
                val synthetic = MotionEvent.obtain(
                    downTime, eventTime, action, count,
                    properties.copyOf(count), points.copyOf(count),
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
                )
                try {
                    dispatchSyntheticEvent(synthetic)
                } finally {
                    synthetic.recycle()
                }
            }

            val initialPoints = coordinates(startDistance)
            dispatch(MotionEvent.ACTION_DOWN, 1, initialPoints)
            dispatch(
                MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                2,
                initialPoints
            )
            var cancelled = false
            ValueAnimator.ofFloat(startDistance, endDistance).apply {
                duration = 220L
                addUpdateListener { animation ->
                    if (!isActive()) cancel()
                    else dispatch(MotionEvent.ACTION_MOVE, 2, coordinates(animation.animatedValue as Float))
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        val points = coordinates(if (cancelled) startDistance else endDistance)
                        if (cancelled || !isActive()) {
                            dispatch(MotionEvent.ACTION_CANCEL, 2, points)
                        } else {
                            dispatch(
                                MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                                2,
                                points
                            )
                            dispatch(MotionEvent.ACTION_UP, 1, points)
                        }
                    }
                })
                start()
            }
        }, 80L)
    }
}
