package com.example.ui.preview.chrome

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RecentFileEntity
import com.example.ui.component.AppearanceBottomSheet
import com.example.ui.component.DocumentOutlineBottomSheet
import com.example.ui.component.DocumentOutlineParser
import com.example.ui.component.FilePropertiesBottomSheet
import com.example.ui.component.ViewerSyntaxTheme
import com.example.ui.component.EditHistoryDialog
import com.example.ui.preview.viewers.ViewerScrollRequest

/** Snapshot of screen-owned dialog state, rendered without a ViewModel dependency. */
data class PreviewDialogState(
    val activeFile: RecentFileEntity?,
    val currentDocumentText: String?,
    val showProperties: Boolean,
    val showDocxEditNotice: Boolean,
    val showJumpToLine: Boolean,
    val showJumpToPage: Boolean,
    val pendingLink: String?,
    val showOutline: Boolean,
    val showAppearance: Boolean,
    val isEditMode: Boolean,
    val showDiscard: Boolean,
    val showSave: Boolean,
    val showHistory: Boolean,
    val showExport: Boolean,
    val viewerTheme: ViewerSyntaxTheme,
    val viewerFontSizeSp: Int,
    val viewerZoomPercent: Int,
    val draft: TextFieldValue,
    val history: List<TextFieldValue>,
    val totalPages: Int
)

data class PreviewDialogActions(
    val dismissProperties: () -> Unit,
    val dismissDocxEditNotice: () -> Unit,
    val dismissJumpToLine: () -> Unit,
    val goToLine: (Int) -> Unit,
    val dismissJumpToPage: () -> Unit,
    val goToPage: (Int) -> Unit,
    val dismissLink: () -> Unit,
    val dismissOutline: () -> Unit,
    val dismissAppearance: () -> Unit,
    val selectOutlineLine: (Int) -> Unit,
    val themeChanged: (ViewerSyntaxTheme) -> Unit,
    val fontSizeChanged: (Int) -> Unit,
    val zoomChanged: (Int) -> Unit,
    val dismissDiscard: () -> Unit,
    val exitEditMode: () -> Unit,
    val dismissSave: () -> Unit,
    val saveDraft: () -> Unit,
    val dismissHistory: () -> Unit,
    val restoreHistory: (TextFieldValue) -> Unit,
    val dismissExport: () -> Unit,
    val exportCopy: (String) -> Unit
)

