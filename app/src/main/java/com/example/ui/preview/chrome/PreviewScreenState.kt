package com.example.ui.preview.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.component.ViewerSyntaxTheme
import com.example.ui.preview.docx.DocxViewerState
import com.example.ui.preview.viewers.PdfSearchSnapshot
import com.example.core.text.TextFileSearchMatch
import com.example.ui.preview.viewers.ViewerScrollRequest

/** Mutable UI-only state owned by FilePreviewScreen and shared with its extracted siblings. */
@Stable
class PreviewScreenState {
    var isBarsVisible by mutableStateOf(true)
    var currentPdfPage by mutableIntStateOf(1)
    var totalPdfPages by mutableIntStateOf(0)
    var showPropertiesDialog by mutableStateOf(false)
    var showDocxEditComingSoon by mutableStateOf(false)
    var pdfViewRotation by mutableIntStateOf(0)
    var scrollToPage by mutableStateOf<Int?>(null)
    var pageJumpToken by mutableIntStateOf(0)
    var pendingLinkToOpen by mutableStateOf<String?>(null)
    var showJumpDialog by mutableStateOf(false)
    var showJumpToLineDialog by mutableStateOf(false)
    var showOutlineBottomSheet by mutableStateOf(false)
    var showAppearanceBottomSheet by mutableStateOf(false)
    var searchTriggerNext by mutableIntStateOf(0)
    var searchTriggerPrev by mutableIntStateOf(0)
    var editTextValue by mutableStateOf(TextFieldValue(""))
    var editOriginalContent by mutableStateOf("")
    var showEditHistoryDialog by mutableStateOf(false)
    var showDiscardEditsDialog by mutableStateOf(false)
    var showSaveConfirmDialog by mutableStateOf(false)
    var showEditSaveAsDialog by mutableStateOf(false)
    var viewerSyntaxTheme by mutableStateOf(ViewerSyntaxTheme.AUTO)
    var viewerFontSizeSp by mutableIntStateOf(16)
    var viewerZoomPercent by mutableIntStateOf(100)
    var pendingScrollRequest by mutableStateOf<ViewerScrollRequest?>(null)
    var currentMatchIndex by mutableIntStateOf(0)
    var totalMatchesCount by mutableIntStateOf(0)
    var searchResultsTruncated by mutableStateOf(false)
    var isPdfSearchLoading by mutableStateOf(false)
    var pdfSearchError by mutableStateOf<String?>(null)
    var pdfSearchSnapshot by mutableStateOf(PdfSearchSnapshot())
    var docxPreviewWebView by mutableStateOf<android.webkit.WebView?>(null)
    var docxPreviewReady by mutableStateOf(false)
    var fullPreviewText by mutableStateOf<String?>(null)
    var largeTextSearchMatches by mutableStateOf<List<TextFileSearchMatch>>(emptyList())
    var largeTextSearchCompletedQuery by mutableStateOf("")
    var isLargeTextSearchLoading by mutableStateOf(false)
    var largeTextSearchError by mutableStateOf<String?>(null)
    var isDocxRenderFallback by mutableStateOf(false)

    fun resetForFile() {
        isBarsVisible = true
        showDocxEditComingSoon = false
        pdfViewRotation = 0
        searchTriggerNext = 0
        searchTriggerPrev = 0
        currentMatchIndex = 0
        totalMatchesCount = 0
        searchResultsTruncated = false
        isPdfSearchLoading = false
        pdfSearchError = null
        pdfSearchSnapshot = PdfSearchSnapshot()
        docxPreviewReady = false
        docxPreviewWebView = null
        largeTextSearchMatches = emptyList()
        largeTextSearchCompletedQuery = ""
        isLargeTextSearchLoading = false
        largeTextSearchError = null
        fullPreviewText = null
        isDocxRenderFallback = false
    }
}

@Composable
fun rememberPreviewScreenState(): PreviewScreenState = remember { PreviewScreenState() }
