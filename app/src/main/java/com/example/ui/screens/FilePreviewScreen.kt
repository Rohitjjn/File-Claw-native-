package com.example.ui.screens

import com.example.ui.preview.docx.DocxPrintController
import com.example.ui.preview.chrome.ViewerFindBarHeader
import com.example.ui.preview.chrome.PreviewOverflowMenu
import com.example.ui.preview.chrome.PreviewOverflowMenuActions
import com.example.ui.preview.chrome.PreviewHeader
import com.example.ui.preview.chrome.PreviewHeaderState
import com.example.ui.preview.chrome.PreviewHeaderActions
import com.example.ui.preview.chrome.PreviewFloatingTray
import com.example.ui.preview.chrome.PreviewTrayAction
import com.example.ui.preview.chrome.PreviewTrayKind
import com.example.ui.preview.chrome.PreviewTrayState
import com.example.ui.preview.chrome.PreviewDialogHost
import com.example.ui.preview.chrome.PreviewDialogState
import com.example.ui.preview.chrome.PreviewDialogActions
import com.example.ui.preview.chrome.PreviewWindowEffects
import com.example.ui.preview.chrome.PreviewFocusPill
import com.example.ui.preview.chrome.rememberPreviewScreenState
import com.example.ui.preview.viewers.PreviewScreenSurface
import com.example.ui.preview.viewers.*
import com.example.ui.preview.viewers.ViewerScrollRequest
import com.example.ui.preview.viewers.PreviewLargeTextSearchEffects
import com.example.ui.preview.operations.readBoundedPreviewText
import com.example.ui.preview.operations.copyPreviewTextToClipboard
import com.example.ui.preview.operations.showPreviewTextOperationError
import com.example.ui.preview.editing.TextUndoStack
import android.app.Activity
import android.content.Context

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.io.TextLoader
import com.example.core.io.TextWindowReader
import com.example.core.text.TextFileSearchEngine
import com.example.core.text.TextFileSearchMatch
import com.example.data.RecentFileEntity
import com.example.viewmodel.MainViewModel
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.component.DocumentOutlineParser
import com.example.ui.component.ViewerSyntaxTheme
import com.example.ui.component.ViewerAppearancePrefs
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription

