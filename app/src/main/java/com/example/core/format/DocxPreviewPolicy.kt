package com.example.core.format

/** Source-size routing policy for the in-memory WebView renderer. */
object DocxPreviewPolicy {
    const val MAX_FORMATTED_PREVIEW_BYTES = 25L * 1024 * 1024

    sealed class Route {
        data object FormattedPreview : Route()
        data class LimitExceeded(val maxBytes: Long) : Route()
    }

    /** Exactly-at-limit files remain supported; larger files receive an explicit DOCX limit state. */
    fun route(fileBytes: Long): Route =
        if (fileBytes <= MAX_FORMATTED_PREVIEW_BYTES) Route.FormattedPreview
        else Route.LimitExceeded(MAX_FORMATTED_PREVIEW_BYTES)
}
