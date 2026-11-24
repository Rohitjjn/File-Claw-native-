package com.example.ui.preview.docx

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import com.example.core.format.DocxZoomPolicy
import org.json.JSONObject
import org.json.JSONTokener

/** Detects confirmed taps and double taps while leaving multi-touch streams with Chromium. */
internal fun WebView.installDocxGestureListeners(
    isActive: () -> Boolean = { true },
    onSingleTapConfirmed: (MotionEvent) -> Unit,
    onDoubleTap: (MotionEvent) -> Unit
) {
    var consumeDoubleTapSequence = false
    val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            if (isActive()) onSingleTapConfirmed(event)
            return true
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            if (isActive()) {
                consumeDoubleTapSequence = true
                onDoubleTap(event)
            }
            return true
        }
    })
    setOnTouchListener { _, event ->
        detector.onTouchEvent(event)
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

/** Smoothly toggles native WebView zoom between fit width and 200%, anchored at the tap. */
internal fun WebView.animateDocxDoubleTapZoom(
    event: MotionEvent,
    isActive: () -> Boolean = { true }
) {
    val density = resources.displayMetrics.density
    val tapX = event.x.toDouble()
    val tapY = event.y.toDouble()
    evaluateJavascript(
        "(function(){var s=document.getElementById('scroller');return JSON.stringify({scale:visualViewport?visualViewport.scale:1,left:s?s.scrollLeft:0,top:s?s.scrollTop:0});})()"
    ) { result ->
        if (!isActive()) return@evaluateJavascript
        val metrics = runCatching {
            JSONObject(JSONTokener(result).nextValue().toString())
        }.getOrNull() ?: return@evaluateJavascript
        val current = metrics.optDouble("scale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val target = DocxZoomPolicy.doubleTapTarget(current)
        val anchorX = tapX / (density * current)
        val anchorY = tapY / (density * current)
        val initialLeft = metrics.optDouble("left", 0.0)
        val initialTop = metrics.optDouble("top", 0.0)
        var previous = current
        var cancelled = false
        ValueAnimator.ofFloat(current, target).apply {
            duration = 220L
            addUpdateListener { animation ->
                if (!isActive()) {
                    cancel()
                } else {
                    val next = animation.animatedValue as Float
                    val factor = next / previous
                    if (factor.isFinite() && factor > 0f) zoomBy(factor)
                    previous = next
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled || !isActive()) return
                    val left = initialLeft + anchorX - tapX / (density * target)
                    val top = initialTop + anchorY - tapY / (density * target)
                    evaluateJavascript(
                        "(function(){var s=document.getElementById('scroller');if(s){s.scrollLeft=$left;s.scrollTop=$top;}})()",
                        null
                    )
                }
            })
            start()
        }
    }
}
