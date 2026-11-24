package com.example.ui.preview.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Chrome-style find bar that replaces the normal app bar while in-document search is
 * active. One compact header row: close control, focused query field, match badge and clear.
 */
@Composable
internal fun ViewerFindBarHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onKeyboardSearch: () -> Unit,
    focusRequester: FocusRequester,
    isPdfSearch: Boolean,
    isLargeTextSearchable: Boolean,
    isPdfSearchLoading: Boolean,
    isFullTextLoading: Boolean,
    pdfSearchError: String?,
    largeTextSearchError: String?,
    totalMatchesCount: Int,
    searchResultsTruncated: Boolean,
    currentMatchIndex: Int,
    pdfPageInfo: String,
    navigableMatchCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val glass = com.example.ui.theme.LocalGlass.current
    Surface(color = glass.fillStrong, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp).height(48.dp)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search", modifier = Modifier.size(20.dp))
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Find in document...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            maxLines = 1
                        )
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onKeyboardSearch() }),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                }
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(140))
                ) {
                    IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Clear query", modifier = Modifier.size(16.dp))
                    }
                }
                SearchMatchBadgeRow(
                    isPdfSearch, isLargeTextSearchable, isPdfSearchLoading, isFullTextLoading,
                    pdfSearchError, largeTextSearchError, totalMatchesCount, searchResultsTruncated,
                    currentMatchIndex, pdfPageInfo, navigableMatchCount, onPrevious, onNext
                )
            }
            androidx.compose.material3.HorizontalDivider(color = glass.strokeEnd)
        }
    }
}

@Composable
private fun SearchMatchBadgeRow(
    isPdfSearch: Boolean,
    isLargeTextSearchable: Boolean,
    isPdfSearchLoading: Boolean,
    isFullTextLoading: Boolean,
    pdfSearchError: String?,
    largeTextSearchError: String?,
    totalMatchesCount: Int,
    searchResultsTruncated: Boolean,
    currentMatchIndex: Int,
    pdfPageInfo: String,
    navigableMatchCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val hasError = (pdfSearchError != null && isPdfSearch) || (largeTextSearchError != null && isLargeTextSearchable)
    val matchLabel = when {
        isPdfSearchLoading -> "Searching…"
        isFullTextLoading -> "Searching full file…"
        hasError -> "Search unavailable"
        totalMatchesCount > 0 && searchResultsTruncated -> "${currentMatchIndex + 1}/$navigableMatchCount of $totalMatchesCount$pdfPageInfo"
        totalMatchesCount > 0 -> "${currentMatchIndex + 1}/$totalMatchesCount$pdfPageInfo"
        else -> "0 / 0"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = when {
                hasError -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                totalMatchesCount > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        ) {
            Text(
                text = matchLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    hasError -> MaterialTheme.colorScheme.error
                    totalMatchesCount > 0 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
        IconButton(onClick = onPrevious, enabled = totalMatchesCount > 0 && !isPdfSearchLoading, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, "Previous match", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onNext, enabled = totalMatchesCount > 0 && !isPdfSearchLoading, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, "Next match", modifier = Modifier.size(20.dp))
        }
    }
}
