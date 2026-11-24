package com.example.ui.preview.viewers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.core.io.TextWindowReader
import com.example.core.text.TextFileSearchEngine
import com.example.core.text.TextFileSearchMatch
import com.example.viewmodel.MainViewModel
import java.io.File
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Owns bounded full-file search IO and selected-match excerpt loading for text preview routes. */
@Composable
fun PreviewLargeTextSearchEffects(
    filePath: String?,
    fileState: MainViewModel.FileContentState,
    query: String,
    searchable: Boolean,
    completedQuery: String,
    matches: List<TextFileSearchMatch>,
    selectedMatchIndex: Int,
    onPreviewTextChanged: (String?) -> Unit,
    onSearchReset: () -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onSearchResults: (List<TextFileSearchMatch>, String, Int, Boolean) -> Unit,
    onSearchError: (String?, Int?) -> Unit
) {
    LaunchedEffect(filePath, fileState) {
        val text = fileState as? MainViewModel.FileContentState.TextSuccess
        onPreviewTextChanged(if (text != null && text.file.extension.lowercase() in setOf("txt", "md")) text.content else null)
    }

    LaunchedEffect(filePath, query, searchable, fileState) {
        val text = fileState as? MainViewModel.FileContentState.TextSuccess
        if (!searchable || text == null) {
            onSearchReset()
            return@LaunchedEffect
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            onSearchReset()
            onPreviewTextChanged(text.content)
            return@LaunchedEffect
        }
        onSearchReset()
        onLoadingChanged(true)
        onPreviewTextChanged(text.content)
        delay(180)
        val searchContext = kotlin.coroutines.coroutineContext
        try {
            val charset = Charset.forName(text.charsetName)
            val result = withContext(Dispatchers.IO) {
                TextFileSearchEngine.search(
                    File(text.file.path), trimmed, charset = charset,
                    shouldContinue = { searchContext.isActive }
                )
            }
            onSearchResults(result.matches, trimmed, result.matchCount, result.truncated)
            onLoadingChanged(false)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onSearchError(error.message ?: "Could not search the complete file", 0)
            onLoadingChanged(false)
        }
    }

    LaunchedEffect(filePath, query, completedQuery, matches, selectedMatchIndex) {
        if (!searchable || query.isBlank() || completedQuery != query.trim() || matches.isEmpty()) return@LaunchedEffect
        val text = fileState as? MainViewModel.FileContentState.TextSuccess ?: return@LaunchedEffect
        val match = matches.getOrNull(selectedMatchIndex) ?: return@LaunchedEffect
        try {
            val charset = Charset.forName(text.charsetName)
            val window = withContext(Dispatchers.IO) {
                TextWindowReader.read(File(text.file.path), match.byteOffset, maxBytes = 250_000, charset = charset)
            }
            onPreviewTextChanged(window.text)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            onSearchError(error.message ?: "Could not open the selected search result", null)
        }
    }
}
