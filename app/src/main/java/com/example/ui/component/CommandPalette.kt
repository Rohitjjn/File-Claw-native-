package com.example.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.search.FileSearchRanker
import com.example.data.RecentFileEntity
import com.example.ui.theme.Eyebrow
import com.example.ui.theme.LocalGlass

/** Palette-visible actions (user-visible titles live in strings.xml; keywords stay code-local). */
enum class CommandPaletteAction(
    val id: String,
    val titleRes: Int,
    val keywords: String,
) {
    TOGGLE_THEME("toggle_theme", R.string.action_toggle_theme, "toggle_theme dark light theme appearance night day"),
    OPEN_SETTINGS("open_settings", R.string.action_open_settings, "open_settings settings preferences options"),
    OPEN_SEARCH("open_search", R.string.action_open_search, "open_search search find query"),
    OPEN_ALL_FILES("open_all_files", R.string.action_open_all_files, "open_all_files all files browse browse"),
    CLEAR_RECENTS("clear_recents", R.string.action_clear_recents, "clear_recents clear recents history delete list"),
}

/**
 * Quick Open / Command Palette (docs/UI_FRONTIER.md §5.1). Fuzzy finder over the user's recent
 * files plus palette-global actions. Opened with Ctrl/Cmd+K from the app root; closes on scrim,
 * back, or after a selection. English keywords are a code-level search index, not UI text.
 */
@Composable
fun CommandPalette(
    recentFiles: List<RecentFileEntity>,
    onOpenFile: (RecentFileEntity) -> Unit,
    onAction: (CommandPaletteAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val showAll = query.isBlank()
    val files = remember(recentFiles, query) {
        if (showAll) recentFiles.take(8)
        else FileSearchRanker.rank(recentFiles, query, nameOf = { it.name }, limit = 12)
    }
    val actions = remember(query) {
        if (showAll) CommandPaletteAction.entries.toList()
        else FileSearchRanker.rank(CommandPaletteAction.entries.toList(), query, nameOf = { it.keywords }, limit = 8)
    }
    val filesTruncated = !showAll && FileSearchRanker.isTruncated(
        recentFiles.count { FileSearchRanker.relevanceScore(query, it.name) > 0 },
        files.size
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true),
    ) {
        val glass = com.example.ui.theme.LocalGlass.current
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .padding(top = 64.dp),
            shape = RoundedCornerShape(16.dp),
            color = glass.fillStrong,
            border = BorderStroke(1.dp, glass.strokeStart.copy(alpha = 0.85f)),
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Query field
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.palette_placeholder)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = {
                            val firstFile = files.firstOrNull()
                            val firstAction = actions.firstOrNull()
                            when {
                                firstFile != null -> onOpenFile(firstFile)
                                firstAction != null -> onAction(firstAction)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )

                LazyColumn(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(max = 420.dp)
                ) {
                    if (files.isNotEmpty()) {
                        item(key = "header_files") {
                            Text(
                                stringResource(R.string.palette_section_files),
                                style = Eyebrow,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        items(files, key = { "file_${it.id}" }) { entity ->
                            PaletteRow(
                                icon = { Icon(Icons.Outlined.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                title = entity.name,
                                subtitle = entity.path,
                                onClick = { onOpenFile(entity) },
                            )
                        }
                        if (filesTruncated) {
                            item(key = "files_truncated") {
                                Text(
                                    stringResource(R.string.palette_show_more),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    if (actions.isNotEmpty()) {
                        item(key = "header_actions") {
                            Text(
                                stringResource(R.string.palette_section_actions),
                                style = Eyebrow,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        items(actions, key = { "action_${it.id}" }) { action ->
                            PaletteRow(
                                icon = { Icon(Icons.Outlined.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                title = stringResource(action.titleRes),
                                subtitle = null,
                                onClick = { onAction(action) },
                            )
                        }
                    }
                    if (files.isEmpty() && actions.isEmpty()) {
                        item(key = "no_matches") {
                            Text(
                                stringResource(R.string.palette_no_matches),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
