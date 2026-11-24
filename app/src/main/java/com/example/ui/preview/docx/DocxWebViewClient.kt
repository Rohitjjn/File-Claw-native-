package com.example.ui.preview.docx

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/** Strictly local navigation/interception policy for both route ownership and renderer failures. */
internal class DocxWebViewClient(
    private val assets: WebViewAssetLoader,
    private val onFailure: (String) -> Unit,
    private val onExternalUrl: (Uri) -> Unit = {},
    private val onPageFinished: (WebView) -> Unit = {},
    private val onScaleChanged: (Float) -> Unit = {}
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val uri = request?.url ?: return null
        val local = uri.scheme == "https" && uri.host == "appassets.androidplatform.net"
        val response = if (local) assets.shouldInterceptRequest(uri) else null
        return response ?: WebResourceResponse(
            "text/plain", "utf-8", 403, "Forbidden", emptyMap(),
            java.io.ByteArrayInputStream(ByteArray(0))
        )
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        request?.url?.let(::shouldOverrideUri) ?: true

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        url?.let { Uri.parse(it) }?.let(::shouldOverrideUri) ?: true

    private fun shouldOverrideUri(uri: Uri): Boolean {
        if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
            (uri.path?.startsWith("/assets/docx_viewer/") == true || uri.path?.startsWith("/docx-src/") == true)
        ) return false
        if (isSupportedDocxExternalUri(uri)) {
            onExternalUrl(uri)
            return true
        }
        return true
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
        if (request?.isForMainFrame == true) onFailure("LOAD_FAILED")
    }

    override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        onFailure("RENDERER_UNAVAILABLE")
        return true
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        view?.let(onPageFinished)
    }

    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
        super.onScaleChanged(view, oldScale, newScale)
        onScaleChanged(newScale)
    }
}
