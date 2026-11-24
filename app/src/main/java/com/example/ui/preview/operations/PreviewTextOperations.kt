package com.example.ui.preview.operations

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.example.core.io.TextLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_TEXT_OPERATION_CHARS = 2_000_000

/** Bounded file read shared by preview print and clipboard actions. */
suspend fun readBoundedPreviewText(file: File, charsetName: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val loaded = TextLoader.load(file, charsetName, maxChars = MAX_TEXT_OPERATION_CHARS)
        check(!loaded.truncated) { "This file exceeds the bounded $MAX_TEXT_OPERATION_CHARS-character limit for this operation." }
        loaded.text
    }
}

fun copyPreviewTextToClipboard(context: Context, title: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(title, text))
    Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
}

fun showPreviewTextOperationError(context: Context, error: Throwable) {
    Toast.makeText(context, error.message ?: "Could not prepare the complete file", Toast.LENGTH_LONG).show()
}
