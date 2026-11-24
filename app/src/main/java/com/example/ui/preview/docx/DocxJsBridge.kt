package com.example.ui.preview.docx

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONObject

/** Small, JSON-only bridge. It never exposes document bytes or filesystem APIs to JavaScript. */
internal class DocxJsBridge(
    private val isCurrent: () -> Boolean,
    private val onVisualRender: (generation: Long, pages: Int, contentHeightPx: Int, contentWidthPx: Int, visiblePages: Int) -> Unit,
    private val onPageChanged: (page: Int, pages: Int) -> Unit,
    private val onProgress: (generation: Long, loadedBytes: Long, totalBytes: Long) -> Unit,
    private val onFailure: (generation: Long, code: String) -> Unit,
    private val onSearch: (total: Int, current: Int, truncated: Boolean) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private fun post(action: () -> Unit) = main.post { if (isCurrent()) action() }

    @JavascriptInterface fun onDocumentVisuallyRendered(payload: String) = post {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return@post
        onVisualRender(
            json.optLong("generation", -1),
            json.optInt("pageCount", 0),
            json.optInt("contentHeight", 0),
            json.optInt("contentWidth", 0),
            json.optInt("visiblePageCount", 0)
        )
    }

    @JavascriptInterface fun onProgress(payload: String) = post {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return@post
        onProgress(
            json.optLong("generation", -1),
            json.optLong("loaded", 0),
            json.optLong("total", 0)
        )
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

}
