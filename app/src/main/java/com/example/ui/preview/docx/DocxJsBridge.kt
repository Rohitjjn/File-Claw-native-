package com.example.ui.preview.docx

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONObject

/** Small, JSON-only bridge. It never exposes document bytes or filesystem APIs to JavaScript. */
internal class DocxJsBridge(
    private val isCurrent: () -> Boolean,
    private val onReady: (generation: Long, pages: Int) -> Unit,
    private val onPageChanged: (page: Int, pages: Int) -> Unit,
    private val onFailure: (generation: Long, code: String) -> Unit,
    private val onSearch: (total: Int, current: Int, truncated: Boolean) -> Unit,
    private val onZoom: (Float) -> Unit,
    private val onTap: () -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onPrintReady: () -> Unit = {}
) {
    private val main = Handler(Looper.getMainLooper())
    private fun post(action: () -> Unit) = main.post { if (isCurrent()) action() }

    @JavascriptInterface fun onReady(payload: String) = post {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return@post
        onReady(json.optLong("generation", -1), json.optInt("pages", 0))
    }

    @JavascriptInterface fun onPageChanged(payload: String) = post {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return@post
        onPageChanged(json.optInt("page", 1), json.optInt("pages", 1))
    }

    @JavascriptInterface fun onFailure(payload: String) = post {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return@post
        onFailure(json.optLong("generation", -1), json.optString("code", "LOAD_FAILED"))
    }

    @JavascriptInterface fun onSearchResults(payload: String) = post {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return@post
        onSearch(json.optInt("total"), json.optInt("current"), json.optBoolean("truncated"))
    }

    @JavascriptInterface fun onZoomChanged(payload: String) = post {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return@post
        onZoom(DocxZoomAndGesture.coerce(json.optDouble("zoom", 1.0).toFloat()))
    }

    @JavascriptInterface fun onPrintReady() = post(onPrintReady)
    @JavascriptInterface fun onDocumentTap() = post(onTap)
    @JavascriptInterface fun onDocumentDoubleTap() = post(onDoubleTap)
}
