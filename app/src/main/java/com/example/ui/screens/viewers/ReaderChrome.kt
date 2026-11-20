package com.example.ui.screens.viewers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/** Where the in-document viewer should scroll next (outline tap / go-to-line). */
data class ViewerScrollRequest(
    val lineNumber: Int,
    val headingIndex: Int? = null
)

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
    Surface(
        color = glass.fillStrong,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .height(48.dp)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
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
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear query",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                SearchMatchBadgeRow(
                    isPdfSearch = isPdfSearch,
                    isLargeTextSearchable = isLargeTextSearchable,
                    isPdfSearchLoading = isPdfSearchLoading,
                    isFullTextLoading = isFullTextLoading,
                    pdfSearchError = pdfSearchError,
                    largeTextSearchError = largeTextSearchError,
                    totalMatchesCount = totalMatchesCount,
                    searchResultsTruncated = searchResultsTruncated,
                    currentMatchIndex = currentMatchIndex,
                    pdfPageInfo = pdfPageInfo,
                    navigableMatchCount = navigableMatchCount,
                    onPrevious = onPrevious,
                    onNext = onNext
                )
            }
            androidx.compose.material3.HorizontalDivider(color = glass.strokeEnd)
        }
    }
}

/** Themed match counter + prev/next controls for the in-document search overlay (v1.3.0). */
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
    val hasError = (pdfSearchError != null && isPdfSearch) ||
        (largeTextSearchError != null && isLargeTextSearchable)
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
        IconButton(
            onClick = onPrevious,
            enabled = totalMatchesCount > 0 && !isPdfSearchLoading,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match", modifier = Modifier.size(20.dp))
        }
        IconButton(
            onClick = onNext,
            enabled = totalMatchesCount > 0 && !isPdfSearchLoading,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match", modifier = Modifier.size(20.dp))
        }
    }
}
