package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MenuOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RecentFileEntity
import com.example.ui.component.ClaudeAppBar
import com.example.ui.component.ClaudeButton
import com.example.ui.component.ClaudeCard
import com.example.ui.component.FileIcon
import com.example.ui.component.premiumLoadingPulse
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.Calendar

import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToPreview: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAllFiles: () -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    val files by viewModel.recentFilesState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentFileState by viewModel.currentFileState.collectAsState()
    val isInitialized by viewModel.isInitialized.collectAsState()
    val loadingFilePath by viewModel.loadingFilePath.collectAsState()
    
    val context = LocalContext.current
    var isSearchActive by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (!isSearchActive) {
            viewModel.updateSearchQuery("")
        }
    }

    // Launcher for Android Document Picker
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importFileFromUri(it)
        }
    }

    // Dynamic greeting based on system hour
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier.testTag("home_screen"),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(310.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    // Drawer Header with Version details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(45.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Files Claw",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Your Files",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    drawerState.close()
                                    onNavigateToSearch()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Files",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(onClick = { pickerLauncher.launch(arrayOf("*/*")) }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Import File",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    var selectedDrawerTab by remember { mutableStateOf(0) }
                    TabRow(
                        selectedTabIndex = selectedDrawerTab,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedDrawerTab == 0,
                            onClick = { selectedDrawerTab = 0 },
                            text = { Text("RECENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                        )
                        Tab(
                            selected = selectedDrawerTab == 1,
                            onClick = { selectedDrawerTab = 1 },
                            text = { Text("FILE SYSTEM", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedDrawerTab == 0) {
                        val settings by viewModel.settingsState.collectAsState()
                        val drawerFiles by remember(files, settings.historyLimit) { 
                            derivedStateOf { files.take(settings.historyLimit) }
                        }
                        LazyColumn(
                            state = rememberLazyListState(),
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(drawerFiles, key = { it.id }, contentType = { "DrawerFile" }) { file ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            coroutineScope.launch {
                                                drawerState.close()
                                                viewModel.openFile(file)
                                            }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val isLoading = loadingFilePath == file.path
                                            FileIcon(
                                                extension = file.extension, 
                                                size = 32, 
                                                isSample = file.isSample,
                                                modifier = Modifier.premiumLoadingPulse(isLoading)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = file.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val elapsedText = remember(file.lastOpened) { formatElapsedTime(file.lastOpened) }
                                                Text(
                                                    text = elapsedText,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteRecentFile(file) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Remove from history",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (files.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No files tracked yet",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val rootDir = android.os.Environment.getExternalStorageDirectory()
                        FileTreeComponent(
                            rootDir = rootDir,
                            onFileClick = { f ->
                                coroutineScope.launch {
                                    drawerState.close()
                                    viewModel.openFile(
                                        com.example.data.RecentFileEntity(
                                            name = f.name,
                                            path = f.absolutePath,
                                            extension = f.extension,
                                            size = f.length(),
                                            lastOpened = System.currentTimeMillis()
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Text(
                        text = "v1.0.0",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (isSearchActive) {
                    // Inline beautiful search bar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .statusBarsPadding()
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            IconButton(onClick = { isSearchActive = false; viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Close Search")
                            }
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = { Text("Search files...") },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocusRequester)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        }
                        LaunchedEffect(Unit) {
                            searchFocusRequester.requestFocus()
                        }
                    }
                } else {
                    ClaudeAppBar(
                        title = "Files Claw",
                        onNavIconClick = { coroutineScope.launch { drawerState.open() } },
                        navIcon = Icons.Default.Menu,
                        actions = {
                            IconButton(onClick = onNavigateToSearch) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!isInitialized) {
                    Box(modifier = Modifier.fillMaxSize())
                } else {
                    AnimatedContent(
                        targetState = files.isEmpty(),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(100)) togetherWith fadeOut(animationSpec = tween(75))
                        },
                        label = "HomeScreenContent"
                    ) { isEmpty ->
                        if (isEmpty) {
                            // Minimal, striking empty state centered exactly like screenshots 3 and 4
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                ) {
                                    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.ClaudeOnyx
                                    val containerColor = if (isDark) Color(0xFF261D1A) else Color(0xFFFBECE5)
                                    val iconColor = Color(0xFFD97757)

                                    Surface(
                                        shape = RoundedCornerShape(28.dp),
                                        color = containerColor,
                                        modifier = Modifier.size(126.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Description,
                                                contentDescription = null,
                                                tint = iconColor,
                                                modifier = Modifier.size(56.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Text(
                                        text = "Open a file to begin",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Select any file from your device to preview or edit.",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )

                                    Spacer(modifier = Modifier.height(28.dp))

                                    ClaudeButton(
                                        text = "Select File",
                                        onClick = { pickerLauncher.launch(arrayOf("*/*")) },
                                        icon = Icons.Default.Add,
                                        testTag = "empty_select_file_button"
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Text(
                                        text = "Recent files appear here",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            val displayedFiles by remember(files) {
                                derivedStateOf { files.take(5) }
                            }
                            // Striking filled list state with custom header and view all button
                            LazyColumn(
                                state = rememberLazyListState(),
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 100.dp)
                            ) {
                                // Header greeting exactly matching screen
                                item(contentType = "Header") {
                                    Column {
                                        Spacer(modifier = Modifier.height(16.dp)) // Safe top margin buffer to prevent overlapping under header bar
                                        Text(
                                            text = greeting,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "What would you like to open today?",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                item(contentType = "RecentFilesHeader") {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Recent Files",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                            )
                                        }
                                        
                                        if (files.isNotEmpty()) {
                                            Text(
                                                text = "View All",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .clickable { onNavigateToAllFiles() }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                                    .testTag("view_all_button")
                                            )
                                        }
                                    }
                                }

                                items(displayedFiles, key = { it.id }, contentType = { "RecentFile" }) { file ->
                                    ClaudeCard(
                                        onClick = { viewModel.openFile(file) }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                val isLoading = loadingFilePath == file.path
                                                FileIcon(
                                                    extension = file.extension, 
                                                    size = 42, 
                                                    isSample = file.isSample,
                                                    modifier = Modifier.premiumLoadingPulse(isLoading)
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column {
                                                    Text(
                                                        text = file.name,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    val formattedSize = remember(file.size) { formatFileSize(file.size) }
                                                    val formattedTime = remember(file.lastOpened) { formatElapsedTime(file.lastOpened) }
                                                    Text(
                                                        text = "${file.extension.uppercase()}  •  $formattedSize  •  $formattedTime",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }

                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Open Preview",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // FIXED bottom layout Open New File button
                    if (files.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp)
                        ) {
                            ClaudeButton(
                                text = "Open New File",
                                onClick = { pickerLauncher.launch(arrayOf("*/*")) },
                                icon = Icons.Default.Add,
                                testTag = "open_new_file_button"
                            )
                        }
                    }
                }
            }
        }
    }
}

// Helpers for formatted visual indicators
fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}

fun formatElapsedTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 60 * 1000) return "just now"
    val mins = diff / (60 * 1000)
    if (mins < 60) return if (mins == 1L) "1 min ago" else "$mins mins ago"
    val hours = mins / 60
    if (hours < 24) return if (hours == 1L) "1 hour ago" else "$hours hours ago"
    val days = hours / 24
    return if (days == 1L) "1 day ago" else "$days days ago"
}
