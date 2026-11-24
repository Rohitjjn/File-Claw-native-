package com.example.ui.preview.viewers

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.webkit.WebView
import com.example.data.RecentFileEntity
import com.example.data.SettingEntity
import com.example.services.FileManager
import com.example.ui.component.ViewerSyntaxTheme
import com.example.ui.component.formatFileSize
import com.example.ui.preview.docx.DocxPreviewScreen
import com.example.ui.preview.docx.DocxViewerState
import com.example.viewmodel.MainViewModel
import com.example.ui.screens.InlineEditWorkspace

/** Immutable inputs for the file-state renderer; ephemeral mutations return through callbacks. */
data class PreviewContentState(
    val fileState: MainViewModel.FileContentState,
    val settings: SettingEntity,
    val expandedZipPaths: Set<String>,
    val isDarkTheme: Boolean,
    val isBarsVisible: Boolean,
    val isHtmlFile: Boolean,
    val isWebRenderActive: Boolean,
    val htmlScriptsEnabled: Boolean,
    val isEditMode: Boolean,
    val editTextValue: TextFieldValue,
    val fullPreviewText: String?,
    val isFullTextLoading: Boolean,
    val largeTextSearchError: String?,
    val inViewerSearchQuery: String,
    val searchTriggerNext: Int,
    val searchTriggerPrev: Int,
    val viewerSyntaxTheme: ViewerSyntaxTheme,
    val viewerFontSizeSp: Int,
    val viewerZoomPercent: Int,
    val pendingScrollRequest: ViewerScrollRequest?,
    val pdfViewRotation: Int,
    val currentMatchIndex: Int,
    val scrollToPage: Int?,
    val pageJumpToken: Int,
    val currentPdfPage: Int,
    val totalPdfPages: Int,
    val docxViewerState: DocxViewerState,
    val docxZoomFactor: Float
)

/** Explicit effects requested by a route; the renderer never reaches into the coordinator. */
data class PreviewContentActions(
    val onBack: () -> Unit,
    val openArchive: (RecentFileEntity, String) -> Unit,
    val onEditTextChanged: (TextFieldValue) -> Unit,
    val enableHtmlScripts: () -> Unit,
    val toggleBars: () -> Unit,
    val onLargeTextSearchResultCount: (Int, Int, Boolean) -> Unit,
    val onScrollRequestHandled: () -> Unit,
    val saveSpreadsheet: (RecentFileEntity, List<List<String>>) -> Unit,
    val toggleZipExpanded: (String) -> Unit,
    val openZipEntry: (RecentFileEntity, FileManager.ZipNode, String) -> Unit,
    val onPdfScrollHandled: () -> Unit,
    val onPdfScrubJump: (Int) -> Unit,
    val onPdfSearchSnapshot: (PdfSearchSnapshot, Boolean) -> Unit,
    val onPdfSearchError: (String) -> Unit,
    val onPdfPageChanged: (Int, Int) -> Unit,
    val onDocxSearchResultCount: (Int, Int, Boolean) -> Unit,
    val onDocxWebViewCreated: (WebView?) -> Unit,
    val onDocxReady: () -> Unit,
    val onDocxZoomChanged: (Float) -> Unit,
    val onDocxRendererFallback: (Boolean) -> Unit,
    val closePresentation: () -> Unit
)

