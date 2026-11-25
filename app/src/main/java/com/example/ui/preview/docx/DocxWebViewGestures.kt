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
import org.json.JSONObject
import org.json.JSONTokener

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
    val tapX = event.x.toDouble()
    val tapY = event.y.toDouble()

    fun parseMetrics(result: String?): JSONObject? = runCatching {
        JSONObject(JSONTokener(result).nextValue().toString())
    }.getOrNull()

    fun startZoom(metrics: JSONObject) {
        if (!isActive()) return
        val current = metrics.optDouble("scale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val target = DocxZoomPolicy.doubleTapTarget(current)
        val factor = target / current
        if (!factor.isFinite() || factor <= 0f || kotlin.math.abs(target - current) < 0.01f) return

        if (target < current) {
            zoomBy(factor)
            evaluateJavascript(
                "(function(){var s=document.getElementById('scroller');var v=window.visualViewport;var stable=0;var attempts=0;function restore(){if(!s||!v)return;if(Math.abs(v.scale-$target)>0.04){stable=0;if(attempts++<90){requestAnimationFrame(restore);return;}return;}if(stable++<2){requestAnimationFrame(restore);return;}s.classList.remove('zoom-anchor-space');s.scrollLeft=0;s.scrollTop=0;}requestAnimationFrame(restore);})()",
                null
            )
            return
        }

        val initialLeft = metrics.optDouble("left", 0.0)
        val initialTop = metrics.optDouble("top", 0.0)
        val initialOffsetLeft = metrics.optDouble("offsetLeft", 0.0)
        val initialOffsetTop = metrics.optDouble("offsetTop", 0.0)
        val anchorX = initialLeft + initialOffsetLeft + tapX / (density * current)
        val anchorY = initialTop + initialOffsetTop + tapY / (density * current)
        val gestureFactor = factor * 1.2f
        val xClearance = minOf(event.x, width - event.x).coerceAtLeast(0f)
        val yClearance = minOf(event.y, height - event.y).coerceAtLeast(0f)
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
                x = if (horizontalAxis) event.x + (if (index == 0) -distance / 2f else distance / 2f) else event.x
                y = if (horizontalAxis) event.y else event.y + (if (index == 0) -distance / 2f else distance / 2f)
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
                        val script = if (target > DocxZoomPolicy.DEFAULT_ZOOM + 0.01f) {
                            val scaleX = tapX / (density * target)
                            val scaleY = tapY / (density * target)
                            "(function(){var s=document.getElementById('scroller');var v=window.visualViewport;var stable=0;var attempts=0;function apply(){if(!s||!v)return;var settled=Math.abs(v.scale-$target)<=0.04;if(!settled){stable=0;if(attempts++<40){requestAnimationFrame(apply);return;}return;}if(stable++<2){requestAnimationFrame(apply);return;}s.scrollLeft=$anchorX-$scaleX-v.offsetLeft;s.scrollTop=$anchorY-$scaleY-v.offsetTop;}requestAnimationFrame(apply);})()"
                        } else {
                            "(function(){var s=document.getElementById('scroller');var v=window.visualViewport;var attempts=0;function restore(){if(!s||!v)return;if(Math.abs(v.scale-1)>0.04&&attempts++<90){requestAnimationFrame(restore);return;}if(Math.abs(v.scale-1)<=0.04){s.classList.remove('zoom-anchor-space');s.scrollLeft=0;s.scrollTop=0;}}requestAnimationFrame(restore);})()"
                        }
                        evaluateJavascript(script, null)
                    }
                }
            })
            start()
        }
    }

    evaluateJavascript(
        "(function(){var s=document.getElementById('scroller');var v=window.visualViewport;return JSON.stringify({scale:v?v.scale:1,left:s?s.scrollLeft:0,top:s?s.scrollTop:0,offsetLeft:v?v.offsetLeft:0,offsetTop:v?v.offsetTop:0,anchorSpace:!!(s&&s.classList.contains('zoom-anchor-space'))});})()"
    ) { result ->
        if (!isActive()) return@evaluateJavascript
        val metrics = parseMetrics(result) ?: return@evaluateJavascript
        val current = metrics.optDouble("scale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val target = DocxZoomPolicy.doubleTapTarget(current)
        if (target > DocxZoomPolicy.DEFAULT_ZOOM + 0.01f && !metrics.optBoolean("anchorSpace")) {
            evaluateJavascript(
                "(function(){var s=document.getElementById('scroller');if(!s)return null;s.classList.add('zoom-anchor-space');void s.offsetWidth;var p=parseFloat(getComputedStyle(s).paddingLeft)||0;s.scrollLeft=Math.max(0,s.clientWidth/2-p);s.scrollTop=window.innerHeight/2;var v=window.visualViewport;return JSON.stringify({scale:v?v.scale:1,left:s.scrollLeft,top:s.scrollTop,offsetLeft:v?v.offsetLeft:0,offsetTop:v?v.offsetTop:0,anchorSpace:true});})()"
            ) { prepared ->
                if (isActive()) parseMetrics(prepared)?.let(::startZoom)
            }
        } else {
            startZoom(metrics)
        }
    }
}
