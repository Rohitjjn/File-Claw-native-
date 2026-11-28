package com.example.ui.preview.docx

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.PathInterpolator
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
    val x = event.x.toString()
    val y = event.y.toString()
    evaluateJavascript(
        "(function(){var v=window.visualViewport,scale=v&&v.scale>0?v.scale:1;var node=document.elementFromPoint($x/($density*scale),$y/($density*scale));return !!(node&&node.closest&&node.closest('a[href]'));})()"
    ) { result ->
        if (isActive()) {
            if (result != "true") onTap()
            onResolved()
        }
    }
}

/** Animates native page scale and keeps the tapped document point under the finger. */
private val activeDocxZoomAnimations = java.util.WeakHashMap<WebView, android.animation.ValueAnimator>()

internal fun WebView.animateDocxDoubleTapZoom(
    event: MotionEvent,
    isActive: () -> Boolean = { true }
) {
    val webView = this
    activeDocxZoomAnimations.remove(webView)?.cancel()
    val density = resources.displayMetrics.density
    val tapX = event.x.toDouble()
    val tapY = event.y.toDouble()

    fun parseMetrics(result: String?): JSONObject? = runCatching {
        JSONObject(JSONTokener(result).nextValue().toString())
    }.getOrNull()

    fun metricsScript(): String =
        "(function(){var s=document.getElementById('scroller'),d=document.getElementById('scaled-document'),v=window.visualViewport,state=window.DocxViewer.getState(),r=d&&d.getBoundingClientRect(),sr=s&&s.getBoundingClientRect(),scale=v?v.scale:1;return JSON.stringify({scale:scale,fitScale:state.nativeFitScale||state.fitScale,anchorX:r&&sr?$tapX/($density*scale)+v.offsetLeft-(r.left-sr.left):0,anchorY:r&&sr?$tapY/($density*scale)+v.offsetTop-(r.top-sr.top):0,anchorSpace:!!(s&&s.classList.contains('zoom-anchor-space')),scrollTop:s?s.scrollTop:0});})()"

    fun start(metrics: JSONObject) {
        if (!isActive()) return
        val current = metrics.optDouble("scale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val fitScale = metrics.optDouble("fitScale", 1.0).toFloat().takeIf { it.isFinite() && it > 0f }
            ?: DocxZoomPolicy.DEFAULT_ZOOM
        val currentZoom = current / fitScale
        val targetZoom = DocxZoomPolicy.doubleTapTarget(currentZoom)
        val target = (fitScale * targetZoom).coerceAtMost(fitScale * DocxZoomPolicy.MAX_ZOOM)
        if (!target.isFinite() || target <= 0f || kotlin.math.abs(target - current) < 0.01f) return

        val anchorX = metrics.optDouble("anchorX", 0.0)
        val anchorY = metrics.optDouble("anchorY", 0.0)
        evaluateJavascript(
            "(function(){window.__docxZoomRequest={current:$current,fitScale:$fitScale,targetZoom:$targetZoom,target:$target,duration:260,easing:'cubic-bezier(0.25,0.1,0.25,1)'};window.__docxDoubleTapZoomActive=true;if(window.__docxDoubleTapReleaseTimer)clearTimeout(window.__docxDoubleTapReleaseTimer);window.__docxDoubleTapReleaseTimer=setTimeout(function(){window.__docxDoubleTapZoomActive=false;window.__docxDoubleTapReleaseTimer=0;},5000);if(window.DocxViewer)window.DocxViewer.startZoomAnchorCorrection($anchorX,$anchorY,$tapX,$tapY,$density);})()",
            null
        )

        var lastScale = current
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L
            interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f)
            addUpdateListener { valueAnimator ->
                if (!isActive()) {
                    cancel()
                    return@addUpdateListener
                }
                val fraction = valueAnimator.animatedValue as Float
                val desiredScale = current + (target - current) * fraction
                val increment = desiredScale / lastScale
                if (increment.isFinite() && increment > 0f && kotlin.math.abs(increment - 1f) > 0.0001f) {
                    zoomBy(increment)
                    lastScale = desiredScale
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    activeDocxZoomAnimations.remove(this@animateDocxDoubleTapZoom)
                    evaluateJavascript(
                        "(function(){if(window.DocxViewer)window.DocxViewer.stopZoomAnchorCorrection();var s=document.getElementById('scroller'),d=document.getElementById('scaled-document'),v=window.visualViewport;if(s&&d&&v){var stable=0,tries=0;function settle(){var r=d.getBoundingClientRect(),sr=s.getBoundingClientRect(),scale=v.scale,x=$tapX/($density*scale)+v.offsetLeft-(r.left-sr.left),y=$tapY/($density*scale)+v.offsetTop-(r.top-sr.top),dx=$anchorX-x,dy=$anchorY-y;window.__docxAnchorCorrection={x:x,y:y,dx:dx,dy:dy,scrollLeft:s.scrollLeft,scrollTop:s.scrollTop,maxScrollTop:s.scrollHeight-s.clientHeight,scale:scale};if(Math.abs(dx)>0.5||Math.abs(dy)>0.5){s.scrollLeft+=dx;s.scrollTop+=dy;stable=0;}else stable++;if(stable<3&&tries++<30){requestAnimationFrame(settle);return;}window.__docxDoubleTapZoomActive=false;if(window.__docxDoubleTapReleaseTimer)clearTimeout(window.__docxDoubleTapReleaseTimer);window.__docxDoubleTapReleaseTimer=0;}requestAnimationFrame(settle);}else{window.__docxDoubleTapZoomActive=false;}})()",
                        null
                    )
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    activeDocxZoomAnimations.remove(this@animateDocxDoubleTapZoom)
                    evaluateJavascript(
                        "(function(){if(window.DocxViewer)window.DocxViewer.stopZoomAnchorCorrection();window.__docxDoubleTapZoomActive=false;if(window.__docxDoubleTapReleaseTimer)clearTimeout(window.__docxDoubleTapReleaseTimer);window.__docxDoubleTapReleaseTimer=0;})()",
                        null
                    )
                }
            })
        }
        activeDocxZoomAnimations[this] = animator
        animator.start()
    }

    evaluateJavascript(
        "(function(){if(window.__docxAnchorFrame){cancelAnimationFrame(window.__docxAnchorFrame);window.__docxAnchorFrame=0;}})()"
    ) {
        if (!isActive()) return@evaluateJavascript
        evaluateJavascript(metricsScript()) { result ->
            if (!isActive()) return@evaluateJavascript
            val metrics = parseMetrics(result) ?: return@evaluateJavascript
            val scale = metrics.optDouble("scale", 1.0)
            val fit = metrics.optDouble("fitScale", 1.0)
            val zoom = if (fit > 0.0) scale / fit else 1.0
            val targetZoom = DocxZoomPolicy.doubleTapTarget(zoom.toFloat())
            val zoomingIn = targetZoom > zoom + 0.01
            val hasAnchorSpace = metrics.optBoolean("anchorSpace")
            if (zoomingIn && !hasAnchorSpace) {
                val anchorX = metrics.optDouble("anchorX", 0.0)
                val anchorY = metrics.optDouble("anchorY", 0.0)
                evaluateJavascript(
                    "(function(){var s=document.getElementById('scroller');if(!s)return null;var previousTop=s.scrollTop;s.classList.add('zoom-anchor-space');void s.offsetWidth;var p=parseFloat(getComputedStyle(s).paddingLeft)||0;s.scrollLeft=Math.max(0,s.clientWidth/2-p);s.scrollTop=previousTop+window.innerHeight/2;return JSON.stringify({scale:window.visualViewport.scale,fitScale:(window.DocxViewer.getState().nativeFitScale||window.DocxViewer.getState().fitScale),anchorX:$anchorX,anchorY:$anchorY,anchorSpace:true});})()"
                ) { prepared -> parseMetrics(prepared)?.let(::start) }
            } else if (!zoomingIn && hasAnchorSpace) {
                // The temporary centering gutter is half a viewport tall. Remove it without
                // resetting scroll to zero, then retain the current page's absolute position.
                evaluateJavascript(
                    "(function(){var s=document.getElementById('scroller');if(!s)return null;var top=s.scrollTop;s.classList.remove('zoom-anchor-space');void s.offsetWidth;s.scrollTop=Math.max(0,top-window.innerHeight/2);return ${metricsScript()};})()"
                ) { adjusted -> parseMetrics(adjusted)?.let(::start) }
            } else {
                start(metrics)
            }
        }
    }
}
