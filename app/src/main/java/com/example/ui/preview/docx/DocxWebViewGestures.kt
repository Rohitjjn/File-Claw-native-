package com.example.ui.preview.docx

import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import com.example.core.format.DocxZoomPolicy
import org.json.JSONObject
import org.json.JSONTokener

/** Detects confirmed taps while leaving Chromium all multi-touch streams. */
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

/** Toggle between fit width and 200% using Chromium's native WebView scale API. */
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
        val fitScale = metrics.optDouble("fitScale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val currentZoom = current / fitScale
        val targetZoom = DocxZoomPolicy.doubleTapTarget(currentZoom)
        val target = fitScale * targetZoom
        if (!target.isFinite() || target <= 0f || kotlin.math.abs(target - current) < 0.01f) return

        if (target < current) {
            zoomBy(target)
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
        val initialDocLeft = metrics.optDouble("docLeft", 0.0)
        val initialDocTop = metrics.optDouble("docTop", 0.0)
        val anchorX = initialLeft + initialOffsetLeft + tapX / (density * current) - initialDocLeft
        val anchorY = initialTop + initialOffsetTop + tapY / (density * current) - initialDocTop
        val scaleX = tapX / (density * target)
        val scaleY = tapY / (density * target)

        zoomBy(target)
        evaluateJavascript(
            "(function(){var s=document.getElementById('scroller'),d=document.getElementById('scaled-document'),v=window.visualViewport;var stable=0;var attempts=0;function apply(){if(!s||!d||!v)return;var settled=Math.abs(v.scale-$target)<=0.04;if(!settled){stable=0;if(attempts++<90){requestAnimationFrame(apply);return;}return;}if(stable++<2){requestAnimationFrame(apply);return;}s.scrollLeft=$anchorX-$scaleX-v.offsetLeft+d.offsetLeft;s.scrollTop=$anchorY-$scaleY-v.offsetTop+d.offsetTop;}requestAnimationFrame(apply);})()",
            null
        )
    }

    evaluateJavascript(
        "(function(){var s=document.getElementById('scroller');var v=window.visualViewport;var state=window.DocxViewer.getState();var d=document.getElementById('scaled-document');return JSON.stringify({scale:v?v.scale:1,fitScale:state.fitScale,left:s?s.scrollLeft:0,top:s?s.scrollTop:0,offsetLeft:v?v.offsetLeft:0,offsetTop:v?v.offsetTop:0,docLeft:d?d.offsetLeft:0,docTop:d?d.offsetTop:0,anchorSpace:!!(s&&s.classList.contains('zoom-anchor-space'))});})()"
    ) { result ->
        if (!isActive()) return@evaluateJavascript
        val metrics = parseMetrics(result) ?: return@evaluateJavascript
        val current = metrics.optDouble("scale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val fitScale = metrics.optDouble("fitScale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val targetZoom = DocxZoomPolicy.doubleTapTarget(current / fitScale)
        if (targetZoom > DocxZoomPolicy.DEFAULT_ZOOM + 0.01f && !metrics.optBoolean("anchorSpace")) {
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
