package com.example.ui.preview.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.component.ClaudeAppBar

/** Render-only header state and explicit actions shared by the filename and find modes. */
data class PreviewHeaderState(
    val visible: Boolean,
    val title: String,
    val searchOpen: Boolean,
    val query: String,
    val isPdfSearch: Boolean,
    val isLargeTextSearchable: Boolean,
    val isPdfSearchLoading: Boolean,
    val isFullTextLoading: Boolean,
    val pdfSearchError: String?,
    val largeTextSearchError: String?,
    val totalMatches: Int,
    val searchResultsTruncated: Boolean,
    val currentMatchIndex: Int,
    val pdfPageInfo: String,
    val navigableMatchCount: Int,
    val isEditMode: Boolean
)

data class PreviewHeaderActions(
    val onNavigation: () -> Unit,
    val onDiscardEdits: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onCloseSearch: () -> Unit,
    val onClearSearch: () -> Unit,
    val onKeyboardSearch: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val menu: PreviewOverflowMenuActions
)

@Composable
fun PreviewHeader(
    state: PreviewHeaderState,
    actions: PreviewHeaderActions,
    isPdf: Boolean,
    isDocx: Boolean,
    hasOutline: Boolean,
    isTextFile: Boolean,
    currentPage: Int,
    pageCount: Int,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.visible,
        enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(260, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(220)),
        modifier = modifier.statusBarsPadding()
    ) {
        if (state.searchOpen) {
            ViewerFindBarHeader(
                query = state.query,
                onQueryChange = actions.onQueryChange,
                onClose = actions.onCloseSearch,
                onClear = actions.onClearSearch,
                onKeyboardSearch = actions.onKeyboardSearch,
                focusRequester = focusRequester,
                isPdfSearch = state.isPdfSearch,
                isLargeTextSearchable = state.isLargeTextSearchable,
                isPdfSearchLoading = state.isPdfSearchLoading,
                isFullTextLoading = state.isFullTextLoading,
                pdfSearchError = state.pdfSearchError,
                largeTextSearchError = state.largeTextSearchError,
                totalMatchesCount = state.totalMatches,
                searchResultsTruncated = state.searchResultsTruncated,
                currentMatchIndex = state.currentMatchIndex,
                pdfPageInfo = state.pdfPageInfo,
                navigableMatchCount = state.navigableMatchCount,
                onPrevious = actions.onPrevious,
                onNext = actions.onNext
            )
        } else {
            var expanded by remember { mutableStateOf(false) }
            ClaudeAppBar(
                title = state.title,
                onNavIconClick = if (state.isEditMode) actions.onDiscardEdits else actions.onNavigation,
                navIcon = if (state.isEditMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                actions = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    PreviewOverflowMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        isPdf = isPdf,
                        isDocx = isDocx,
                        isEditMode = state.isEditMode,
                        hasOutline = hasOutline,
                        isTextFile = isTextFile,
                        currentPage = currentPage,
                        pageCount = pageCount,
                        actions = actions.menu
                    )
                }
            )
        }
    }
}

/** Lower-level shell retained for callers that only need the standard preview title bar. */
@Composable
fun PreviewTopBar(
    title: String,
    onNavIconClick: (() -> Unit)?,
    navIcon: ImageVector?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) = ClaudeAppBar(title, onNavIconClick, navIcon, actions, modifier)
