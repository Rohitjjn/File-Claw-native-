package com.example.ui.preview.docx

import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView

/** Adds a confirmed single-tap callback without consuming Chromium's native touch stream. */
internal fun WebView.installConfirmedSingleTapListener(onSingleTapConfirmed: () -> Unit) {
    val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            onSingleTapConfirmed()
            return true
        }
    })
    setOnTouchListener { _, event ->
        detector.onTouchEvent(event)
        false // Preserve pinch, double-tap zoom, and one-finger pan in Chromium.
    }
}