/** Callback-driven preview dialog host; all navigation and persistence stay in the route. */
@Composable
fun PreviewDialogHost(state: PreviewDialogState, actions: PreviewDialogActions) {
    val context = LocalContext.current
    val file = state.activeFile
    if (state.showProperties && file != null) {
        FilePropertiesBottomSheet(fileEntity = file, onDismissRequest = actions.dismissProperties)
    }
    if (state.showDocxEditNotice) PreviewMessageDialog("Edit", "Coming Soon", actions.dismissDocxEditNotice)
    if (state.showJumpToLine) {
        var value by remember { mutableStateOf("") }
        val lineCount = remember(state.currentDocumentText) {
            state.currentDocumentText?.count { it == '\n' }?.plus(1) ?: 1000
        }
        AlertDialog(
            onDismissRequest = actions.dismissJumpToLine,
            title = { Text("Go to Line") },
            text = {
                Column {
                    Text("Enter line number (1 to $lineCount):")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value, { value = it.filter(Char::isDigit) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val line = value.toIntOrNull()
                    if (line != null && line > 0) actions.goToLine(line)
                    else Toast.makeText(context, "Invalid line number", Toast.LENGTH_SHORT).show()
                    actions.dismissJumpToLine()
                }) { Text("Go") }
            },
            dismissButton = { TextButton(actions.dismissJumpToLine) { Text("Cancel") } }
        )
    }
    if (state.showJumpToPage) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = actions.dismissJumpToPage,
            title = { Text(if (file?.extension?.lowercase() == "docx") "Go to Approximate Page" else "Go to Page") },
            text = {
                Column {
                    if (file?.extension?.lowercase() == "docx") {
                        Text("DOCX page positions are renderer estimates, not verified Microsoft Word pagination.")
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("Enter page number (1 to ${state.totalPages}):")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value, { value = it.filter(Char::isDigit) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val page = value.toIntOrNull()
                    if (page != null && page in 1..state.totalPages) actions.goToPage(page)
                    else Toast.makeText(context, "Invalid page number", Toast.LENGTH_SHORT).show()
                    actions.dismissJumpToPage()
                }) { Text("Go") }
            },
            dismissButton = { TextButton(actions.dismissJumpToPage) { Text("Cancel") } }
        )
    }
    state.pendingLink?.let { url ->
        AlertDialog(
            onDismissRequest = actions.dismissLink,
            title = { Text("Open Link") },
            text = { Text("Do you want to open this link in your browser?\n\n$url") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        .onFailure { Toast.makeText(context, "Failed to open link", Toast.LENGTH_SHORT).show() }
                    actions.dismissLink()
                }) { Text("Open") }
            },
            dismissButton = { TextButton(actions.dismissLink) { Text("Cancel") } }
        )
    }
    if (state.showOutline && file != null && !state.isEditMode) {
        val content = state.currentDocumentText.orEmpty()
        DocumentOutlineBottomSheet(
            documentContent = content,
            documentTitle = file.name,
            onDismissRequest = actions.dismissOutline,
            onSelectHeading = actions.selectOutlineLine
        )
    }
    if (state.showAppearance) {
        AppearanceBottomSheet(
            currentTheme = state.viewerTheme,
            currentFontSizeSp = state.viewerFontSizeSp,
            onThemeChanged = actions.themeChanged,
            onFontSizeChanged = actions.fontSizeChanged,
            currentZoomPercent = state.viewerZoomPercent,
            onZoomChanged = actions.zoomChanged,
            onDismissRequest = actions.dismissAppearance
        )
    }
    if (state.showDiscard) {
        AlertDialog(
            onDismissRequest = actions.dismissDiscard,
            title = { Text("Discard changes?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("Your edits to this file have not been saved yet. Discard them?") },
            dismissButton = { TextButton(actions.dismissDiscard) { Text("Keep editing") } },
            confirmButton = { TextButton(onClick = actions.exitEditMode) { Text("Discard", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
            shape = MaterialTheme.shapes.large
        )
    }
    if (state.showSave) {
        AlertDialog(
            onDismissRequest = actions.dismissSave,
            title = { Text("Save changes?", fontWeight = FontWeight.Bold) },
            text = { Text("Save your edits to \"${file?.name ?: "this file"}\"? A .bak backup of the original is kept automatically.") },
            dismissButton = { TextButton(actions.dismissSave) { Text("Cancel") } },
            confirmButton = { Button(onClick = actions.saveDraft) { Text("Save") } },
            shape = MaterialTheme.shapes.large
        )
    }
    if (state.showHistory) {
        EditHistoryDialog(entries = state.history, onDismissRequest = actions.dismissHistory, onRestore = actions.restoreHistory)
    }
    if (state.showExport && file != null) {
        val defaultName = remember(file.name) {
            val dot = file.name.lastIndexOf('.')
            if (dot > 0) file.name.substring(0, dot) + "_copy." + file.extension else file.name + "_copy"
        }
        var name by remember(file.path) { mutableStateOf(defaultName) }
        AlertDialog(
            onDismissRequest = actions.dismissExport,
            title = { Text("Export Modified Copy", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Atomically save your edits into a new copy without overwriting the original:", fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("File Name") }, modifier = Modifier.fillMaxWidth())
                }
            },
            dismissButton = { TextButton(actions.dismissExport) { Text("Cancel") } },
            confirmButton = { Button(onClick = { if (name.isNotBlank()) actions.exportCopy(name.trim()) }) { Text("Export Copy") } },
            shape = MaterialTheme.shapes.large
        )
    }
}

@Composable
fun PreviewMessageDialog(title: String, message: String, onDismissRequest: () -> Unit, dismissLabel: String = "OK") {
    AlertDialog(onDismissRequest = onDismissRequest, title = { Text(title) }, text = { Text(message) }, confirmButton = { TextButton(onClick = onDismissRequest) { Text(dismissLabel) } })
}
