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

    fun parseMetrics(result: String?): JSONObject? = runCatching {
        JSONObject(JSONTokener(result).nextValue().toString())
    }.getOrNull()

    fun startZoom(metrics: JSONObject) {
        if (!isActive()) return
        val current = metrics.optDouble("scale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val target = DocxZoomPolicy.doubleTapTarget(current)
        val anchorX = tapX / (density * current)
        val anchorY = tapY / (density * current)
        val initialLeft = metrics.optDouble("left", 0.0)
        val initialTop = metrics.optDouble("top", 0.0)
        val initialOffsetLeft = metrics.optDouble("offsetLeft", 0.0)
        val initialOffsetTop = metrics.optDouble("offsetTop", 0.0)
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
                    val script = if (target > DocxZoomPolicy.DEFAULT_ZOOM + 0.01f) {
                        val targetLeft = initialLeft + initialOffsetLeft + anchorX - tapX / (density * target)
                        val targetTop = initialTop + initialOffsetTop + anchorY - tapY / (density * target)
                        "(function(){var s=document.getElementById('scroller');var v=window.visualViewport;var stable=0;var attempts=0;function apply(){if(!s||!v)return;if(Math.abs(v.scale-$target)>0.04||stable<2){stable=Math.abs(v.scale-$target)<=0.04?stable+1:0;if(attempts++<40){requestAnimationFrame(apply);return;}}s.scrollLeft=$targetLeft-v.offsetLeft;s.scrollTop=$targetTop-v.offsetTop;}requestAnimationFrame(apply);})()"
                    } else {
                        "(function(){var s=document.getElementById('scroller');if(s){s.classList.remove('zoom-anchor-space');s.scrollLeft=0;s.scrollTop=0;}})()"
                    }
                    evaluateJavascript(script, null)
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
