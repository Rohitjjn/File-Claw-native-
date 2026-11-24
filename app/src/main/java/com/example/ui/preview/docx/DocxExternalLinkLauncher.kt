package com.example.ui.preview.docx

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri

/** Dispatches only supported external DOCX links to Android's normal resolver. */
internal fun launchDocxExternalLink(
    context: Context,
    uri: Uri,
    onUnavailable: () -> Unit
): Boolean {
    if (!isSupportedDocxExternalUri(uri)) return false
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (!hasActivityContext(context)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        onUnavailable()
        false
    } catch (_: SecurityException) {
        onUnavailable()
        false
    }
}

internal val DOCX_EXTERNAL_LINK_SCHEMES = setOf("http", "https", "mailto", "tel")

internal fun isSupportedDocxExternalUri(uri: Uri): Boolean =
    uri.scheme?.lowercase(java.util.Locale.ROOT) in DOCX_EXTERNAL_LINK_SCHEMES

private fun hasActivityContext(context: Context): Boolean {
    var current = context
    while (current is ContextWrapper) {
        if (current is Activity) return true
        current = current.baseContext
    }
    return current is Activity
}