/** Where the in-document viewer should scroll next (outline tap / go-to-line). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onNavigateToEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileState by viewModel.currentFileState.collectAsState()
    val expandedZipPaths by viewModel.expandedZipPaths.collectAsState()
    val settings by viewModel.settingsState.collectAsState()

    val activeFile = remember(fileState) { fileState.fileOrNull }
    val screenState = rememberPreviewScreenState()
    val isDarkTheme = MaterialTheme.colorScheme.background == com.example.ui.theme.ClaudeOnyx


    val isHtmlFile = remember(activeFile) {
        activeFile?.extension?.lowercase() in setOf("html", "htm")
    }

    var isWebRenderActive by remember(activeFile) {
        mutableStateOf(true)
    }

    // HTML files are untrusted input: scripts stay off until the user opts in for this file.
    var htmlScriptsEnabled by remember(activeFile) { mutableStateOf(false) }

    val canBeRenderedInWeb = remember(activeFile) {
        activeFile?.extension?.lowercase() in setOf("html", "htm", "js", "ts", "css", "xml", "txt", "json", "md")
    }


    // Loop 14: PDF view rotation in 90-degree steps (toolbar Rotate button). Pure
    // viewer state — the PDF.js document session is never reloaded, so password
    // protected files are asked once per open session. PDFs always display in
    // light mode now, so the night-mode state is DOCX-only and keeps its
    // system-theme seed for documents that still support dark mode.

    val isDocxPreview = activeFile?.extension?.lowercase() == "docx"
    val previewCanvasColor = if (isDocxPreview) Color.White else MaterialTheme.colorScheme.background
    // Loop 14: bumped on every explicit jump (scrubber release, Go-to-Page dialog) so
    // re-navigating to the SAME page number still re-fires the WebView navigation
    // effect (a repeated identical Int would not).

    // --- Inline edit mode state (MD/TXT): the preview screen itself becomes the editor ---
    val isEditMode by viewModel.isEditMode.collectAsState()
    val editUndoStack = remember { TextUndoStack(50) }
    val editRedoStack = remember { TextUndoStack(50) }
    val hasUnsavedEdits = isEditMode && screenState.editTextValue.text != screenState.editOriginalContent

    // --- Viewer appearance state (MD/TXT): theme, font size and browser-style zoom ---

    var isSearchInDocumentOpen by rememberSaveable { mutableStateOf(false) }
    var inViewerSearchQuery by rememberSaveable { mutableStateOf("") }
    val docxViewerState = com.example.ui.preview.docx.rememberDocxViewerState(activeFile?.path)
    // True when the DOCX renderer fell back to the pure-Kotlin text extractor: the
    // WebView zoom pipeline does not exist there, so dead zoom controls must be hidden.

    LaunchedEffect(activeFile?.path) {
        screenState.resetForFile()
        inViewerSearchQuery = ""
        isSearchInDocumentOpen = false
    }

    val context = LocalContext.current
    val printScope = rememberCoroutineScope()

    LaunchedEffect(activeFile?.path, screenState.docxPreviewReady, screenState.docxPreviewWebView) {
        docxViewerState.installPrintAction {
            activeFile?.let { entity ->
                DocxPrintController.print(
                    context = context,
                    file = File(entity.path),
                    title = entity.name,
                    preview = screenState.docxPreviewWebView,
                    previewReady = screenState.docxPreviewReady
                )
            }
        }
    }

    // Seed the inline editor with the currently displayed content every time edit mode starts.
    LaunchedEffect(isEditMode) {
        if (isEditMode) {
            val seedContent = (fileState as? MainViewModel.FileContentState.TextSuccess)?.content.orEmpty()
            screenState.editTextValue = TextFieldValue(seedContent)
            screenState.editOriginalContent = seedContent
            editUndoStack.clear()
            editRedoStack.clear()
        }
    }

    // Load persisted viewer appearance preferences once per screen instance.
    LaunchedEffect(Unit) {
        screenState.viewerSyntaxTheme = ViewerAppearancePrefs.getTheme(context)
        screenState.viewerFontSizeSp = ViewerAppearancePrefs.getFontSize(context)
        screenState.viewerZoomPercent = ViewerAppearancePrefs.getZoomPercent(context)
    }

    // Auto-focus the in-document search field (and raise the keyboard) when the overlay opens.
    val searchFieldFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(isSearchInDocumentOpen, screenState.isBarsVisible) {
        if (isSearchInDocumentOpen && screenState.isBarsVisible) {
            delay(220)
            searchFieldFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Restore previously opened file state on recreate
    // Attempted once per screen instance (process-death recovery). Without the flag, "Remove from history"
    // set the state to Idle and this effect immediately re-opened the same file.
    var restoreAttempted by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(fileState) {
        if (fileState is MainViewModel.FileContentState.Idle && !restoreAttempted) {
            restoreAttempted = true
            val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val lastPath = prefs.getString("last_previewed_file_path", null)
            if (lastPath != null) {
                val file = java.io.File(lastPath)
                if (file.exists()) {
                    val entity = RecentFileEntity(
                        path = file.absolutePath,
                        name = file.name,
                        size = file.length(),
                        extension = file.extension.lowercase(),
                        lastOpened = System.currentTimeMillis()
                    )
                    viewModel.openFile(entity)
                }
            }
        }
    }

    val isLargeTextSearchable = activeFile?.extension?.lowercase() in setOf("txt", "md") &&
        (fileState as? MainViewModel.FileContentState.TextSuccess)?.truncated == true

    PreviewLargeTextSearchEffects(
        filePath = activeFile?.path,
        fileState = fileState,
        query = inViewerSearchQuery,
        searchable = isLargeTextSearchable,
        completedQuery = screenState.largeTextSearchCompletedQuery,
        matches = screenState.largeTextSearchMatches,
        selectedMatchIndex = screenState.currentMatchIndex,
        onPreviewTextChanged = { screenState.fullPreviewText = it },
        onSearchReset = {
            screenState.largeTextSearchMatches = emptyList()
            screenState.largeTextSearchCompletedQuery = ""
            screenState.isLargeTextSearchLoading = false
            screenState.largeTextSearchError = null
        },
        onLoadingChanged = { screenState.isLargeTextSearchLoading = it },
        onSearchResults = { matches, query, count, truncated ->
            screenState.largeTextSearchMatches = matches
            screenState.largeTextSearchCompletedQuery = query
            screenState.totalMatchesCount = count
            screenState.searchResultsTruncated = truncated
            screenState.currentMatchIndex = 0
        },
        onSearchError = { message, count ->
            screenState.largeTextSearchError = message
            if (count != null) screenState.totalMatchesCount = count
        }
    )

    val isFullTextLoading = isLargeTextSearchable && inViewerSearchQuery.isNotBlank() && screenState.isLargeTextSearchLoading

    // --- In-document find pipeline (Loop 10): one shared owner for header + keyboard ---
    // Navigation lives in the screen scope so the header find bar and the IME Search
    // action provably call the same logic (the old lambdas were scoped inside the
    // floating overlay, which Bug 1 removes).
    val goToNextMatch = {
        if (fileState is MainViewModel.FileContentState.PdfSuccess) {
            val next = InViewerSearchNavigation.nextIndex(screenState.currentMatchIndex, screenState.pdfSearchSnapshot.matches.size)
            if (next != null) {
                screenState.currentMatchIndex = next
                screenState.scrollToPage = screenState.pdfSearchSnapshot.matches[next].pageNumber - 1
            }
        } else if (isLargeTextSearchable) {
            val next = InViewerSearchNavigation.nextIndex(screenState.currentMatchIndex, screenState.largeTextSearchMatches.size)
            if (next != null) screenState.currentMatchIndex = next
        } else {
            if (fileState is MainViewModel.FileContentState.DocxSuccess) {
                docxViewerState.findNext()
                screenState.searchTriggerNext = docxViewerState.nextSearchRequest
            } else screenState.searchTriggerNext++
        }
        Unit
    }
    val goToPrevMatch = {
        if (fileState is MainViewModel.FileContentState.PdfSuccess) {
            val prev = InViewerSearchNavigation.prevIndex(screenState.currentMatchIndex, screenState.pdfSearchSnapshot.matches.size)
            if (prev != null) {
                screenState.currentMatchIndex = prev
                screenState.scrollToPage = screenState.pdfSearchSnapshot.matches[prev].pageNumber - 1
            }
        } else if (isLargeTextSearchable) {
            val prev = InViewerSearchNavigation.prevIndex(screenState.currentMatchIndex, screenState.largeTextSearchMatches.size)
            if (prev != null) screenState.currentMatchIndex = prev
        } else {
            if (fileState is MainViewModel.FileContentState.DocxSuccess) {
                docxViewerState.findPrevious()
                screenState.searchTriggerPrev = docxViewerState.previousSearchRequest
            } else screenState.searchTriggerPrev++
        }
        Unit
    }
    // Closing find mode hides the keyboard, restores the normal app bar and clears the
    // query so every engine's existing empty-query path removes stale highlights.
    val closeInViewerSearch = {
        keyboardController?.hide()
        isSearchInDocumentOpen = false
        inViewerSearchQuery = ""
        screenState.pdfSearchSnapshot = PdfSearchSnapshot()
        screenState.isPdfSearchLoading = false
        screenState.pdfSearchError = null
        screenState.largeTextSearchMatches = emptyList()
        screenState.largeTextSearchCompletedQuery = ""
        screenState.largeTextSearchError = null
        screenState.searchResultsTruncated = false
        screenState.totalMatchesCount = 0
        screenState.currentMatchIndex = 0
        Unit
    }
    val toggleInViewerSearch = {
        if (isSearchInDocumentOpen) {
            closeInViewerSearch()
        } else {
            // The find bar lives in the header: reveal the bars when opening from focus mode.
            screenState.isBarsVisible = true
            isSearchInDocumentOpen = true
        }
        Unit
    }

    val handleBack = {
        val parentPath = activeFile?.parentZipPath
        if (parentPath != null) {
            viewModel.openParentZip(parentPath)
        } else {
            onBackClick()
        }
    }

    androidx.activity.compose.BackHandler {
        if (isSearchInDocumentOpen) {
            // Deliberate Chrome-style behaviour: back closes find mode first.
            closeInViewerSearch()
        } else if (isEditMode) {
            if (hasUnsavedEdits) screenState.showDiscardEditsDialog = true else viewModel.exitEditMode()
        } else {
            handleBack()
        }
    }

    PreviewWindowEffects(
        activity = context as? android.app.Activity,
        extension = activeFile?.extension,
        isDarkTheme = isDarkTheme,
        barsVisible = screenState.isBarsVisible
    )

    PreviewScreenSurface(
        viewModel = viewModel, fileState = fileState, settings = settings, expandedZipPaths = expandedZipPaths,
        activeFile = activeFile, screenState = screenState, isDarkTheme = isDarkTheme,
        isHtmlFile = isHtmlFile, isWebRenderActive = isWebRenderActive, htmlScriptsEnabled = htmlScriptsEnabled,
        isEditMode = isEditMode, hasUnsavedEdits = hasUnsavedEdits, isSearchInViewerOpen = isSearchInDocumentOpen,
        inViewerSearchQuery = inViewerSearchQuery, isLargeTextSearchable = isLargeTextSearchable,
        isFullTextLoading = isFullTextLoading, docxViewerState = docxViewerState,
        searchFieldFocusRequester = searchFieldFocusRequester, goToNextMatch = goToNextMatch,
        goToPrevMatch = goToPrevMatch, closeInViewerSearch = closeInViewerSearch,
        toggleInViewerSearch = toggleInViewerSearch, handleBack = handleBack, context = context,
        printScope = printScope, editUndoStack = editUndoStack, editRedoStack = editRedoStack,
        previewCanvasColor = previewCanvasColor, modifier = modifier
    )
}
