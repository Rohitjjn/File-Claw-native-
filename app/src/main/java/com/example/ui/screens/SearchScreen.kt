package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RecentFileEntity
import com.example.ui.component.ClaudeAppBar
import com.example.ui.component.ClaudeCard
import com.example.ui.component.FileIcon
import com.example.ui.component.premiumLoadingPulse
import com.example.ui.theme.ClaudeMutedText
import com.example.viewmodel.MainViewModel
import java.io.File

import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onNavigateToPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val historyResults by viewModel.recentFilesState.collectAsState()
    val deviceResults by viewModel.deviceSearchResults.collectAsState()
    val isSearchingDevice by viewModel.isSearchingDevice.collectAsState()
    val isIndexing by viewModel.isIndexing.collectAsState()
    val searchTermsHistory by viewModel.searchTermsHistory.collectAsState()

    var showAllHistory by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        viewModel.triggerStorageIndexRefresh()
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.updateSearchQuery("")
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextField(
                        value = searchQuery,
                        onValueChange = {
                            viewModel.updateSearchQuery(it)
                            viewModel.searchLocalFiles(it)
                        },
                        placeholder = { Text("Search files & storage...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { 
                                    viewModel.updateSearchQuery("")
                                    viewModel.searchLocalFiles("")
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear Input")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { 
                            if (searchQuery.isNotBlank()) viewModel.addSearchTermToHistory(searchQuery)
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("search_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (searchQuery.isEmpty()) {
                if (searchTermsHistory.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.TravelExplore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(96.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Search files & storage",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp)
                    ) {
                        items(searchTermsHistory, key = { it }) { term ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateSearchQuery(term)
                                        viewModel.searchLocalFiles(term)
                                        viewModel.addSearchTermToHistory(term)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "History",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = term,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.removeSearchTermFromHistory(term) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val limitCount = if (showAllHistory) historyResults.size else 3
                val displayedHistory by remember(historyResults, limitCount) {
                    derivedStateOf { historyResults.take(limitCount) }
                }

                LazyColumn(
                    state = rememberLazyListState(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. RECENT HISTORY MATCHES (ORANGE COLOR SCHEME)
                    item(contentType = "HistoryHeader") {
                        Text(
                            text = "RECENT HISTORY MATCHES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97757), // Terracotta Orange
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }

                    if (historyResults.isEmpty()) {
                        item(contentType = "HistoryEmptyState") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No history files found matching \"$searchQuery\"",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        items(displayedHistory, key = { "hist_" + it.id }, contentType = { "HistoryItem" }) { file ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openFile(file) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FileIcon(extension = file.extension, size = 28, isSample = file.isSample)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val formattedSize = remember(file.size) { formatLocalFileSize(file.size) }
                                        Text(
                                            text = "History • ${file.extension.uppercase()} • $formattedSize",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Bottom-right "Show More" expansion option for history matching list
                        if (historyResults.size > 3 && !showAllHistory) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(
                                        text = "Show More (${historyResults.size - 3} more)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97757),
                                        modifier = Modifier
                                            .clickable { showAllHistory = true }
                                            .padding(vertical = 4.dp, horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. DEVICE DIRECT FILES MATCHES (BLUE COLOR SCHEME)
                    item(contentType = "DeviceHeader") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = "DEVICE STORAGE SEARCH",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3B67A4), // Dynamic blue accent
                            )
                            if (isSearchingDevice || isIndexing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isIndexing) "Building index... " else "Searching... ",
                                        fontSize = 11.sp,
                                        color = Color(0xFF3B67A4),
                                        modifier = Modifier.premiumLoadingPulse(true)
                                    )
                                }
                            }
                        }
                    }

                    if (deviceResults.isEmpty()) {
                        item(contentType = "DeviceEmptyState") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isIndexing) "Building index... Please wait." else if (isSearchingDevice) "Scanning local directories..." else "No local device files found matching \"$searchQuery\"",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        items(deviceResults, key = { "dev_" + it.absolutePath }, contentType = { "DeviceItem" }) { localFile ->
                            val isAlreadyInHistory = historyResults.any { it.path == localFile.absolutePath }
                            
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.importAndOpenFile(localFile)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val ext = localFile.name.substringAfterLast('.', "")
                                    FileIcon(
                                        extension = ext,
                                        size = 28,
                                        isSample = false,
                                        isRecent = isAlreadyInHistory
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = localFile.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Storage • ${localFile.absolutePath}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatLocalFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}
