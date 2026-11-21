package com.example.utils

import android.util.Log
import org.json.JSONObject
import java.io.FilterInputStream
import java.io.InputStream

/** Structured, content-free diagnostics shared by the normal and print DOCX WebViews. */
object DocxPreviewDiagnostics {
    const val TAG = "DocxPreviewDiag"

    private val jsMetricKeys = setOf(
        "event", "elapsedMs", "fetchCount", "fetchUrl", "fetchStatus", "fetchMime", "fetchContentLength",
        "fetchBytes", "fetchDurationMs", "docxMagic", "rendererAvailable", "pages", "pageWidth", "pageHeight",
        "responseMime",
        "renderDurationMs", "contentPresent", "contentVisible", "pageCount", "assetRequestUrl", "fileName",
        "fileSizeBytes", "decision", "lifecycle", "callbackElapsedMs", "responseStatus", "responseBytes", "responseLength",
        "rectLeft", "rectTop", "rectRight", "rectBottom", "rectWidth", "rectHeight",
        "scrollerLeft", "scrollerTop", "scrollerRight", "scrollerBottom", "scrollerWidth",
        "scrollerHeight", "windowWidth", "windowHeight", "documentWidth", "documentHeight",
        "outputWidth", "outputHeight", "currentZoom", "baseScale", "effectiveZoom", "cssZoom",
        "transform", "outputDisplay", "outputVisibility", "outputOpacity", "outputZoom", "outputTransform",
        "scrollLeft", "scrollTop", "visible", "display", "visibility", "opacity",
        "errorKind", "source", "line", "printMode", "layoutReady"
    )

    fun log(event: String, vararg fields: Pair<String, Any?>) {
        val json = JSONObject().put("event", event)
        fields.forEach { (key, value) ->
            if (value != null) json.put(key, if (value is String) safeString(value) else value)
        }
        Log.i(TAG, json.toString())
    }

    /** Accept only known scalar layout/transport metrics; never log arbitrary JS payloads or text. */
    fun logJavascriptMetrics(payload: String) {
        if (payload.length > 4096) {
            log("js_diagnostic_rejected", "payloadLength" to payload.length)
            return
        }
        runCatching {
            val source = JSONObject(payload)
            val safe = JSONObject()
            jsMetricKeys.forEach { key ->
                if (source.has(key)) {
                    val value = source.opt(key)
                    when (value) {
                        is String -> safe.put(key, safeString(value))
                        is Number, is Boolean -> safe.put(key, value)
                    }
                }
            }
            Log.i(TAG, safe.toString())
        }.onFailure {
            log("js_diagnostic_invalid", "payloadLength" to payload.length)
        }
    }

    fun safeString(value: String, maxLength: Int = 160): String = value
        .filterNot { it.isISOControl() }
        .take(maxLength)
}

/** Counts streamed bytes without retaining document data. Logs only the count and completion. */
class DocxCountingInputStream(
    input: InputStream,
    private val fileName: String,
    private val expectedBytes: Long
) : FilterInputStream(input) {
    private var bytesRead = 0L
    private var reported = false

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) bytesRead++ else report(complete = true)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) bytesRead += count else if (count < 0) report(complete = true)
        return count
    }

    override fun close() {
        try {
            super.close()
        } finally {
            report(complete = bytesRead >= expectedBytes)
        }
    }

    @Synchronized
    private fun report(complete: Boolean) {
        if (reported) return
        reported = true
        DocxPreviewDiagnostics.log(
            "asset_stream_complete",
            "fileName" to DocxPreviewDiagnostics.safeString(fileName),
            "expectedBytes" to expectedBytes,
            "bytesRead" to bytesRead,
            "complete" to complete
        )
    }
}