/** Dispatches every file state to its viewer using only snapshot inputs and explicit effects. */
@Composable
fun PreviewContent(state: PreviewContentState, actions: PreviewContentActions, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        when (val routedState = state.fileState) {
                        is MainViewModel.FileContentState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                com.example.ui.component.PremiumLoadingIndicator(text = "Loading file...")
                            }
                        }
                        is MainViewModel.FileContentState.DocxPreviewLimitExceeded -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "DOCX preview limit",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Formatted preview is currently limited to ${routedState.maxBytes / (1024 * 1024)} MiB to protect device memory. This file is larger, so it was not rendered. The original file has not been changed.",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        is MainViewModel.FileContentState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Preview Error",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = routedState.message,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        is MainViewModel.FileContentState.ArchivePasswordRequired -> {
                            var inputPassword by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = { actions.onBack() },
                                title = { Text("Password Required") },
                                text = {
                                    Column {
                                        Text("This archive is password protected.", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = inputPassword,
                                            onValueChange = { inputPassword = it },
                                            singleLine = true,
                                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                            label = { Text("Password") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        actions.openArchive(routedState.file, inputPassword)
                                    }) {
                                        Text("Open")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { actions.onBack() }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                        is MainViewModel.FileContentState.TextSuccess -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                val completeContent = state.fullPreviewText ?: routedState.content
                                val supportsFullFileSearch = routedState.file.extension.lowercase() in setOf("txt", "md")
                                val notice = routedState.readOnlyReason ?: when {
                                    routedState.truncated && supportsFullFileSearch && state.isFullTextLoading -> "Searching the complete ${formatFileSize(routedState.totalBytes)} file…"
                                    routedState.truncated && supportsFullFileSearch && state.largeTextSearchError != null -> "Full-file search failed: ${state.largeTextSearchError.orEmpty()}"
                                    routedState.truncated && supportsFullFileSearch -> "Preview is bounded to protect memory; in-document search scans the complete ${formatFileSize(routedState.totalBytes)} file and opens matches in bounded excerpts (first 10,000 results are navigable)."
                                    routedState.truncated -> "Preview is limited to the first ${routedState.content.length} characters to protect memory."
                                    else -> null
                                }
                                if (notice != null && state.isBarsVisible) NoticeBanner(notice)
                                if (state.isHtmlFile && state.isWebRenderActive && !state.htmlScriptsEnabled) {
                                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 16.dp, end = 4.dp)
                                        ) {
                                            Text(
                                                "Scripts are off for safety.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(onClick = { actions.enableHtmlScripts() }) { Text("Run scripts") }
                                        }
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    if (state.isEditMode) {
                                        // Inline editing workspace: sits on the exact same surface as the
                                        // preview canvas (no line-number gutter) so the screen keeps looking
                                        // like the preview — the user simply starts typing in place.
                                        InlineEditWorkspace(
                                            value = state.editTextValue,
                                            onValueChange = actions.onEditTextChanged,
                                            settings = state.settings,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else when (routedState.file.extension.lowercase()) {
                                        "md" -> MarkdownPreview(
                                            markdownText = completeContent,
                                            fileEntity = routedState.file,
                                            fontSizeSetting = state.settings.fontSize,
                                            isNightMode = state.isDarkTheme,
                                            searchQuery = state.inViewerSearchQuery,
                                            searchTriggerNext = state.searchTriggerNext,
                                            searchTriggerPrev = state.searchTriggerPrev,
                                            onSearchResultCount = actions.onLargeTextSearchResultCount,
                                            onSingleTap = actions.toggleBars,
                                            syntaxTheme = state.viewerSyntaxTheme,
                                            viewerFontSizeSp = state.viewerFontSizeSp,
                                            zoomPercent = state.viewerZoomPercent,
                                            scrollRequest = state.pendingScrollRequest,
                                            onScrollRequestHandled = actions.onScrollRequestHandled,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        "txt" -> PlainTextPreview(
                                            content = completeContent,
                                            isDarkMode = state.isDarkTheme,
                                            fontSizeSetting = state.settings.fontSize,
                                            searchQuery = state.inViewerSearchQuery,
                                            searchTriggerNext = state.searchTriggerNext,
                                            searchTriggerPrev = state.searchTriggerPrev,
                                            onSearchResultCount = actions.onLargeTextSearchResultCount,
                                            onSingleTap = actions.toggleBars,
                                            syntaxTheme = state.viewerSyntaxTheme,
                                            viewerFontSizeSp = state.viewerFontSizeSp,
                                            zoomPercent = state.viewerZoomPercent,
                                            scrollRequest = state.pendingScrollRequest,
                                            onScrollRequestHandled = actions.onScrollRequestHandled,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        else -> WebViewPreview(
                                            fileEntity = routedState.file,
                                            allowScripts = state.htmlScriptsEnabled,
                                            searchQuery = state.inViewerSearchQuery,
                                            onSingleTap = actions.toggleBars,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                        is MainViewModel.FileContentState.CsvSuccess -> {
                            CsvPreview(
                                csvRows = routedState.rows,
                                fileEntity = routedState.file,
                                onSaveSpreadsheet = actions.saveSpreadsheet,
                                editable = routedState.editable,
                                notice = routedState.readOnlyReason
                                    ?: if (routedState.truncated) "Large file: showing the first ${routedState.rows.size} rows. Read-only, so nothing is lost on save." else null,
                                onDiscardAndExit = actions.onBack,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is MainViewModel.FileContentState.ZipSuccess -> {
                            ZipPreview(
                                zipRoot = routedState.root,
                                fileEntity = routedState.file,
                                expandedPaths = state.expandedZipPaths,
                                onToggleExpand = { path ->
                                    actions.toggleZipExpanded(path)
                                },
                                onZipEntryClick = { node, pwd ->
                                    actions.openZipEntry(routedState.file, node, pwd)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is MainViewModel.FileContentState.ImageSuccess -> {
                            ImagePreview(
                                fileEntity = routedState.file,
                                modifier = Modifier.fillMaxSize(),
                                onTap = actions.toggleBars
                            )
                        }
                        is MainViewModel.FileContentState.PdfSuccess -> {
                            PdfPreview(
                                fileEntity = routedState.file,
                                viewRotation = state.pdfViewRotation,
                                searchQuery = state.inViewerSearchQuery,
                                selectedMatchIndex = state.currentMatchIndex,
                                scrollToPage = state.scrollToPage,
                                scrollToken = state.pageJumpToken,
                                onScrollToPageHandled = actions.onPdfScrollHandled,
                                scrubberPage = state.currentPdfPage,
                                scrubberPageCount = state.totalPdfPages,
                                onScrubJump = actions.onPdfScrubJump,
                                onSearchSnapshot = actions.onPdfSearchSnapshot,
                                onSearchError = actions.onPdfSearchError,
                                modifier = Modifier.fillMaxSize(),
                                onSingleTap = actions.toggleBars,
                                onPageChanged = actions.onPdfPageChanged,
                                onBackClick = actions.onBack
                            )
                        }
                        is MainViewModel.FileContentState.DocxSuccess -> {
                            DocxPreviewScreen(
                                fileEntity = routedState.file,
                                viewerState = state.docxViewerState,
                                isNightMode = state.isDarkTheme,
                                modifier = Modifier.fillMaxSize(),
                                onPageChanged = actions.onPdfPageChanged,
                                scrollToPage = state.scrollToPage,
                                onScrollProgressHandled = actions.onPdfScrollHandled,
                                searchQuery = state.inViewerSearchQuery,
                                searchTriggerNext = state.searchTriggerNext,
                                searchTriggerPrev = state.searchTriggerPrev,
                                onSearchResultCount = actions.onDocxSearchResultCount,
                                zoomFactor = state.docxZoomFactor,
                                onWebViewCreated = actions.onDocxWebViewCreated,
                                onDocumentReady = actions.onDocxReady,
                                onSingleTap = actions.toggleBars,
                                onZoomChanged = actions.onDocxZoomChanged,
                                onDoubleTap = actions.onDocxZoomChanged,
                                onRendererFallback = actions.onDocxRendererFallback
                            )
                        }
                        is MainViewModel.FileContentState.PptxSuccess -> {
                            PptxPreviewWebView(
                                base64Pptx = routedState.base64Data,
                                fileEntity = routedState.file,
                                isNightMode = state.isDarkTheme,
                                onClose = actions.closePresentation,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is MainViewModel.FileContentState.MediaSuccess -> {
                            if (routedState.isAudio) {
                                AudioPlayer(
                                    fileEntity = routedState.file,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                VideoPlayer(
                                    fileEntity = routedState.file,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        is MainViewModel.FileContentState.BinarySuccess -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                val header = listOfNotNull(routedState.label?.let { "Detected: $it" }, routedState.note).joinToString(" \u2022 ")
                                if (header.isNotEmpty()) NoticeBanner(header)
                                HexViewer(
                                    hexRows = routedState.hexRows,
                                    asciiRows = routedState.asciiRows,
                                    fileEntity = routedState.file,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                        is MainViewModel.FileContentState.SvgSuccess -> SvgViewer(routedState.file, Modifier.fillMaxSize())
                        is MainViewModel.FileContentState.SqliteSuccess -> SqliteViewer(routedState.file, Modifier.fillMaxSize())
                        is MainViewModel.FileContentState.FontSuccess -> FontViewer(routedState.file, Modifier.fillMaxSize())
                        is MainViewModel.FileContentState.Model3dSuccess -> Model3dViewer(routedState.mesh, routedState.label, Modifier.fillMaxSize())
                        is MainViewModel.FileContentState.HtmlSuccess -> HtmlStringViewer(routedState.html, Modifier.fillMaxSize())
                        else -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No file selected", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            }
                        }
                        }
    }
}
