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

    fun startZoom(
        metrics: JSONObject,
        preservedAnchor: Pair<Double, Double>? = null,
        cancelPreviousCorrection: Boolean = true
    ) {
        if (!isActive()) return
        if (cancelPreviousCorrection) {
            evaluateJavascript("(function(){if(window.__docxAnchorFrame){cancelAnimationFrame(window.__docxAnchorFrame);window.__docxAnchorFrame=0;}})()") {
                if (isActive()) startZoom(metrics, preservedAnchor, cancelPreviousCorrection = false)
            }
            return
        }
        val current = metrics.optDouble("scale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val fitScale = metrics.optDouble("fitScale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val currentZoom = current / fitScale
        val targetZoom = DocxZoomPolicy.doubleTapTarget(currentZoom)
        val target = fitScale * targetZoom
        if (!target.isFinite() || target <= 0f || kotlin.math.abs(target - current) < 0.01f) return

        evaluateJavascript(
            "window.__docxZoomRequest={current:$current,fitScale:$fitScale,targetZoom:$targetZoom,target:$target};",
            null
        )
        if (target < current) {
            // Keep Chromium's native page-scale as the only zoom owner. Updating
            // viewport meta here races WebView's zoom compositor and can leave
            // the old overzoomed tile layer visible over the fit-width frame.
            zoomBy(target / current)
            evaluateJavascript(
                "(function(){var s=document.getElementById('scroller'),v=window.visualViewport;if(!s||!v)return;var stable=0,attempts=0,last=NaN;function restore(){var scale=v.scale;if(Math.abs(scale-$target)>0.04){stable=0;last=scale;if(attempts++<120){requestAnimationFrame(restore);return;}return;}if(Math.abs(scale-last)<0.002)stable++;else stable=0;last=scale;if(stable<3&&attempts++<120){requestAnimationFrame(restore);return;}s.classList.remove('zoom-anchor-space');s.scrollLeft=0;s.scrollTop=0;}requestAnimationFrame(restore);})()",
                null
            )
            return
        }

        val anchorX = preservedAnchor?.first ?: metrics.optDouble("anchorX", 0.0)
        val anchorY = preservedAnchor?.second ?: metrics.optDouble("anchorY", 0.0)
        zoomBy(target / current)
        evaluateJavascript(
            "(function(){var s=document.getElementById('scroller'),d=document.getElementById('scaled-document'),v=window.visualViewport;if(!s||!d||!v)return;var attempts=0,corrections=0,stable=0,lastScale=NaN,lastX=NaN,lastY=NaN;function apply(){if(!s.isConnected||!d.isConnected)return;var scale=v.scale,r=d.getBoundingClientRect(),sr=s.getBoundingClientRect();var x=$tapX/($density*scale)-(r.left-sr.left),y=$tapY/($density*scale)-(r.top-sr.top);if(Math.abs(scale-lastScale)<0.002&&Math.abs(x-lastX)<0.5&&Math.abs(y-lastY)<0.5)stable++;else stable=0;lastScale=scale;lastX=x;lastY=y;if(stable<4&&attempts++<120){window.__docxAnchorFrame=requestAnimationFrame(apply);return;}var dx=$anchorX-x,dy=$anchorY-y;if(corrections<4&&(Math.abs(dx)>0.5||Math.abs(dy)>0.5)){s.scrollLeft+=dx;s.scrollTop+=dy;corrections++;stable=0;window.__docxAnchorFrame=requestAnimationFrame(apply);return;}window.__docxAnchorFrame=0;}window.__docxAnchorFrame=requestAnimationFrame(apply);})()",
            null
        )
    }

    evaluateJavascript(
        "(function(){var s=document.getElementById('scroller'),d=document.getElementById('scaled-document'),v=window.visualViewport;var state=window.DocxViewer.getState(),r=d&&d.getBoundingClientRect(),sr=s&&s.getBoundingClientRect(),scale=v?v.scale:1;return JSON.stringify({scale:scale,fitScale:state.nativeFitScale||state.fitScale,anchorX:r&&sr?$tapX/($density*scale)-(r.left-sr.left):0,anchorY:r&&sr?$tapY/($density*scale)-(r.top-sr.top):0,anchorSpace:!!(s&&s.classList.contains('zoom-anchor-space'))});})()"
    ) { result ->
        if (!isActive()) return@evaluateJavascript
        val metrics = parseMetrics(result) ?: return@evaluateJavascript
        val current = metrics.optDouble("scale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val fitScale = metrics.optDouble("fitScale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val targetZoom = DocxZoomPolicy.doubleTapTarget(current / fitScale)
        if (targetZoom > DocxZoomPolicy.DEFAULT_ZOOM + 0.01f && !metrics.optBoolean("anchorSpace")) {
            val anchorX = metrics.optDouble("anchorX", 0.0)
            val anchorY = metrics.optDouble("anchorY", 0.0)
            evaluateJavascript(
                "(function(){var s=document.getElementById('scroller');if(!s)return null;s.classList.add('zoom-anchor-space');void s.offsetWidth;var p=parseFloat(getComputedStyle(s).paddingLeft)||0;s.scrollLeft=Math.max(0,s.clientWidth/2-p);s.scrollTop=window.innerHeight/2;var v=window.visualViewport,state=window.DocxViewer.getState();return JSON.stringify({scale:v?v.scale:1,left:s.scrollLeft,top:s.scrollTop,fitScale:state.nativeFitScale||state.fitScale,offsetLeft:v?v.offsetLeft:0,offsetTop:v?v.offsetTop:0,anchorSpace:true});})()"
            ) { prepared ->
                if (isActive()) parseMetrics(prepared)?.let { startZoom(it, anchorX to anchorY) }
            }
        } else {
            startZoom(metrics)
        }
    }
}
