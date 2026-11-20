package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.io.TextLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val MAX_TEXT_PRINT_CHARS = 2_000_000

internal suspend fun readBoundedText(file: File, charsetName: String): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val loaded = TextLoader.load(file, charsetName, maxChars = MAX_TEXT_PRINT_CHARS)
            check(!loaded.truncated) { "This file exceeds the bounded $MAX_TEXT_PRINT_CHARS-character limit for this operation." }
            loaded.text
        }
    }

internal fun copyTextToClipboard(context: android.content.Context, title: String, text: String) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(title, text))
    android.widget.Toast.makeText(context, "Text copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
}

internal fun showTextOperationError(context: android.content.Context, error: Throwable) {
    android.widget.Toast.makeText(
        context,
        error.message ?: "Could not prepare the complete file",
        android.widget.Toast.LENGTH_LONG
    ).show()
}

@Composable
fun PropertyItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
