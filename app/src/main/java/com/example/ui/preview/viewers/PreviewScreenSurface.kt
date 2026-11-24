package com.example.ui.preview.viewers

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.data.RecentFileEntity
import com.example.data.SettingEntity
import com.example.ui.component.DocumentOutlineParser
import com.example.ui.preview.viewers.ViewerScrollRequest
import com.example.ui.preview.chrome.*
import com.example.ui.preview.docx.DocxViewerState
import com.example.ui.preview.editing.TextUndoStack
import com.example.ui.preview.chrome.PreviewScreenState
import com.example.ui.screens.openPdfInBrowser
import androidx.compose.ui.platform.testTag
import com.example.ui.screens.findHostActivity
import com.example.ui.screens.applyTabIndent
import com.example.ui.screens.applyMarkdownWrap
import com.example.ui.preview.operations.*
import com.example.viewmodel.MainViewModel
import com.example.ui.component.ViewerSyntaxTheme
import com.example.ui.component.ViewerAppearancePrefs
import com.example.ui.preview.viewers.PdfSearchSnapshot
import com.example.ui.screens.InViewerSearchNavigation
import com.example.ui.preview.viewers.PreviewLargeTextSearchEffects
import com.example.ui.preview.chrome.PreviewFocusPill
import com.example.ui.preview.viewers.PreviewContent
import com.example.ui.preview.viewers.PreviewContentState
import com.example.ui.preview.viewers.PreviewContentActions
import com.example.ui.preview.chrome.PreviewTrayAction
import com.example.ui.preview.chrome.PreviewTrayKind
import com.example.ui.preview.chrome.PreviewTrayState
import com.example.utils.FilePrintHelper
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviewScreenSurface(
    viewModel: MainViewModel,
    fileState: MainViewModel.FileContentState,
    settings: SettingEntity,
    expandedZipPaths: Set<String>,
    activeFile: RecentFileEntity?,
    screenState: PreviewScreenState,
    isDarkTheme: Boolean,
    isHtmlFile: Boolean,
    isWebRenderActive: Boolean,
    htmlScriptsEnabled: Boolean,
    onEnableHtmlScripts: () -> Unit,
    isEditMode: Boolean,
    hasUnsavedEdits: Boolean,
    isSearchInViewerOpen: Boolean,
    inViewerSearchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isLargeTextSearchable: Boolean,
    isFullTextLoading: Boolean,
    docxViewerState: DocxViewerState,
    searchFieldFocusRequester: FocusRequester,
    goToNextMatch: () -> Unit,
    goToPrevMatch: () -> Unit,
    closeInViewerSearch: () -> Unit,
    toggleInViewerSearch: () -> Unit,
    handleBack: () -> Unit,
    context: Context,
    printScope: CoroutineScope,
    editUndoStack: TextUndoStack,
    editRedoStack: TextUndoStack,
    previewCanvasColor: Color,
    modifier: Modifier
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PreviewHeader(
                state = PreviewHeaderState(
                    visible = screenState.isBarsVisible,
                    title = fileState.fileOrNull?.name ?: "Preview",
                    searchOpen = isSearchInViewerOpen,
                    query = inViewerSearchQuery,
                    isPdfSearch = fileState is MainViewModel.FileContentState.PdfSuccess,
                    isLargeTextSearchable = isLargeTextSearchable,
                    isPdfSearchLoading = screenState.isPdfSearchLoading,
                    isFullTextLoading = isFullTextLoading,
                    pdfSearchError = screenState.pdfSearchError,
                    largeTextSearchError = screenState.largeTextSearchError,
                    totalMatches = screenState.totalMatchesCount,
                    searchResultsTruncated = screenState.searchResultsTruncated,
                    currentMatchIndex = screenState.currentMatchIndex,
                    pdfPageInfo = screenState.pdfSearchSnapshot.matches.getOrNull(screenState.currentMatchIndex)?.pageNumber?.let { " (p.$it)" }.orEmpty(),
                    navigableMatchCount = when {
                        fileState is MainViewModel.FileContentState.PdfSuccess -> screenState.pdfSearchSnapshot.matches.size
                        isLargeTextSearchable -> screenState.largeTextSearchMatches.size
                        activeFile?.extension?.lowercase() in setOf("docx", "doc") -> minOf(screenState.totalMatchesCount, 5_000)
                        activeFile?.extension?.lowercase() in setOf("txt", "md") -> minOf(screenState.totalMatchesCount, 1_000)
                        else -> screenState.totalMatchesCount
                    },
                    isEditMode = isEditMode
                ),
                actions = PreviewHeaderActions(
                    onNavigation = handleBack,
                    onDiscardEdits = { if (hasUnsavedEdits) screenState.showDiscardEditsDialog = true else viewModel.exitEditMode() },
                    onQueryChange = { value ->
                        onSearchQueryChange(value)
                        screenState.currentMatchIndex = 0
                        screenState.totalMatchesCount = 0
                        screenState.searchResultsTruncated = false
                        screenState.pdfSearchError = null
                        screenState.largeTextSearchMatches = emptyList()
                        screenState.largeTextSearchCompletedQuery = ""
                        screenState.largeTextSearchError = null
                        if (fileState is MainViewModel.FileContentState.PdfSuccess) {
                            screenState.isPdfSearchLoading = value.isNotBlank()
                            screenState.pdfSearchSnapshot = PdfSearchSnapshot()
                        }
                    },
                    onCloseSearch = { closeInViewerSearch() },
                    onClearSearch = {
                        onSearchQueryChange("")
                        screenState.currentMatchIndex = 0
                        screenState.totalMatchesCount = 0
                        screenState.searchResultsTruncated = false
                        screenState.pdfSearchSnapshot = PdfSearchSnapshot()
                        screenState.isPdfSearchLoading = false
                        screenState.pdfSearchError = null
                        screenState.largeTextSearchMatches = emptyList()
                        screenState.largeTextSearchCompletedQuery = ""
                        screenState.largeTextSearchError = null
                    },
                    onKeyboardSearch = { goToNextMatch() },
                    onPrevious = { goToPrevMatch() },
                    onNext = { goToNextMatch() },
                    menu = PreviewOverflowMenuActions(
                        share = { if (!isEditMode) activeFile?.let { shareFile(context, File(it.path), if (fileState is MainViewModel.FileContentState.DocxSuccess) "docx" else it.extension) } },
                        saveChanges = { if (hasUnsavedEdits) screenState.showSaveConfirmDialog = true else viewModel.exitEditMode() },
                        exportCopy = { screenState.showEditSaveAsDialog = true },
                        jumpToPage = { screenState.showJumpDialog = true },
                        openSystemViewer = { activeFile?.let { openPdfInBrowser(context, File(it.path)) } },
                        openOutline = { screenState.showOutlineBottomSheet = true },
                        openAppearance = { screenState.showAppearanceBottomSheet = true },
                        goToLine = { screenState.showJumpToLineDialog = true },
                        printOrExport = {
                            activeFile?.let { entity ->
                                val ext = entity.extension.lowercase()
                                when {
                                    ext == "pdf" -> com.example.utils.FilePrintHelper.printPdf(context, File(entity.path), entity.name)
                                    fileState is MainViewModel.FileContentState.DocxSuccess || fileState is MainViewModel.FileContentState.DocxPreviewLimitExceeded || ext in setOf("docx", "docm", "dotx", "dotm") -> docxViewerState.print()
                                    ext == "doc" -> com.example.utils.FilePrintHelper.printDocx(context, File(entity.path), entity.name)
                                    ext in setOf("png", "jpg", "jpeg", "webp", "gif") -> com.example.utils.FilePrintHelper.printImage(context, File(entity.path), entity.name)
                                    else -> {
                                        val textState = fileState as? MainViewModel.FileContentState.TextSuccess
                                        printScope.launch {
                                            val result = if (textState != null && !textState.truncated) Result.success(textState.content)
                                            else readBoundedPreviewText(File(entity.path), textState?.charsetName ?: "UTF-8")
                                            result.onSuccess { text ->
                                                if (ext == "md") com.example.utils.FilePrintHelper.printMarkdown(context, text, entity.name)
                                                else com.example.utils.FilePrintHelper.printTextOrMarkdown(context, text, entity.name)
                                            }.onFailure { showPreviewTextOperationError(context, it) }
                                        }
                                    }
                                }
                            }
                        },
                        properties = { screenState.showPropertiesDialog = true },
                        removeFromHistory = { activeFile?.let { viewModel.deleteRecentFile(it); handleBack() } }
                    )
                ),
                isPdf = fileState is MainViewModel.FileContentState.PdfSuccess,
                isDocx = fileState is MainViewModel.FileContentState.DocxSuccess,
                hasOutline = activeFile?.extension?.lowercase() in setOf("md", "markdown", "txt") && !isEditMode,
                isTextFile = activeFile?.extension?.lowercase() in setOf("txt", "md", "json", "xml", "html", "htm", "js", "ts", "css", "log", "yaml", "yml", "ini", "conf", "sh", "py", "java", "kt", "c", "cpp", "h", "sql", "csv", "tsv"),
                currentPage = screenState.currentPdfPage,
                pageCount = screenState.totalPdfPages,
                focusRequester = searchFieldFocusRequester
            )
        },
        containerColor = previewCanvasColor,
        modifier = modifier.testTag("file_preview_screen")
    ) { innerPadding ->
        val usesReaderGestures = activeFile?.extension?.lowercase() in setOf("pdf", "doc", "docx", "txt", "md")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!usesReaderGestures) {
                        Modifier.pointerInput(activeFile?.path) {
                            detectTapGestures(onDoubleTap = { screenState.isBarsVisible = !screenState.isBarsVisible })
                        }
                    } else Modifier
                )
                .background(previewCanvasColor)
                // Always respect the animated scaffold insets: the padding shrinks frame-by-frame
                // while the bars slide away, so focus mode no longer snaps the content full-bleed.
                .padding(innerPadding)
        ) {
            PreviewFocusPill(
                visible = !screenState.isBarsVisible,
                onBack = handleBack,
                onExitFocus = { screenState.isBarsVisible = true },
                modifier = Modifier.align(Alignment.TopStart)
            )

            PreviewContent(
                state = PreviewContentState(
                    fileState = fileState,
                    settings = settings,
                    expandedZipPaths = expandedZipPaths,
                    isDarkTheme = isDarkTheme,
                    isBarsVisible = screenState.isBarsVisible,
                    isHtmlFile = isHtmlFile,
                    isWebRenderActive = isWebRenderActive,
                    htmlScriptsEnabled = htmlScriptsEnabled,
                    isEditMode = isEditMode,
                    editTextValue = screenState.editTextValue,
                    fullPreviewText = screenState.fullPreviewText,
                    isFullTextLoading = isFullTextLoading,
                    largeTextSearchError = screenState.largeTextSearchError,
                    inViewerSearchQuery = inViewerSearchQuery,
                    searchTriggerNext = screenState.searchTriggerNext,
                    searchTriggerPrev = screenState.searchTriggerPrev,
                    viewerSyntaxTheme = screenState.viewerSyntaxTheme,
                    viewerFontSizeSp = screenState.viewerFontSizeSp,
                    viewerZoomPercent = screenState.viewerZoomPercent,
                    pendingScrollRequest = screenState.pendingScrollRequest,
                    pdfViewRotation = screenState.pdfViewRotation,
                    currentMatchIndex = screenState.currentMatchIndex,
                    scrollToPage = screenState.scrollToPage,
                    pageJumpToken = screenState.pageJumpToken,
                    currentPdfPage = screenState.currentPdfPage,
                    totalPdfPages = screenState.totalPdfPages,
                    docxViewerState = docxViewerState,
                    docxZoomFactor = screenState.docxZoomFactor
                ),
                actions = PreviewContentActions(
                    onBack = handleBack,
                    openArchive = { file, password -> viewModel.openFile(file, password) },
                    onEditTextChanged = { newValue ->
                        if (newValue.text != screenState.editTextValue.text) {
                            editUndoStack.push(screenState.editTextValue)
                            editRedoStack.clear()
                        }
                        screenState.editTextValue = newValue
                    },
                    enableHtmlScripts = onEnableHtmlScripts,
                    toggleBars = { screenState.isBarsVisible = !screenState.isBarsVisible },
                    onLargeTextSearchResultCount = { count, index, truncated ->
                        if (!isLargeTextSearchable) {
                            screenState.totalMatchesCount = count
                            screenState.currentMatchIndex = if (count > 0) index.coerceAtLeast(0) else 0
                            screenState.searchResultsTruncated = truncated
                        }
                    },
                    onScrollRequestHandled = { screenState.pendingScrollRequest = null },
                    saveSpreadsheet = { file, rows -> viewModel.saveSpreadsheetFile(file, rows) },
                    toggleZipExpanded = { path -> viewModel.toggleZipPathExpanded(path) },
                    openZipEntry = { file, node, password -> viewModel.openZipEntry(file, node, password) },
                    onPdfScrollHandled = { screenState.scrollToPage = null },
                    onPdfScrubJump = { page -> screenState.scrollToPage = page - 1; screenState.pageJumpToken++ },
                    onPdfSearchSnapshot = { snapshot, searching ->
                        screenState.pdfSearchSnapshot = snapshot
                        screenState.totalMatchesCount = snapshot.count
                        screenState.searchResultsTruncated = snapshot.truncated
                        screenState.isPdfSearchLoading = searching
                        screenState.pdfSearchError = null
                        if (!searching) screenState.currentMatchIndex = 0
                    },
                    onPdfSearchError = { message ->
                        screenState.pdfSearchError = message
                        screenState.isPdfSearchLoading = false
                    },
                    onPdfPageChanged = { page, total ->
                        screenState.currentPdfPage = page
                        screenState.totalPdfPages = total
                    },
                    onDocxSearchResultCount = { count, index, truncated ->
                        screenState.totalMatchesCount = count
                        screenState.currentMatchIndex = if (count > 0) index.coerceAtLeast(0) else 0
                        screenState.searchResultsTruncated = truncated
                    },
                    onDocxWebViewCreated = { screenState.docxPreviewWebView = it },
                    onDocxReady = { screenState.docxPreviewReady = true },
                    onDocxZoomChanged = { factor -> screenState.docxZoomFactor = factor },
                    onDocxRendererFallback = { fallback -> screenState.isDocxRenderFallback = fallback },
                    closePresentation = { viewModel.closeCurrentFile() }
                ),
                modifier = Modifier.fillMaxSize()
            )

            val isPdf = fileState is MainViewModel.FileContentState.PdfSuccess
            val isDocx = fileState is MainViewModel.FileContentState.DocxSuccess
            // Gate on the real state: legacy .doc files open in the hex viewer and must not
            // show the DOCX-specific reading tray.
            val isImage = fileState is MainViewModel.FileContentState.ImageSuccess
            val isText = fileState is MainViewModel.FileContentState.TextSuccess ||
                (activeFile?.extension?.lowercase() in setOf("txt", "md", "json", "xml", "html", "htm", "js", "ts", "css", "log", "yaml", "yml", "ini", "conf", "sh", "py", "java", "kt", "c", "cpp", "h", "sql", "csv", "tsv"))

            val showFloatingBar = isPdf || isDocx || isImage || isText

            if (showFloatingBar) {
                val trayKind = when {
                    isPdf -> PreviewTrayKind.PDF
                    isDocx -> PreviewTrayKind.DOCX
                    isImage -> PreviewTrayKind.IMAGE
                    else -> PreviewTrayKind.TEXT
                }
                val trayTextState = fileState as? MainViewModel.FileContentState.TextSuccess
                PreviewFloatingTray(
                    visible = screenState.isBarsVisible,
                    liftForIme = isEditMode,
                    state = PreviewTrayState(
                        kind = trayKind,
                        isEditMode = isEditMode,
                        isSearchOpen = isSearchInViewerOpen,
                        currentPage = screenState.currentPdfPage,
                        pageCount = screenState.totalPdfPages,
                        hasUnsavedEdits = hasUnsavedEdits,
                        hasUndo = editUndoStack.isNotEmpty,
                        hasRedo = editRedoStack.isNotEmpty,
                        canEdit = trayTextState?.editable == true
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter).background(Color.Transparent),
                    onAction = { action ->
                        when (action) {
                            PreviewTrayAction.JUMP_PAGE -> screenState.showJumpDialog = true
                            PreviewTrayAction.ROTATE -> context.findHostActivity()?.let { activity ->
                                activity.requestedOrientation = com.example.core.format.ScreenOrientationPolicy.toggleOrientation(
                                    activity.resources.configuration.orientation
                                )
                            }
                            PreviewTrayAction.FIND -> toggleInViewerSearch()
                            PreviewTrayAction.PRINT -> when (trayKind) {
                                PreviewTrayKind.PDF -> activeFile?.let { com.example.utils.FilePrintHelper.printPdf(context, File(it.path), it.name) }
                                PreviewTrayKind.DOCX -> docxViewerState.print()
                                PreviewTrayKind.IMAGE -> activeFile?.let { com.example.utils.FilePrintHelper.printImage(context, File(it.path), it.name) }
                                PreviewTrayKind.TEXT -> activeFile?.let { entity ->
                                    val ext = entity.extension.lowercase()
                                    printScope.launch {
                                        val result = if (trayTextState != null && !trayTextState.truncated) Result.success(trayTextState.content)
                                        else readBoundedPreviewText(File(entity.path), trayTextState?.charsetName ?: "UTF-8")
                                        result.onSuccess { content ->
                                            if (ext == "md") com.example.utils.FilePrintHelper.printMarkdown(context, content, entity.name)
                                            else com.example.utils.FilePrintHelper.printTextOrMarkdown(context, content, entity.name)
                                        }.onFailure { showPreviewTextOperationError(context, it) }
                                    }
                                }
                            }
                            PreviewTrayAction.SHARE -> activeFile?.let { shareFile(context, File(it.path), it.extension) }
                            PreviewTrayAction.EDIT_DOCUMENT -> screenState.showDocxEditComingSoon = true
                            PreviewTrayAction.UNDO -> if (editUndoStack.isNotEmpty) editUndoStack.pop()?.let { previous ->
                                editRedoStack.push(screenState.editTextValue)
                                screenState.editTextValue = previous
                            }
                            PreviewTrayAction.REDO -> if (editRedoStack.isNotEmpty) editRedoStack.pop()?.let { next ->
                                editUndoStack.push(screenState.editTextValue)
                                screenState.editTextValue = next
                            }
                            PreviewTrayAction.INDENT -> applyTabIndent(screenState.editTextValue.text, screenState.editTextValue.selection, settings.tabSize)?.let { result ->
                                editUndoStack.push(screenState.editTextValue)
                                editRedoStack.clear()
                                screenState.editTextValue = TextFieldValue(result.text, result.newSelection)
                            }
                            PreviewTrayAction.BOLD -> {
                                editUndoStack.push(screenState.editTextValue)
                                editRedoStack.clear()
                                applyMarkdownWrap(screenState.editTextValue.text, screenState.editTextValue.selection, "**").let { screenState.editTextValue = TextFieldValue(it.text, it.newSelection) }
                            }
                            PreviewTrayAction.ITALIC -> {
                                editUndoStack.push(screenState.editTextValue)
                                editRedoStack.clear()
                                applyMarkdownWrap(screenState.editTextValue.text, screenState.editTextValue.selection, "*").let { screenState.editTextValue = TextFieldValue(it.text, it.newSelection) }
                            }
                            PreviewTrayAction.HISTORY -> screenState.showEditHistoryDialog = true
                            PreviewTrayAction.SAVE -> if (hasUnsavedEdits) screenState.showSaveConfirmDialog = true else viewModel.exitEditMode()
                            PreviewTrayAction.JUMP_LINE -> screenState.showJumpToLineDialog = true
                            PreviewTrayAction.COPY -> activeFile?.let { entity ->
                                if (trayTextState != null && !trayTextState.truncated) copyPreviewTextToClipboard(context, entity.name, trayTextState.content)
                                else if (trayTextState != null) printScope.launch {
                                    readBoundedPreviewText(File(entity.path), trayTextState.charsetName)
                                        .onSuccess { copyPreviewTextToClipboard(context, entity.name, it) }
                                        .onFailure { showPreviewTextOperationError(context, it) }
                                }
                            }
                            PreviewTrayAction.ENTER_EDIT -> if (!viewModel.enterEditMode()) {
                                android.widget.Toast.makeText(context, "This file can't be edited here", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }


        }
    }

    PreviewDialogHost(
        state = PreviewDialogState(
            activeFile = activeFile,
            currentDocumentText = (fileState as? MainViewModel.FileContentState.TextSuccess)?.let { screenState.fullPreviewText ?: it.content },
            showProperties = screenState.showPropertiesDialog,
            showDocxEditNotice = screenState.showDocxEditComingSoon,
            showJumpToLine = screenState.showJumpToLineDialog,
            showJumpToPage = screenState.showJumpDialog,
            pendingLink = screenState.pendingLinkToOpen,
            showOutline = screenState.showOutlineBottomSheet,
            showAppearance = screenState.showAppearanceBottomSheet,
            isEditMode = isEditMode,
            showDiscard = screenState.showDiscardEditsDialog,
            showSave = screenState.showSaveConfirmDialog,
            showHistory = screenState.showEditHistoryDialog,
            showExport = screenState.showEditSaveAsDialog,
            viewerTheme = screenState.viewerSyntaxTheme,
            viewerFontSizeSp = screenState.viewerFontSizeSp,
            viewerZoomPercent = screenState.viewerZoomPercent,
            draft = screenState.editTextValue,
            history = editUndoStack.snapshot(),
            totalPages = screenState.totalPdfPages
        ),
        actions = PreviewDialogActions(
            dismissProperties = { screenState.showPropertiesDialog = false },
            dismissDocxEditNotice = { screenState.showDocxEditComingSoon = false },
            dismissJumpToLine = { screenState.showJumpToLineDialog = false },
            goToLine = { line ->
                val ext = activeFile?.extension?.lowercase().orEmpty()
                val content = (fileState as? MainViewModel.FileContentState.TextSuccess)?.let { screenState.fullPreviewText ?: it.content }.orEmpty()
                val headingIndex = if (ext in setOf("md", "markdown")) {
                    DocumentOutlineParser.parseHeadings(content).indexOfFirst { it.lineNumber == line }.takeIf { it >= 0 }
                } else null
                screenState.pendingScrollRequest = ViewerScrollRequest(lineNumber = line, headingIndex = headingIndex)
            },
            dismissJumpToPage = { screenState.showJumpDialog = false },
            goToPage = { page ->
                if (fileState is MainViewModel.FileContentState.DocxSuccess) docxViewerState.goToPage(page)
                screenState.scrollToPage = page - 1
                screenState.pageJumpToken++
            },
            dismissLink = { screenState.pendingLinkToOpen = null },
            dismissOutline = { screenState.showOutlineBottomSheet = false },
            dismissAppearance = { screenState.showAppearanceBottomSheet = false },
            selectOutlineLine = { line ->
                val content = (fileState as? MainViewModel.FileContentState.TextSuccess)?.let { screenState.fullPreviewText ?: it.content }.orEmpty()
                val markdown = activeFile?.extension?.lowercase() in setOf("md", "markdown")
                val headingIndex = if (markdown) DocumentOutlineParser.parseHeadings(content).indexOfFirst { it.lineNumber == line }.takeIf { it >= 0 } else null
                screenState.pendingScrollRequest = ViewerScrollRequest(lineNumber = line, headingIndex = headingIndex)
            },
            themeChanged = { screenState.viewerSyntaxTheme = it },
            fontSizeChanged = { screenState.viewerFontSizeSp = it },
            zoomChanged = { screenState.viewerZoomPercent = it },
            dismissDiscard = { screenState.showDiscardEditsDialog = false },
            exitEditMode = { screenState.showDiscardEditsDialog = false; viewModel.exitEditMode() },
            dismissSave = { screenState.showSaveConfirmDialog = false },
            saveDraft = {
                screenState.showSaveConfirmDialog = false
                activeFile?.let { viewModel.saveTextFile(it, screenState.editTextValue.text); screenState.editOriginalContent = screenState.editTextValue.text; viewModel.exitEditMode() }
            },
            dismissHistory = { screenState.showEditHistoryDialog = false },
            restoreHistory = { restored ->
                editUndoStack.push(screenState.editTextValue)
                editRedoStack.clear()
                screenState.editTextValue = restored
            },
            dismissExport = { screenState.showEditSaveAsDialog = false },
            exportCopy = { name -> activeFile?.let { viewModel.exportCopy(it, name, screenState.editTextValue.text); screenState.showEditSaveAsDialog = false } }
        )
    )
}
