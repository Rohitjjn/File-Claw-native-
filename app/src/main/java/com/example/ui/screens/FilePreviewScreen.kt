package com.example.ui.screens
import androidx.compose.ui.text.drawText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Check
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RecentFileEntity
import com.example.services.FileManager
import com.example.ui.component.ClaudeAppBar
import com.example.ui.component.ClaudeCard
import com.example.ui.component.FileIcon
import com.example.ui.component.formatFileSize
import com.example.ui.component.formatElapsedTime
import com.example.viewmodel.MainViewModel
import java.io.File
import coil.compose.AsyncImage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.media.MediaPlayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Slider
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.tom_roush.pdfbox.pdmodel.PDDocument
import androidx.compose.foundation.lazy.rememberLazyListState

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
    
    val activeFile = remember(fileState) {
        when (val state = fileState) {
            is MainViewModel.FileContentState.TextSuccess -> state.file
            is MainViewModel.FileContentState.CsvSuccess -> state.file
            is MainViewModel.FileContentState.ZipSuccess -> state.file
            is MainViewModel.FileContentState.ImageSuccess -> state.file
            is MainViewModel.FileContentState.PdfSuccess -> state.file
            is MainViewModel.FileContentState.DocxSuccess -> state.file
            is MainViewModel.FileContentState.MediaSuccess -> state.file
            is MainViewModel.FileContentState.BinarySuccess -> state.file
            else -> null
        }
    }

    var isBarsVisible by remember { mutableStateOf(true) }

    val isHtmlFile = remember(activeFile) {
        activeFile?.extension?.lowercase() in setOf("html", "htm")
    }

    var isWebRenderActive by remember(activeFile) {
        mutableStateOf(isHtmlFile)
    }

    val canBeRenderedInWeb = remember(activeFile) {
        activeFile?.extension?.lowercase() in setOf("html", "htm", "js", "ts", "css", "xml", "txt", "json", "md", "pdf")
    }
    
    var currentPdfPage by remember { mutableStateOf(1) }
    var totalPdfPages by remember { mutableStateOf(0) }
    var showPropertiesDialog by remember { mutableStateOf(false) }

    var isPdfNightMode by remember { mutableStateOf(false) }
    var scrollToPage by remember { mutableStateOf<Int?>(null) }
    var pendingLinkToOpen by remember { mutableStateOf<String?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Restore previously opened file state on recreate
    LaunchedEffect(fileState) {
        if (fileState is MainViewModel.FileContentState.Idle) {
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

    val handleBack = {
        val parentPath = activeFile?.parentZipPath
        if (parentPath != null) {
            viewModel.openParentZip(parentPath)
        } else {
            onBackClick()
        }
    }

    androidx.activity.compose.BackHandler {
        handleBack()
    }

    Scaffold(
        topBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = isBarsVisible,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut()
            ) {
                val titleText = when (val state = fileState) {
                    is MainViewModel.FileContentState.TextSuccess -> state.file.name
                    is MainViewModel.FileContentState.CsvSuccess -> state.file.name
                    is MainViewModel.FileContentState.ZipSuccess -> state.file.name
                    is MainViewModel.FileContentState.ImageSuccess -> state.file.name
                    is MainViewModel.FileContentState.PdfSuccess -> state.file.name
                    is MainViewModel.FileContentState.DocxSuccess -> state.file.name
                    is MainViewModel.FileContentState.MediaSuccess -> state.file.name
                    is MainViewModel.FileContentState.BinarySuccess -> state.file.name
                    else -> "Preview"
                }
                
                var showMenu by remember { mutableStateOf(false) }
                val context = LocalContext.current

                ClaudeAppBar(
                    title = titleText,
                    onNavIconClick = handleBack,
                    navIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    actions = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Share file") },
                                    onClick = {
                                        showMenu = false
                                        activeFile?.let { fileEntity ->
                                            shareFile(context, File(fileEntity.path), fileEntity.extension)
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share file"
                                        )
                                    }
                                )

                                val isPdf = activeFile?.extension?.lowercase() == "pdf"
                                val isDocx = activeFile?.extension?.lowercase() == "docx"
                                if (isPdf || isDocx) {
                                    DropdownMenuItem(
                                        text = { Text("Go to Page ($currentPdfPage / $totalPdfPages)") },
                                        onClick = {
                                            showMenu = false
                                            showJumpDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.InsertDriveFile,
                                                contentDescription = "Go to Page"
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isPdfNightMode) "Light Mode" else "Dark Mode") },
                                        onClick = {
                                            showMenu = false
                                            isPdfNightMode = !isPdfNightMode
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isPdfNightMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                                contentDescription = "Toggle Dark Mode"
                                             )
                                        }
                                    )
                                    if (isPdf) {
                                        DropdownMenuItem(
                                            text = { Text("Open in System Viewer") },
                                            onClick = {
                                                showMenu = false
                                                activeFile?.let { fileEntity ->
                                                    openPdfInBrowser(context, java.io.File(fileEntity.path))
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.Language,
                                                    contentDescription = "Open in System Viewer"
                                                )
                                            }
                                        )
                                    }
                                }

                                if (canBeRenderedInWeb && activeFile?.extension?.lowercase() != "pdf") {
                                    DropdownMenuItem(
                                        text = { 
                                            Text(if (isWebRenderActive) "Show Syntax Code" else "App Browser Preview") 
                                        },
                                        onClick = {
                                            showMenu = false
                                            isWebRenderActive = !isWebRenderActive
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isWebRenderActive) Icons.Outlined.Code else Icons.Outlined.Language,
                                                contentDescription = "Toggle Web Preview"
                                            )
                                        }
                                    )
                                }

                                DropdownMenuItem(
                                    text = { Text("Properties") },
                                    onClick = {
                                        showMenu = false
                                        showPropertiesDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = "Properties"
                                        )
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("Remove from history", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        activeFile?.let { fileEntity ->
                                            viewModel.deleteRecentFile(fileEntity)
                                            handleBack()
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove from history",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            val isEditable = when (val state = fileState) {
                is MainViewModel.FileContentState.TextSuccess -> {
                    state.file.extension.lowercase() != "zip" && state.file.extension.lowercase() != "csv"
                }
                else -> false
            }
            if (isEditable) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isBarsVisible,
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = onNavigateToEditor,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Edit Code / Text", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("file_preview_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .let {
                    if (isBarsVisible) {
                        it.padding(innerPadding)
                    } else {
                        it
                    }
                }
        ) {
            when (val state = fileState) {
                is MainViewModel.FileContentState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        com.example.ui.component.PremiumLoadingIndicator(text = "Loading file...")
                    }
                }
                is MainViewModel.FileContentState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Preview Error",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                is MainViewModel.FileContentState.ArchivePasswordRequired -> {
                    var inputPassword by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { onBackClick() },
                        title = { Text("Password Required") },
                        text = {
                            Column {
                                Text("This archive is password protected.", fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = inputPassword,
                                    onValueChange = { inputPassword = it },
                                    singleLine = true,
                                    label = { Text("Password") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                viewModel.openFile(state.file, inputPassword)
                            }) {
                                Text("Open")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { onBackClick() }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                is MainViewModel.FileContentState.TextSuccess -> {
                    if (isWebRenderActive) {
                        WebViewPreview(
                            fileEntity = state.file,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Check if it's dynamic markdown or standard text code
                        if (state.file.extension.lowercase() == "md") {
                            MarkdownPreview(
                                markdownText = state.content,
                                fileEntity = state.file,
                                fontSizeSetting = settings.fontSize,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CodePreview(
                                codeText = state.content,
                                fileEntity = state.file,
                                fontSizeSetting = settings.fontSize,
                                wrapSetting = settings.wordWrap,
                                showLineNumbers = settings.showLineNumbers,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                is MainViewModel.FileContentState.CsvSuccess -> {
                    CsvPreview(
                        csvRows = state.rows,
                        fileEntity = state.file,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is MainViewModel.FileContentState.ZipSuccess -> {
                    ZipPreview(
                        zipRoot = state.root,
                        fileEntity = state.file,
                        expandedPaths = expandedZipPaths,
                        onToggleExpand = { path ->
                            viewModel.toggleZipPathExpanded(path)
                        },
                        onZipEntryClick = { node, pwd ->
                            viewModel.openZipEntry(state.file, node, pwd)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is MainViewModel.FileContentState.ImageSuccess -> {
                    ImagePreview(
                        fileEntity = state.file,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is MainViewModel.FileContentState.PdfSuccess -> {
                    PdfPreview(
                        fileEntity = state.file,
                        isNightMode = isPdfNightMode,
                        scrollToPage = scrollToPage,
                        onScrollToPageHandled = { scrollToPage = null },
                        onLinkClicked = { _ -> },
                        modifier = Modifier.fillMaxSize(),
                        onSingleTap = { isBarsVisible = !isBarsVisible },
                        onPageChanged = { page, total ->
                            currentPdfPage = page
                            totalPdfPages = total
                        },
                        onBackClick = onBackClick
                    )
                }
                is MainViewModel.FileContentState.DocxSuccess -> {
                    DocxPreviewWebView(
                        base64Docx = state.base64Data,
                        fileEntity = state.file,
                        isNightMode = isPdfNightMode,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                        onPageChanged = { page, total ->
                            currentPdfPage = page
                            totalPdfPages = total
                        },
                        scrollToPage = scrollToPage,
                        onScrollProgressHandled = { scrollToPage = null }
                    )
                }
                is MainViewModel.FileContentState.PptxSuccess -> {
                    PptxPreviewWebView(
                        base64Pptx = state.base64Data,
                        fileEntity = state.file,
                        isNightMode = isPdfNightMode,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is MainViewModel.FileContentState.MediaSuccess -> {
                    if (state.isAudio) {
                        AudioPlayer(
                            fileEntity = state.file,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        VideoPlayer(
                            fileEntity = state.file,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                is MainViewModel.FileContentState.BinarySuccess -> {
                    HexViewer(
                        hexRows = state.hexRows,
                        asciiRows = state.asciiRows,
                        fileEntity = state.file,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No file selected", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    // Properties Bottom Dialog popup
    if (showPropertiesDialog && activeFile != null) {
        activeFile.let { fileEntity ->
            val sizeStr = formatFileSize(fileEntity.size)
            val updatedStr = formatElapsedTime(fileEntity.lastOpened)
            AlertDialog(
                onDismissRequest = { showPropertiesDialog = false },
                title = { Text(text = "File Properties", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Divider()
                        PropertyItem(label = "Filename", value = fileEntity.name)
                        PropertyItem(label = "Extension", value = fileEntity.extension.uppercase())
                        PropertyItem(label = "Size", value = sizeStr)
                        PropertyItem(label = "Cache path", value = fileEntity.path)
                        PropertyItem(label = "Last opened", value = updatedStr)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPropertiesDialog = false }) {
                        Text("Dismiss", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }

    if (showJumpDialog) {
        var pageInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val pNum = pageInput.toIntOrNull()
                        if (pNum != null && pNum in 1..totalPdfPages) {
                            scrollToPage = pNum - 1
                        } else {
                            android.widget.Toast.makeText(context, "Invalid page number", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        showJumpDialog = false
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Go to Page") },
            text = {
                Column {
                    Text("Enter page number (1 to $totalPdfPages):")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = { pageInput = it.filter { char -> char.isDigit() } },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    if (pendingLinkToOpen != null) {
        AlertDialog(
            onDismissRequest = { pendingLinkToOpen = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLinkToOpen?.let { url ->
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Failed to open link", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                        pendingLinkToOpen = null
                    }
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLinkToOpen = null }) {
                    Text("Cancel")
                }
            },
            title = { Text("Open Link") },
            text = { Text("Do you want to open this link in your browser?\n\n$pendingLinkToOpen") }
        )
    }
}

@Composable
fun PropertyItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

sealed class MarkdownElement {
    data class Header(val level: Int, val text: String) : MarkdownElement()
    data class ListItem(val text: String) : MarkdownElement()
    object Rule : MarkdownElement()
    data class Table(val rows: List<String>) : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()
    data class CodeBlock(val language: String, val content: String) : MarkdownElement()
    data class Blockquote(val text: String) : MarkdownElement()
}

// Helper: Fast and robust code syntax highlighter
@Composable
fun highlightCode(code: String, primaryColor: Color): androidx.compose.ui.text.AnnotatedString {
    val keywordStyle = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)
    val typeStyle = SpanStyle(color = Color(0xFF3B67A4), fontWeight = FontWeight.SemiBold)
    val annotationStyle = SpanStyle(color = Color(0xFF7F52FF), fontWeight = FontWeight.SemiBold)
    val commentStyle = SpanStyle(color = Color(0xFF7B7875), fontStyle = FontStyle.Italic)
    val wordRegex = Regex("[a-zA-Z0-9_]+|[^a-zA-Z0-9_]")

    return remember(code, primaryColor) {
        val lines = code.split("\n")
        buildAnnotatedString {
            lines.forEachIndexed { index, line ->
                if (line.trim().startsWith("//") || line.trim().startsWith("/*") || line.trim().startsWith("*") || line.trim().startsWith("#")) {
                    withStyle(commentStyle) { append(line) }
                } else {
                    val matches = wordRegex.findAll(line)
                    matches.forEach { match ->
                        val word = match.value
                        when (word) {
                            "val", "var", "fun", "class", "package", "import", "private", "override", "suspend", "interface", "null", "if", "else", "when", "return", "true", "false", "for", "while", "const", "def", "import", "from", "as", "let", "function" -> {
                                withStyle(keywordStyle) { append(word) }
                            }
                            "String", "Int", "Boolean", "Long", "Double", "Color", "MaterialTheme", "Composable", "Modifier", "RecentFileEntity", "RecentFileDao", "Flow", "List", "ArrayList", "Map", "Set" -> {
                                withStyle(typeStyle) { append(word) }
                            }
                            "Annotation", "Database", "Entity", "PrimaryKey", "Dao", "Query", "Insert" -> {
                                withStyle(annotationStyle) { append(word) }
                            }
                            else -> append(word)
                        }
                    }
                }
                if (index < lines.lastIndex) {
                    append("\n")
                }
            }
        }
    }
}

// Helper: Custom inline Markdown parser
fun parseInlineMarkdown(text: String, primaryColor: Color, onBackgroundColor: Color): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("***", i) -> {
                    val endIdx = text.indexOf("***", i + 3)
                    if (endIdx != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 3, endIdx))
                        }
                        i = endIdx + 3
                    } else {
                        append("***")
                        i += 3
                    }
                }
                text.startsWith("**", i) -> {
                    val endIdx = text.indexOf("**", i + 2)
                    if (endIdx != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, endIdx))
                        }
                        i = endIdx + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }
                text.startsWith("*", i) -> {
                    val endIdx = text.indexOf("*", i + 1)
                    if (endIdx != -1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, endIdx))
                        }
                        i = endIdx + 1
                    } else {
                        append("*")
                        i += 1
                    }
                }
                text.startsWith("`", i) -> {
                    val endIdx = text.indexOf("`", i + 1)
                    if (endIdx != -1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = primaryColor,
                                background = primaryColor.copy(alpha = 0.08f),
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(text.substring(i + 1, endIdx))
                        }
                        i = endIdx + 1
                    } else {
                        append("`")
                        i += 1
                    }
                }
                text.startsWith("[", i) -> {
                    val midIdx = text.indexOf("]", i + 1)
                    if (midIdx != -1 && text.startsWith("(", midIdx + 1)) {
                        val endIdx = text.indexOf(")", midIdx + 2)
                        if (endIdx != -1) {
                            val linkText = text.substring(i + 1, midIdx)
                            withStyle(
                                SpanStyle(
                                    color = primaryColor,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) {
                                append(linkText)
                            }
                            i = endIdx + 1
                        } else {
                            append("[")
                            i += 1
                        }
                    } else {
                        append("[")
                        i += 1
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

// Premium Code Block Renderer with integrated Copy button and dynamic Copied transitions
@Composable
fun MarkdownCodeBlockRenderer(
    language: String,
    content: String,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            kotlinx.coroutines.delay(1800)
            isCopied = false
        }
    }

    val highlightedText = highlightCode(code = content, primaryColor = MaterialTheme.colorScheme.primary)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF4F4F4)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column {
            // Header Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = "Code Block",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = language.ifEmpty { "Code" }.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(content))
                        isCopied = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = if (isCopied) "Copied" else "Copy Code",
                            modifier = Modifier.size(14.dp),
                            tint = if (isCopied) Color(0xFF427A5B) else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCopied) "Copied!" else "Copy",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCopied) Color(0xFF427A5B) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Code Content Workspace
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = highlightedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.5f,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// 1. HIGH-FIDELITY MARKDOWN PREVIEW COMPONENT
@Composable
fun MarkdownPreview(
    markdownText: String,
    fileEntity: RecentFileEntity,
    fontSizeSetting: String,
    modifier: Modifier = Modifier
) {
    val isNightMode = androidx.compose.foundation.isSystemInDarkTheme()
    var generatedHtml by remember(markdownText, isNightMode, fontSizeSetting) { mutableStateOf<String?>(null) }
    var isWebViewLoading by remember(markdownText) { mutableStateOf(true) }

    LaunchedEffect(markdownText, isNightMode, fontSizeSetting) {
        val htmlContent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val parser = org.intellij.markdown.parser.MarkdownParser(org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor())
            val parsedTree = parser.buildMarkdownTreeFromString(markdownText)
            val rawHtml = org.intellij.markdown.html.HtmlGenerator(markdownText, parsedTree, org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor()).generateHtml()

            val fontSize = when (fontSizeSetting) {
                "Small" -> "14px"
                "Large" -> "20px"
                else -> "16px"
            }

            // Claude Theme Colors (EXACT from File Claw - MD Preview Theme Fix Prompt)
            val bgColor = if (isNightMode) "#151514" else "#faf9f5" // canvas
            val textPrimary = if (isNightMode) "#f5f5f3" else "#141413" // ink
            val textBody = if (isNightMode) "#d1cfc7" else "#3d3d3a" // body
            val textBodyStrong = if (isNightMode) "#faf9f5" else "#252523" // body_strong
            val textSecondary = if (isNightMode) "#a39f93" else "#6c6a64" // muted
            val textSecondarySoft = if (isNightMode) "#8e8779" else "#8e8b82" // muted_soft
            val accent = if (isNightMode) "#e08567" else "#cc785c" // primary (coral)
            val accentActive = if (isNightMode) "#f09a7d" else "#a9583e" // primary_active
            val border = if (isNightMode) "#2d2b28" else "#e6dfd8" // hairline
            val borderSoft = if (isNightMode) "#211f1d" else "#ebe6df" // hairline_soft
            val cardBg = if (isNightMode) "#2a2723" else "#efe9de" // surface_card
            val quoteBg = if (isNightMode) "#1c1a18" else "#f5f0e8" // surface_soft
            val inlineCodeBg = if (isNightMode) "#2d2823" else "#e8e0d2" // surface_cream_strong
            val codeBlockOuterBg = if (isNightMode) "#0d0d0c" else "#181715" // surface_dark
            val codeBlockInnerBg = if (isNightMode) "#161513" else "#1f1e1b" // surface_dark_soft
            val codeBlockHeaderBg = if (isNightMode) "#1d1b19" else "#252320" // surface_dark_elevated
            val codeBlockText = if (isNightMode) "#f5f5f3" else "#faf9f5" // on_dark
            val codeBlockButtons = if (isNightMode) "#a09d96" else "#8e8b82" // on_dark_soft

            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
                <style>
                    :root {
                        --bg-color: $bgColor;
                        --text-primary: $textPrimary;
                        --text-body: $textBody;
                        --text-body-strong: $textBodyStrong;
                        --text-secondary: $textSecondary;
                        --text-secondary-soft: $textSecondarySoft;
                        --accent: $accent;
                        --accent-active: $accentActive;
                        --border: $border;
                        --border-soft: $borderSoft;
                        --card-bg: $cardBg;
                        --quote-bg: $quoteBg;
                        --inline-code-bg: $inlineCodeBg;
                        --code-block-outer: $codeBlockOuterBg;
                        --code-block-inner: $codeBlockInnerBg;
                        --code-block-header: $codeBlockHeaderBg;
                        --code-text: $codeBlockText;
                        --code-buttons: $codeBlockButtons;
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background-color: var(--bg-color);
                        color: var(--text-body);
                        font-size: $fontSize;
                        line-height: 1.55;
                        padding: 16px;
                        margin: 0;
                        word-wrap: break-word;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        color: var(--text-primary);
                        margin-top: 24px;
                        margin-bottom: 16px;
                        font-weight: 700;
                    }
                    h1 {
                        font-size: 28px;
                        border-bottom: 2px solid var(--border);
                        padding-bottom: 8px;
                    }
                    h2 { font-size: 22px; }
                    h3 { font-size: 18px; font-weight: 600; }
                    p {
                        margin-top: 0;
                        margin-bottom: 16px;
                    }
                    li {
                        margin: 8px 0;
                    }
                    ul, ol {
                        padding-left: 24px;
                    }
                    a {
                        color: var(--accent);
                        text-decoration: none;
                    }
                    a:hover {
                        color: var(--accent-active);
                        text-decoration: underline;
                    }
                    code {
                        font-family: "JetBrains Mono", ui-monospace, Consolas, Monaco, monospace;
                        background-color: var(--inline-code-bg);
                        color: var(--accent);
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-size: 14px;
                    }
                    pre {
                        background-color: transparent;
                        margin: 0;
                        padding: 0;
                        overflow: visible;
                    }
                    .code-block-container {
                        background-color: var(--code-block-outer);
                        border-radius: 12px;
                        margin-bottom: 16px;
                        overflow: hidden;
                        border: 1px solid var(--border);
                    }
                    .code-header {
                        background-color: var(--code-block-header);
                        padding: 8px 16px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .code-lang {
                        color: var(--text-secondary-soft);
                        font-size: 12px;
                        font-family: monospace;
                        text-transform: uppercase;
                    }
                    .copy-btn {
                        background: transparent;
                        border: 1px solid var(--code-buttons);
                        color: var(--text-secondary-soft);
                        padding: 4px 12px;
                        border-radius: 6px;
                        font-size: 12px;
                        cursor: pointer;
                        transition: background 0.2s;
                    }
                    .copy-btn:hover {
                        background: rgba(255, 255, 255, 0.1);
                    }
                    .code-content {
                        background-color: var(--code-block-inner);
                        padding: 16px;
                        overflow-x: auto;
                    }
                    .code-content code {
                        background-color: transparent;
                        color: var(--code-text);
                        padding: 0;
                        font-size: 14px;
                        line-height: 1.6;
                    }
                    blockquote {
                        margin: 0 0 16px 0;
                        padding: 12px 20px;
                        color: var(--text-body);
                        border-left: 4px solid var(--accent);
                        background-color: var(--quote-bg);
                        border-radius: 0 8px 8px 0;
                    }
                    table {
                        border-collapse: collapse;
                        width: 100%;
                        margin-bottom: 16px;
                        display: block;
                        overflow-x: auto;
                        border: 1px solid var(--border);
                    }
                    th, td {
                        border: 1px solid var(--border);
                        padding: 12px;
                    }
                    th {
                        background-color: var(--quote-bg);
                        font-weight: 600;
                        color: var(--text-body-strong);
                    }
                    tr:nth-child(even) {
                        background-color: var(--bg-color);
                    }
                    tr:nth-child(odd) {
                        background-color: var(--bg-color);
                    }
                    hr {
                        border: 0;
                        border-top: 2px solid var(--border);
                        margin: 24px 0;
                    }
                    img {
                        max-width: 100%;
                        border-radius: 8px;
                    }
                </style>
                <script>
                    function copyCode(btn, codeId) {
                        var codeText = document.getElementById(codeId).innerText;
                        if(window.Android) {
                            window.Android.copyToClipboard(codeText);
                            var originalText = btn.innerText;
                            btn.innerText = 'Copied!';
                            setTimeout(function() {
                                btn.innerText = originalText;
                            }, 2000);
                        }
                    }
                    
                    document.addEventListener("DOMContentLoaded", function() {
                        var pres = document.querySelectorAll('pre');
                        pres.forEach(function(pre, index) {
                            if (pre.parentNode.className === 'code-content') return;
                            
                            var code = pre.querySelector('code');
                            var lang = 'Code';
                            if (code && code.className) {
                                var match = code.className.match(/language-(\w+)/);
                                if (match) lang = match[1];
                            }
                            
                            var codeId = 'code-' + index;
                            if (code) { code.id = codeId; } else { pre.id = codeId; }
                            
                            var container = document.createElement('div');
                            container.className = 'code-block-container';
                            
                            var header = document.createElement('div');
                            header.className = 'code-header';
                            
                            var langSpan = document.createElement('span');
                            langSpan.className = 'code-lang';
                            langSpan.innerText = lang;
                            
                            var copyBtn = document.createElement('button');
                            copyBtn.className = 'copy-btn';
                            copyBtn.innerText = 'Copy';
                            copyBtn.onclick = function() { copyCode(this, codeId); };
                            
                            header.appendChild(langSpan);
                            header.appendChild(copyBtn);
                            
                            var content = document.createElement('div');
                            content.className = 'code-content';
                            
                            pre.parentNode.insertBefore(container, pre);
                            container.appendChild(header);
                            container.appendChild(content);
                            content.appendChild(pre);
                        });
                    });
                </script>
            </head>
            <body>
                $rawHtml
            </body>
            </html>
            """.trimIndent()
        }
        generatedHtml = htmlContent
    }

    Box(modifier = modifier.fillMaxSize().background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5))) {
        if (generatedHtml != null) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { context ->
                    android.webkit.WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)
                        }
                        setBackgroundColor(if (isNightMode) 0xFF151514.toInt() else 0xFFfaf9f5.toInt())
                        setInitialScale(0)
                        addJavascriptInterface(WebAppInterface(context), "Android")
                        
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                isWebViewLoading = false
                            }
                        }
                        
                        loadDataWithBaseURL(null, generatedHtml!!, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    // No-op
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (generatedHtml == null || isWebViewLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5)),
                contentAlignment = Alignment.Center
            ) {
                com.example.ui.component.PremiumLoadingIndicator(text = "Preparing Document...")
            }
        }
    }
}

class WebAppInterface(private val context: android.content.Context) {
    @android.webkit.JavascriptInterface
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Code", text)
        clipboard.setPrimaryClip(clip)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, "Copied!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MarkdownTableRenderer(tableRows: List<String>) {
    val data = remember(tableRows) {
        // Filter separating alignment line (e.g. |:---|:---|)
        val rowsToRender = tableRows.filterIndexed { index, _ -> index != 1 }
        rowsToRender.map { row ->
            val rawParts = row.split("|").map { it.trim() }
            // Drop leading empty element if row starts with a pipe
            val partsTemp = if (rawParts.isNotEmpty() && rawParts.first().isEmpty()) rawParts.drop(1) else rawParts
            // Drop trailing empty element if row ends with a pipe
            val finalParts = if (partsTemp.isNotEmpty() && partsTemp.last().isEmpty()) partsTemp.dropLast(1) else partsTemp
            finalParts
        }
    }

    if (data.isEmpty()) return

    val scrollState = rememberScrollState()
    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.ClaudeOnyx

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(scrollState)
    ) {
        Column {
            data.forEachIndexed { rIdx, row ->
                val isHeader = (rIdx == 0)
                val rowColor = if (isHeader) {
                    if (isDark) Color(0xFF1E3A5F) else Color(0xFFE6F0FA)
                } else if (rIdx % 2 == 1) {
                    if (isDark) Color(0xFF181C24) else Color(0xFFF7F9FC)
                } else {
                    MaterialTheme.colorScheme.surface
                }

                Row(
                    modifier = Modifier
                        .background(rowColor)
                        .height(IntrinsicSize.Min)
                ) {
                    row.forEachIndexed { cIdx, cell ->
                        Box(
                            modifier = Modifier
                                .width(150.dp)
                                .fillMaxHeight()
                                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = cell,
                                fontSize = 13.sp,
                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                color = if (isHeader) {
                                    if (isDark) Color(0xFF90CAF9) else Color(0xFF1A56B1)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// 2. MONOSPACE SYNTAX HIGHLIGHTED CODE COMPONENT
@Composable
fun CodePreview(
    codeText: String,
    fileEntity: RecentFileEntity,
    fontSizeSetting: String,
    wrapSetting: Boolean,
    showLineNumbers: Boolean,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var isAllCopied by remember { mutableStateOf(false) }
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    LaunchedEffect(isAllCopied) {
        if (isAllCopied) {
            kotlinx.coroutines.delay(1800)
            isAllCopied = false
        }
    }

    Column(modifier = modifier) {
        // Tag Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Text(
                    text = fileEntity.extension.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(
                text = "${formatFileSize(fileEntity.size)} • Modified ${formatElapsedTime(fileEntity.lastOpened)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(codeText))
                    isAllCopied = true
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAllCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Copy Entire Code",
                        modifier = Modifier.size(14.dp),
                        tint = if (isAllCopied) Color(0xFF427A5B) else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAllCopied) "Copied!" else "Copy All",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAllCopied) Color(0xFF427A5B) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "WebView Render",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val htmlContent = remember(codeText, isDark, fontSizeSetting, wrapSetting, showLineNumbers) {
                val escapedCode = codeText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                val bgColor = if (isDark) "#1e1e1e" else "#ffffff"
                val textColor = if (isDark) "#d4d4d4" else "#000000"
                val fSize = when (fontSizeSetting) {
                    "Small" -> "12px"
                    "Large" -> "18px"
                    else -> "14px"
                }
                val wWrap = if (wrapSetting) "pre-wrap" else "pre"
                val wWord = if (wrapSetting) "break-word" else "normal"
                val theme = if (isDark) "vs2015.min.css" else "vs.min.css"
                
                val lineNumbersCss = if (showLineNumbers) """
                    .hljs {
                        counter-reset: linenumber;
                    }
                    .hljs-line {
                        display: block;
                        counter-increment: linenumber;
                    }
                    .hljs-line::before {
                        content: counter(linenumber);
                        display: inline-block;
                        width: 3em;
                        margin-right: 1em;
                        text-align: right;
                        color: #888;
                        user-select: none;
                    }
                """.trimIndent() else ""
                
                val jsLineNumbers = if (showLineNumbers) """
                    const codeBlock = document.querySelector('code');
                    const lines = codeBlock.innerHTML.split('\n');
                    codeBlock.innerHTML = lines.map(line => '<span class="hljs-line">' + line + '</span>').join('\n');
                """.trimIndent() else ""

                """
                <!DOCTYPE html>
                <html>
                <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0">
                <style>
                  body { 
                    background-color: $bgColor; 
                    color: $textColor;
                    margin: 0; padding: 16px;
                    font-size: $fSize;
                  }
                  pre {
                    margin: 0;
                    white-space: $wWrap;
                    word-wrap: $wWord;
                    font-family: monospace;
                  }
                  $lineNumbersCss
                </style>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/$theme">
                <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                </head>
                <body>
                <pre><code class="language-${fileEntity.extension.lowercase()}">$escapedCode</code></pre>
                <script>
                  hljs.highlightAll();
                  $jsLineNumbers
                </script>
                </body>
                </html>
                """.trimIndent()
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                }
            )
        }
    }
}

// 3. SPREADSHEET CSV SCREEN COMPONENT
@Composable
fun CsvPreview(
    csvRows: List<List<String>>,
    fileEntity: RecentFileEntity,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val isNightMode = androidx.compose.foundation.isSystemInDarkTheme()

    Column(modifier = modifier) {
        // Tag Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Text(
                    text = fileEntity.extension.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(
                text = "${formatFileSize(fileEntity.size)} • Modified ${formatElapsedTime(fileEntity.lastOpened)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Edit Mode",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Optimized Compose Excel View with x-spreadsheet via WebView
        val base64Data = remember(csvRows) {
            val jsonArray = org.json.JSONArray()
            for (row in csvRows) {
                val rowArray = org.json.JSONArray()
                for (cell in row) {
                    rowArray.put(cell)
                }
                jsonArray.put(rowArray)
            }
            android.util.Base64.encodeToString(jsonArray.toString().toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        domStorageEnabled = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                    }

                    addJavascriptInterface(object : Any() {
                        @android.webkit.JavascriptInterface
                        fun onDataChanged(jsonStr: String) {
                            try {
                                val parser = org.json.JSONArray(jsonStr)
                                val newRows = mutableListOf<List<String>>()
                                for (i in 0 until parser.length()) {
                                    val rowArray = parser.getJSONArray(i)
                                    val row = mutableListOf<String>()
                                    for (j in 0 until rowArray.length()) {
                                        row.add(rowArray.getString(j))
                                    }
                                    newRows.add(row)
                                }
                                viewModel.saveSpreadsheetFile(fileEntity, newRows)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }, "Android")

                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            view?.evaluateJavascript("loadData('$base64Data');", null)
                        }
                    }

                    val bgColor = if (isNightMode) "#151514" else "#faf9f5"
                    val htmlContent = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
                            <link rel="stylesheet" href="file:///android_asset/xspreadsheet/xspreadsheet.css">
                            <script src="file:///android_asset/xspreadsheet/xspreadsheet.js"></script>
                            <style>
                                body, html { margin: 0; padding: 0; height: 100%; width: 100%; overflow: hidden; background-color: $bgColor; }
                                #x-spreadsheet-demo { height: 100%; width: 100%; }
                            </style>
                        </head>
                        <body>
                            <div id="x-spreadsheet-demo"></div>
                            <script>
                                var s = x_spreadsheet('#x-spreadsheet-demo', {
                                    showToolbar: true,
                                    showGrid: true,
                                    view: {
                                        height: () => document.documentElement.clientHeight,
                                        width: () => document.documentElement.clientWidth,
                                    }
                                });
                                
                                function loadData(base64Str) {
                                    try {
                                        var jsonStr = decodeURIComponent(escape(window.atob(base64Str)));
                                        var rowsData = JSON.parse(jsonStr);
                                        var xRows = {};
                                        for(var i=0; i<rowsData.length; i++) {
                                            var row = rowsData[i];
                                            xRows[i] = { cells: {} };
                                            for(var j=0; j<row.length; j++) {
                                                xRows[i].cells[j] = { text: row[j] || "" };
                                            }
                                        }
                                        s.loadData([{
                                            name: 'Sheet1',
                                            rows: xRows
                                        }]);
                                        
                                        s.change(data => {
                                            var maxRow = 0;
                                            var maxCol = 0;
                                            for (var r in data.rows) {
                                                if (r === 'len') continue;
                                                var ri = parseInt(r);
                                                if (ri > maxRow) maxRow = ri;
                                                var row = data.rows[r];
                                                if (row && row.cells) {
                                                    for (var c in row.cells) {
                                                        var ci = parseInt(c);
                                                        if (ci > maxCol) maxCol = ci;
                                                    }
                                                }
                                            }
                                            var resultRows = [];
                                            for (var i = 0; i <= maxRow; i++) {
                                                var rowArr = [];
                                                for (var j = 0; j <= maxCol; j++) {
                                                    if (data.rows[i] && data.rows[i].cells && data.rows[i].cells[j]) {
                                                        rowArr.push(data.rows[i].cells[j].text || "");
                                                    } else {
                                                        rowArr.push("");
                                                    }
                                                }
                                                resultRows.push(rowArr);
                                            }
                                            Android.onDataChanged(JSON.stringify(resultRows));
                                        });
                                    } catch(e) {
                                        console.error(e);
                                    }
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                }
            }
        )
    }
}

// 4. TREE-LISTING ARCHIVE PREVIEW COMPONENT
@Composable
fun ZipPreview(
    zipRoot: FileManager.ZipNode,
    fileEntity: RecentFileEntity,
    expandedPaths: Set<String>,
    onToggleExpand: (String) -> Unit,
    onZipEntryClick: (FileManager.ZipNode, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep list state
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    // Automatically expand the root ZIP node if not expanded
    LaunchedEffect(zipRoot) {
        if (!expandedPaths.contains("")) {
            onToggleExpand("")
        }
    }

    Column(modifier = modifier) {
        // ZIP info row banner
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Text(
                    text = fileEntity.extension.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(
                text = "${formatFileSize(fileEntity.size)} • Modified ${formatElapsedTime(fileEntity.lastOpened)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Read-only",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold
            )
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Nested Header card
            item(contentType = "ZipHeader") {
                ClaudeCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFBF0EC),
                            modifier = Modifier.size(45.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = zipRoot.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val totalFilesCount = countTotalFiles(zipRoot)
                            Text(
                                text = "$totalFilesCount files • ${formatFileSize(fileEntity.size)} uncompressed",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Recursive flat structure listing to handle animations correctly in LazyColumn
            val nodesList = mutableListOf<NodeWithDepth>()
            buildNodesList(zipRoot, 0, expandedPaths, nodesList)

            items(nodesList, key = { it.node.path + "_" + it.depth }, contentType = { "ZipNode" }) { (node, depth) ->
                val isExpanded = expandedPaths.contains(node.path)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItemPlacement()
                        .clickable {
                            if (node.isDirectory) {
                                onToggleExpand(node.path)
                            } else {
                                onZipEntryClick(node, "")
                            }
                        }
                        .padding(start = (depth * 20).dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (node.isDirectory) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = "Folder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = node.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Spacer(modifier = Modifier.width(24.dp)) // indentation padding
                        Icon(
                            imageVector = Icons.Outlined.InsertDriveFile,
                            contentDescription = "File",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = node.name,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatFileSize(node.size),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class NodeWithDepth(val node: FileManager.ZipNode, val depth: Int)

fun countTotalFiles(root: FileManager.ZipNode): Int {
    var count = 0
    fun traverse(node: FileManager.ZipNode) {
        if (!node.isDirectory) {
            count++
        }
        node.children.forEach { traverse(it) }
    }
    traverse(root)
    return count
}

fun buildNodesList(
    node: FileManager.ZipNode,
    currentDepth: Int,
    expandedPaths: Set<String>,
    result: MutableList<NodeWithDepth>
) {
    // We omit the root node itself since it is already rendered in the beautiful header card!
    if (node.path.isNotEmpty()) {
        result.add(NodeWithDepth(node, currentDepth))
    }

    val isExpanded = expandedPaths.contains(node.path)
    if (isExpanded || node.path.isEmpty()) {
        node.children.forEach { child ->
            buildNodesList(child, if (node.path.isEmpty()) 0 else currentDepth + 1, expandedPaths, result)
        }
    }
}

@Composable
fun ZoomableContainer(
    maxScale: Float = 5f,
    modifier: Modifier = Modifier,
    onVerticalScroll: ((Float) -> Unit)? = null,
    onSingleTap: (() -> Unit)? = null,
    content: @Composable BoxScope.(scale: Float, scrollEnabled: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    
    var pointerCount by remember { mutableStateOf(0) }
    
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val activeFingers = event.changes.count { it.pressed }
                        if (pointerCount != activeFingers) {
                            pointerCount = activeFingers
                        }
                    }
                }
            }
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        
        val isZoomed = scale > 1.05f
        val scrollEnabled = !isZoomed && pointerCount < 2
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
                // Stable double-tap and single-tap gesture binding, never cancelled during zooms
                .pointerInput(onSingleTap) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            val targetScale = if (scale > 1.1f) 1f else 2.5f
                            val targetOffsetX = if (targetScale == 1f) 0f else {
                                val centerX = size.width / 2f
                                val dx = centerX - tapOffset.x
                                val maxTx = (widthPx * (targetScale - 1f)) / 2f
                                (dx * targetScale).coerceIn(-maxTx, maxTx)
                            }
                            val targetOffsetY = if (targetScale == 1f) 0f else {
                                val centerY = size.height / 2f
                                val dy = centerY - tapOffset.y
                                val maxTy = (heightPx * (targetScale - 1f)) / 2f
                                (dy * targetScale).coerceIn(-maxTy, maxTy)
                            }
                            coroutineScope.launch {
                                val startScale = scale
                                val startOffsetX = offsetX
                                val startOffsetY = offsetY
                                androidx.compose.animation.core.animate(
                                    initialValue = 0f,
                                    targetValue = 1f,
                                    animationSpec = tween(100, easing = FastOutSlowInEasing)
                                ) { fraction, _ ->
                                    scale = startScale + (targetScale - startScale) * fraction
                                    offsetX = startOffsetX + (targetOffsetX - startOffsetX) * fraction
                                    offsetY = startOffsetY + (targetOffsetY - startOffsetY) * fraction
                                }
                            }
                        },
                        onTap = {
                            onSingleTap?.invoke()
                        }
                    )
                }
                // Persistent transform gesture stream (pinch & rotate & pan), never disconnected across recompositions
                .pointerInput(onVerticalScroll) {
                    detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, maxScale)
                        val maxTx = (widthPx * (newScale - 1f)) / 2f
                        val maxTy = (heightPx * (newScale - 1f)) / 2f
                        
                        val nextOffsetX = if (newScale == 1f) 0f else (offsetX + pan.x).coerceIn(-maxTx, maxTx)
                        
                        // Handle vertical translation smoothly and programmatically scroll only when scale is 1
                        val nextOffsetY = if (newScale == 1f) {
                            0f
                        } else {
                            (offsetY + pan.y).coerceIn(-maxTy, maxTy)
                        }

                        // Propagate scroll to parent container ONLY if scale is 1 and dragging vertically
                        if (newScale == 1f && pan.y != 0f && onVerticalScroll != null) {
                            onVerticalScroll(-pan.y)
                        }
                        
                        scale = newScale
                        offsetX = nextOffsetX
                        offsetY = nextOffsetY
                    }
                }
        ) {
            content(scale, scrollEnabled)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ImagePreview(
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier
) {
    val parentDir = remember(fileEntity) { java.io.File(fileEntity.path).parentFile }
    val imageFiles = remember(parentDir) {
        parentDir?.listFiles()?.filter { 
            val ext = it.extension.lowercase()
            ext in listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
        }?.sortedBy { it.name } ?: listOf(java.io.File(fileEntity.path))
    }
    
    val initialIndex = remember(imageFiles, fileEntity) {
        val idx = imageFiles.indexOfFirst { it.absolutePath == fileEntity.path }
        if (idx == -1) 0 else idx
    }
    
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialIndex,
        pageCount = { imageFiles.size }
    )
    
    val currentFile = imageFiles.getOrNull(pagerState.currentPage) ?: java.io.File(fileEntity.path)
    
    // The history save requirement "history me sirf folder save kro or path me last previewed pictures jis per click krne per us folder ki last dekhi hue image lload ho"
    // Since MainViewModel automatically saves the file when openFile is called, we don't need to save every swipe to the history,
    // but the user says "path me last previewed pictures jis per click krne per us folder ki last dekhi hue image lload ho"
    // So maybe we can trigger a history update when the page settles, updating the same DB record? 
    // For now, let's just show the images. We can use a LaunchedEffect to update history silently if needed.
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != initialIndex) {
            val dao = com.example.data.AppDatabase.getDatabase(context).recentFileDao()
            val newFile = imageFiles[pagerState.currentPage]
            val existing = dao.getRecentFileByPath(fileEntity.path)
            if (existing != null) {
                dao.deleteRecentFileByPath(existing.path)
                dao.insertRecentFile(existing.copy(
                    path = newFile.absolutePath, 
                    name = newFile.name, 
                    extension = newFile.extension.lowercase(), 
                    size = newFile.length(),
                    lastOpened = System.currentTimeMillis(),
                    id = 0 // Auto-generate new id
                ))
            } else {
                dao.insertRecentFile(RecentFileEntity(
                    path = newFile.absolutePath,
                    name = newFile.name,
                    extension = newFile.extension.lowercase(),
                    size = newFile.length(),
                    lastOpened = System.currentTimeMillis()
                ))
            }
        }
    }

    var showOverlay by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val file = imageFiles[page]
            me.saket.telephoto.zoomable.coil.ZoomableAsyncImage(
                model = file,
                contentDescription = "Image preview",
                modifier = Modifier.fillMaxSize(),
                onClick = { showOverlay = !showOverlay }
            )
        }
        
        // Top Bar overlay for 3-dots and name
        androidx.compose.animation.AnimatedVisibility(
            visible = showOverlay,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    ))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentFile.name} (${pagerState.currentPage + 1}/${imageFiles.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Properties", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Properties") },
                            onClick = { showMenu = false },
                            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Size: ${formatFileSize(currentFile.length())}") },
                            onClick = { showMenu = false }
                        )
                    }
                }
            }
        }
    }
}


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PdfPreview(
    fileEntity: RecentFileEntity,
    isNightMode: Boolean,
    scrollToPage: Int?,
    onScrollToPageHandled: () -> Unit,
    onLinkClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    onPageChanged: (Int, Int) -> Unit = { _, _ -> },
    onBackClick: () -> Unit = {}
) {
    val originalFile = remember(fileEntity) { java.io.File(fileEntity.path) }
    var isReady by remember { mutableStateOf(false) }
    var requirePassword by remember { mutableStateOf(false) }
    var pdfPassword by remember { mutableStateOf("") }
    var submittedPassword by remember { mutableStateOf<String?>(null) }
    var loadTrigger by remember { mutableStateOf(0) }
    var passwordError by remember { mutableStateOf(false) }

    if (requirePassword) {
        AlertDialog(
            onDismissRequest = { onBackClick() },
            title = { Text("Password Required") },
            text = {
                Column {
                    Text("This PDF is password protected.", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pdfPassword,
                        onValueChange = { pdfPassword = it; passwordError = false },
                        singleLine = true,
                        isError = passwordError,
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordError) {
                        Text("Incorrect password", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    submittedPassword = pdfPassword
                    requirePassword = false
                    loadTrigger++ // retry loading
                }) {
                    Text("Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { onBackClick() }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isNightMode) Color(0xFF121212) else Color(0xFFF4F4F9))
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                com.github.barteksc.pdfviewer.PDFView(context, null).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { pdfView ->
                if ((!isReady && originalFile.exists()) || loadTrigger > 0) {
                    val configurator = pdfView.fromFile(originalFile)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .defaultPage(scrollToPage ?: 0)
                        .onPageChange { page, pageCount ->
                            onPageChanged(page + 1, pageCount)
                        }
                        .enableAnnotationRendering(true)
                        .password(submittedPassword)
                        .scrollHandle(com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle(pdfView.context))
                        .enableAntialiasing(true)
                        .spacing(0)
                        .autoSpacing(false)
                        .pageFitPolicy(com.github.barteksc.pdfviewer.util.FitPolicy.WIDTH)
                        .nightMode(isNightMode)
                        .onLoad {
                            isReady = true
                            requirePassword = false
                            passwordError = false
                            loadTrigger = 0
                            if (scrollToPage != null) {
                                onScrollToPageHandled()
                            }
                        }
                        .onError { t ->
                            if (t is com.shockwave.pdfium.PdfPasswordException || t.message?.contains("Password required") == true) {
                                requirePassword = true
                                if (!submittedPassword.isNullOrEmpty()) {
                                    passwordError = true
                                }
                            } else {
                                t.printStackTrace()
                            }
                        }
                        .onTap {
                            onSingleTap()
                            true
                        }
                        
                    configurator.load()
                    if (loadTrigger > 0) loadTrigger = 0
                } else if (isReady && scrollToPage != null) {
                    pdfView.jumpTo(scrollToPage, true)
                    onScrollToPageHandled()
                }
                
                if (isReady) {
                    pdfView.setNightMode(isNightMode)
                }
            }
        )
        
        if (!isReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.example.ui.component.PremiumLoadingIndicator(text = "Loading perfect PDF...")
            }
        }
    }
}

@Composable
fun AudioPlayer(
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(1) }

    LaunchedEffect(fileEntity) {
        try {
            val mp = MediaPlayer().apply {
                setDataSource(fileEntity.path)
                prepare()
                setOnCompletionListener {
                    isPlaying = false
                }
            }
            mediaPlayer = mp
            duration = mp.duration
            
            while (true) {
                val mpActive = mediaPlayer
                if (mpActive != null) {
                    try {
                        if (mpActive.isPlaying) {
                            currentPos = mpActive.currentPosition
                        }
                    } catch (e: Exception) {
                        break
                    }
                } else {
                    break
                }
                delay(500)
            }
        } catch (e: Exception) {
            // handle error
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.apply {
                try {
                    if (isPlaying) {
                        stop()
                    }
                } catch (ignored: Exception) {}
                release()
            }
            mediaPlayer = null
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = fileEntity.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Progress bar / Slider
        Slider(
            value = currentPos.toFloat(),
            onValueChange = { newVal ->
                mediaPlayer?.seekTo(newVal.toInt())
                currentPos = newVal.toInt()
            },
            valueRange = 0f..duration.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime(currentPos), fontSize = 12.sp, color = Color.Gray)
            Text(text = formatTime(duration), fontSize = 12.sp, color = Color.Gray)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Play / Pause FAB
        FloatingActionButton(
            onClick = {
                val mp = mediaPlayer ?: return@FloatingActionButton
                try {
                    if (isPlaying) {
                        mp.pause()
                        isPlaying = false
                    } else {
                        mp.start()
                        isPlaying = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause"
            )
        }
    }
}

@Composable
fun VideoPlayer(
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = fileEntity.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    setVideoPath(fileEntity.path)
                    val controller = android.widget.MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    start()
                }
            },
            modifier = Modifier.fillMaxWidth().height(260.dp)
        )
    }
}

@Composable
fun HexViewer(
    hexRows: List<String>,
    asciiRows: List<String>,
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Text(
                    text = "BINARY / HEX",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(
                text = "${formatFileSize(fileEntity.size)} • Modified ${formatElapsedTime(fileEntity.lastOpened)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E)) // Dark code editor styled background for hex contrast
                .padding(8.dp)
        ) {
            items(hexRows.size, key = { it }, contentType = { "HexRow" }) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).animateItemPlacement(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = hexRows[index],
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFD4D4D4)
                    )
                    Text(
                        text = "| ${if (index < asciiRows.size) asciiRows[index] else ""}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF9CDCFE),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

fun formatTime(ms: Int): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}

private fun shareFile(context: android.content.Context, file: File, extension: String) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = when (extension.lowercase()) {
            "pdf" -> "application/pdf"
            "csv" -> "text/csv"
            "zip" -> "application/zip"
            "png", "jpg", "jpeg", "webp" -> "image/*"
            "mp3", "wav", "m4a" -> "audio/*"
            "mp4", "mkv" -> "video/*"
            else -> "text/plain"
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share File"))
    } catch (e: Exception) {
        e.printStackTrace()
        // Reliable fallback sharing flow using plain file path text
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, "File Path: ${file.absolutePath}")
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share File Path"))
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}

@Composable
fun WebViewPreview(
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.ClaudeOnyx
    val bgColor = if (isDark) com.example.ui.theme.ClaudeOnyx else Color.White

    var base64Data by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fileEntity) {
        if (fileEntity.extension.lowercase() == "pdf") {
            withContext(Dispatchers.IO) {
                try {
                    val file = java.io.File(fileEntity.path)
                    val bytes = file.readBytes()
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    base64Data = base64
                } catch (e: Exception) {
                    loadError = e.message
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        val context = LocalContext.current
        val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

        if (fileEntity.extension.lowercase() == "pdf" && base64Data == null && loadError == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.example.ui.component.PremiumLoadingIndicator(text = "Loading PDF...")
            }
        } else if (loadError != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: $loadError", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        
                        addJavascriptInterface(object {
                            @android.webkit.JavascriptInterface
                            fun performSingleTap() {
                                mainHandler.post {
                                    onSingleTap()
                                }
                            }
                        }, "Android")

                        setBackgroundColor(if (isDark) 0xFF181715.toInt() else 0xFFFAF9F5.toInt())
                        webViewClient = android.webkit.WebViewClient()
                        webChromeClient = android.webkit.WebChromeClient()
                    }
                },
                update = { webView ->
                    val ext = fileEntity.extension.lowercase()
                    if (ext == "html" || ext == "htm") {
                        webView.loadUrl("file://" + fileEntity.path)
                    } else if (ext == "pdf") {
                        try {
                            val base64Pdf = base64Data ?: ""
                            val htmlContent = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
                                    <script src="file:///android_asset/pdfjs/build/pdf.js"></script>
                                    <style>
                                        body {
                                            margin: 0;
                                            padding: 12px;
                                            background-color: ${if (isDark) "#181715" else "#FAF9F5"};
                                            display: flex;
                                            flex-direction: column;
                                            align-items: center;
                                            font-family: -apple-system, sans-serif;
                                            user-select: text !important;
                                            -webkit-user-select: text !important;
                                        }
                                        .page-container {
                                            position: relative;
                                            margin-bottom: 20px;
                                            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                                            background-color: white;
                                            border-radius: 6px;
                                            overflow: hidden;
                                            max-width: 100%;
                                            display: flex;
                                            justify-content: center;
                                        }
                                        canvas {
                                            display: block;
                                            max-width: 100%;
                                            height: auto !important;
                                        }
                                        .text-layer {
                                            position: absolute;
                                            left: 0;
                                            top: 0;
                                            right: 0;
                                            bottom: 0;
                                            overflow: hidden;
                                            background: transparent;
                                            pointer-events: auto;
                                        }
                                        .text-layer > span {
                                            color: rgba(0,0,0,0);
                                            position: absolute;
                                            white-space: pre;
                                            cursor: text;
                                            transform-origin: 0% 0%;
                                            pointer-events: auto;
                                            user-select: text !important;
                                            -webkit-user-select: text !important;
                                        }
                                        #loading {
                                            margin-top: 40px;
                                            color: ${if (isDark) "#CCCCCC" else "#333333"};
                                            font-size: 15px;
                                            font-weight: 500;
                                        }
                                    </style>
                                </head>
                                <body>
                                    <div id="loading">Preparing PDF Browser Preview...</div>
                                    <div id="viewer"></div>
    
                                    <script>
                                        function transformMatrix(m1, m2) {
                                            return [
                                                m1[0] * m2[0] + m1[2] * m2[1],
                                                m1[1] * m2[0] + m1[3] * m2[1],
                                                m1[0] * m2[2] + m1[2] * m2[3],
                                                m1[1] * m2[2] + m1[3] * m2[3],
                                                m1[0] * m2[4] + m1[2] * m2[5] + m1[4],
                                                m1[1] * m2[4] + m1[3] * m2[5] + m1[5]
                                            ];
                                        }
    
                                        function base64ToUint8Array(base64) {
                                            var raw = window.atob(base64);
                                            var rawLength = raw.length;
                                            var array = new Uint8Array(new ArrayBuffer(rawLength));
                                            for(var i = 0; i < rawLength; i++) {
                                                array[i] = raw.charCodeAt(i);
                                            }
                                            return array;
                                        }
    
                                        document.addEventListener('click', function(e) {
                                            var selection = window.getSelection().toString();
                                            if (!selection) {
                                                if (typeof Android !== 'undefined' && Android.performSingleTap) {
                                                    Android.performSingleTap();
                                                }
                                            }
                                        });
    
                                        var pdfData = base64ToUint8Array("$base64Pdf");
                                        pdfjsLib.GlobalWorkerOptions.workerSrc = 'file:///android_asset/pdfjs/build/pdf.worker.js';
    
                                        var loadingTask = pdfjsLib.getDocument({data: pdfData});
                                        loadingTask.promise.then(function(pdf) {
                                            var loadingEl = document.getElementById('loading');
                                            if (loadingEl) loadingEl.style.display = 'none';
                                            var viewer = document.getElementById('viewer');
                                            for (var pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
                                                renderPage(pdf, pageNum, viewer);
                                            }
                                        }, function (reason) {
                                            document.getElementById('loading').innerText = 'Error loading PDF: ' + reason.message;
                                        });
    
                                        function renderPage(pdf, pageNum, container) {
                                            pdf.getPage(pageNum).then(function(page) {
                                                var scale = 1.5;
                                                var viewport = page.getViewport({scale: scale});
    
                                                var pageDiv = document.createElement('div');
                                                pageDiv.className = 'page-container';
                                                pageDiv.style.width = viewport.width + 'px';
                                                pageDiv.style.height = viewport.height + 'px';
                                                
                                                var canvas = document.createElement('canvas');
                                                var context = canvas.getContext('2d');
                                                canvas.height = viewport.height;
                                                canvas.width = viewport.width;
    
                                                pageDiv.appendChild(canvas);
                                                container.appendChild(pageDiv);
    
                                                var renderContext = {
                                                    canvasContext: context,
                                                    viewport: viewport
                                                };
                                                
                                                var renderTask = page.render(renderContext);
                                                renderTask.promise.then(function() {
                                                    return page.getTextContent();
                                                }).then(function(textContent) {
                                                    var textLayerDiv = document.createElement('div');
                                                    textLayerDiv.className = 'text-layer';
                                                    pageDiv.appendChild(textLayerDiv);
                                                    
                                                    textContent.items.forEach(function(item) {
                                                        var tx = transformMatrix(
                                                            viewport.transform,
                                                            item.transform
                                                        );
                                                        
                                                        var span = document.createElement('span');
                                                        span.textContent = item.str;
                                                        span.style.fontFamily = item.fontName;
                                                        var fontHeight = item.height * scale;
                                                        span.style.fontSize = fontHeight + 'px';
                                                        
                                                        var textWidth = item.width * scale;
                                                        span.style.width = textWidth + 'px';
                                                        
                                                        span.style.left = tx[4] + 'px';
                                                        span.style.top = (tx[5] - fontHeight) + 'px';
                                                        
                                                        textLayerDiv.appendChild(span);
                                                    });
                                                });
                                            });
                                        }
                                    </script>
                                </body>
                                </html>
                            """.trimIndent()
                            webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                        } catch (e: Exception) {
                            webView.loadDataWithBaseURL(null, "<html><body><h3>Error: ${e.message}</h3></body></html>", "text/html", "UTF-8", null)
                        }
                    } else {
                    try {
                        val content = java.io.File(fileEntity.path).readText()
                        val htmlContent = if (ext == "md") {
                            """
                            <html>
                            <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body {
                                    background-color: ${if (isDark) "#181715" else "#FAF9F5"};
                                    color: ${if (isDark) "#FAF9F5" else "#141413"};
                                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                                    padding: 16px;
                                    line-height: 1.6;
                                    margin: 0;
                                }
                                h1, h2, h3, h4, h5, h6 {
                                    color: ${if (isDark) "#E8A55A" else "#CC785C"};
                                    margin-top: 24px;
                                    margin-bottom: 12px;
                                }
                                pre {
                                    background-color: ${if (isDark) "#252320" else "#EFE9DE"};
                                    padding: 12px;
                                    border-radius: 6px;
                                    overflow-x: auto;
                                    font-family: monospace;
                                    font-size: 13px;
                                }
                                code {
                                    background-color: ${if (isDark) "#252320" else "#EFE9DE"};
                                    padding: 2px 4px;
                                    border-radius: 4px;
                                    font-family: monospace;
                                }
                                blockquote {
                                    border-left: 4px solid ${if (isDark) "#E8A55A" else "#CC785C"};
                                    margin: 0;
                                    padding-left: 16px;
                                    color: ${if (isDark) "#A09D96" else "#6C6A64"};
                                    font-style: italic;
                                }
                                table {
                                    border-collapse: collapse;
                                    width: 100%;
                                    margin: 16px 0;
                                }
                                th, td {
                                    border: 1px solid ${if (isDark) "#2D2C2A" else "#E6DFD8"};
                                    padding: 8px;
                                    text-align: left;
                                }
                                th {
                                    background-color: ${if (isDark) "#252320" else "#EFE9DE"};
                                }
                                ul, ol {
                                    padding-left: 20px;
                                }
                            </style>
                            </head>
                            <body>
                                ${formatSimpleMarkdownToHtml(content)}
                            </body>
                            </html>
                            """.trimIndent()
                        } else {
                            """
                            <html>
                            <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body {
                                    background-color: ${if (isDark) "#181715" else "#FAF9F5"};
                                    color: ${if (isDark) "#FAF9F5" else "#141413"};
                                    font-family: monospace;
                                    padding: 16px;
                                    white-space: pre-wrap;
                                    word-wrap: break-word;
                                    line-height: 1.5;
                                    font-size: 14px;
                                    margin: 0;
                                }
                            </style>
                            </head>
                            <body>${escapeHtml(content)}</body>
                            </html>
                            """.trimIndent()
                        }
                        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                    } catch (e: Exception) {
                        webView.loadUrl("file://" + fileEntity.path)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        }
    }
}

fun formatSimpleMarkdownToHtml(mdText: String): String {
    val lines = mdText.lines()
    val htmlBuilder = StringBuilder()
    var inList = false
    var inCodeBlock = false
    val codeBlockContent = StringBuilder()

    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                htmlBuilder.append("<pre><code>")
                htmlBuilder.append(escapeHtml(codeBlockContent.toString()))
                htmlBuilder.append("</code></pre>\n")
                codeBlockContent.clear()
                inCodeBlock = false
            } else {
                inCodeBlock = true
            }
            continue
        }

        if (inCodeBlock) {
            codeBlockContent.append(line).append("\n")
            continue
        }

        val trimmed = line.trim()
        
        // Handle list items
        val listStart = trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")
        if (listStart) {
            if (!inList) {
                htmlBuilder.append("<ul>\n")
                inList = true
            }
            val content = trimmed.substring(2)
            htmlBuilder.append("<li>").append(formatInlineMarkdownToHtml(content)).append("</li>\n")
            continue
        } else {
            if (inList) {
                htmlBuilder.append("</ul>\n")
                inList = false
            }
        }

        // Handle headings
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6 && trimmed.getOrNull(level) == ' ') {
                val content = trimmed.substring(level + 1)
                htmlBuilder.append("<h$level>").append(formatInlineMarkdownToHtml(content)).append("</h$level>\n")
                continue
            }
        }

        // Handle horizontal rules
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            htmlBuilder.append("<hr/>\n")
            continue
        }

        // Handle blockquotes
        if (trimmed.startsWith(">")) {
            val content = trimmed.substring(1).trim()
            htmlBuilder.append("<blockquote>").append(formatInlineMarkdownToHtml(content)).append("</blockquote>\n")
            continue
        }

        // Empty lines
        if (trimmed.isEmpty()) {
            continue
        }

        // Default to paragraph
        htmlBuilder.append("<p>").append(formatInlineMarkdownToHtml(line)).append("</p>\n")
    }

    if (inList) {
        htmlBuilder.append("</ul>\n")
    }
    if (inCodeBlock) {
        htmlBuilder.append("<pre><code>")
        htmlBuilder.append(escapeHtml(codeBlockContent.toString()))
        htmlBuilder.append("</code></pre>\n")
    }

    return htmlBuilder.toString()
}

fun escapeHtml(text: String): String {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

fun formatInlineMarkdownToHtml(text: String): String {
    var formatted = escapeHtml(text)
    
    // Bold: **text** or __text__
    val boldRegex = """\*\*(.*?)\*\*""".toRegex()
    formatted = boldRegex.replace(formatted) { "<strong>${it.groupValues[1]}</strong>" }
    
    // Italic: *text* or _text_
    val italicRegex = """\*(.*?)\*""".toRegex()
    formatted = italicRegex.replace(formatted) { "<em>${it.groupValues[1]}</em>" }
    
    // Inline code: `code`
    val codeRegex = """`(.*?)`""".toRegex()
    formatted = codeRegex.replace(formatted) { "<code>${it.groupValues[1]}</code>" }
    
    return formatted
}

@Composable
fun DocxPreview(
    docxElements: List<com.example.services.FileManager.DocxElement>,
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.ClaudeOnyx
    
    // Background of the "word processor editor"
    val processorBgColor = if (isDark) Color(0xFF14181F) else Color(0xFFF0F2F5)
    
    // Paper page color
    val paperColor = if (isDark) Color(0xFF1F242F) else Color(0xFFFFFFFF)
    val paperTextColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1A1D20)
    val paperBorderColor = if (isDark) Color(0xFF2D3748) else Color(0xFFE2E8F0)

    val pages = remember(docxElements) {
        val result = mutableListOf<List<com.example.services.FileManager.DocxElement>>()
        var currentPage = mutableListOf<com.example.services.FileManager.DocxElement>()
        var paragraphCount = 0
        var characterCount = 0

        docxElements.forEach { element ->
            when (element) {
                is com.example.services.FileManager.DocxElement.PageBreak -> {
                    if (currentPage.isNotEmpty()) {
                        result.add(currentPage.toList())
                        currentPage.clear()
                    }
                    paragraphCount = 0
                    characterCount = 0
                }
                is com.example.services.FileManager.DocxElement.Table -> {
                    // Start tables on a new page if current page has significant elements to look like a real document
                    if (currentPage.isNotEmpty() && (paragraphCount > 5 || characterCount > 500)) {
                        result.add(currentPage.toList())
                        currentPage.clear()
                        paragraphCount = 0
                        characterCount = 0
                    }
                    currentPage.add(element)
                    // Push following elements to a new page to keep tables clean
                    result.add(currentPage.toList())
                    currentPage.clear()
                    paragraphCount = 0
                    characterCount = 0
                }
                is com.example.services.FileManager.DocxElement.Image -> {
                    currentPage.add(element)
                }
                is com.example.services.FileManager.DocxElement.Paragraph -> {
                    currentPage.add(element)
                    paragraphCount++
                    characterCount += element.text.length
                    
                    // Automatic page splitting for a clean Google Docs view
                    if (paragraphCount >= 10 || characterCount >= 1200) {
                        result.add(currentPage.toList())
                        currentPage.clear()
                        paragraphCount = 0
                        characterCount = 0
                    }
                }
            }
        }
        if (currentPage.isNotEmpty()) {
            result.add(currentPage.toList())
        }
        result
    }

    ZoomableContainer(
        modifier = modifier
            .background(processorBgColor)
            .fillMaxSize()
    ) { scale, scrollEnabled ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = scrollEnabled,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        itemsIndexed(
            items = pages,
            key = { pageIdx, _ -> "docx_page_$pageIdx" },
            contentType = { _, _ -> "docx_page" }
        ) { pageIdx, pageElements ->
            // Simulated Paper sheet like Google Docs
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp)
                    .animateItemPlacement(),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = paperColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, paperBorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    pageElements.forEachIndexed { itemIdx, element ->
                        when (element) {
                            is com.example.services.FileManager.DocxElement.Paragraph -> {
                                if (element.text.isNotEmpty()) {
                                    val fontSize = if (element.isHeading) {
                                        when (element.headingLevel) {
                                            1 -> 24.sp
                                            2 -> 20.sp
                                            else -> 18.sp
                                        }
                                    } else 15.sp

                                    val fontWeight = if (element.isHeading || element.isBold) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    }

                                    val fontStyle = if (element.isItalic) {
                                        FontStyle.Italic
                                    } else {
                                        FontStyle.Normal
                                    }

                                    val bottomPadding = if (element.isHeading) 12.dp else 8.dp
                                    val topPadding = if (element.isHeading && itemIdx > 0) 16.dp else 0.dp

                                    Text(
                                        text = element.text,
                                        fontSize = fontSize,
                                        fontWeight = fontWeight,
                                        fontStyle = fontStyle,
                                        lineHeight = fontSize * 1.5f,
                                        color = if (element.isHeading) {
                                            if (isDark) Color(0xFF90CAF9) else Color(0xFF1E56B1)
                                        } else {
                                            paperTextColor
                                        },
                                        modifier = Modifier
                                            .padding(top = topPadding, bottom = bottomPadding)
                                            .fillMaxWidth()
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                            is com.example.services.FileManager.DocxElement.Image -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = File(element.localPath),
                                        contentDescription = "Embedded Image",
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .heightIn(max = 350.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, paperBorderColor, RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            is com.example.services.FileManager.DocxElement.Table -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .border(1.dp, paperBorderColor, RoundedCornerShape(6.dp))
                                        .horizontalScroll(rememberScrollState())
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        element.rows.forEachIndexed { rIdx, row ->
                                            val isRowHeader = (rIdx == 0)
                                            val rowBg = if (isRowHeader) {
                                                if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                                            } else if (rIdx % 2 == 1) {
                                                if (isDark) Color(0xFF131A26) else Color(0xFFF8FAFC)
                                            } else {
                                                Color.Transparent
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .background(rowBg)
                                                     .border(
                                                        width = 0.5.dp,
                                                        color = paperBorderColor.copy(alpha = 0.5f)
                                                    )
                                            ) {
                                                row.forEach { cell ->
                                                    Box(
                                                        modifier = Modifier
                                                            .width(150.dp)
                                                            .padding(10.dp)
                                                    ) {
                                                        Text(
                                                            text = cell,
                                                            fontSize = 13.sp,
                                                            fontWeight = if (isRowHeader) FontWeight.Bold else FontWeight.Normal,
                                                            color = paperTextColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            // Page break centered dashed indicator
            if (pageIdx < pages.size - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 800.dp)
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val strokeColor = if (isDark) Color(0xFF4A5568) else Color(0xFFCBD5E1)
                        
                        // Left dashed line
                        androidx.compose.foundation.Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
                            drawLine(
                                color = strokeColor,
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                strokeWidth = 1f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                        
                        Text(
                            text = "Page break",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = strokeColor,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        
                        // Right dashed line
                        androidx.compose.foundation.Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
                            drawLine(
                                color = strokeColor,
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                strokeWidth = 1f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
}

private fun openPdfInBrowser(context: android.content.Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Open with Browser / Viewer"))
    } catch (e: Exception) {
         try {
             val uri = androidx.core.content.FileProvider.getUriForFile(
                 context,
                 "${context.packageName}.fileprovider",
                 file
             )
             val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                 setDataAndType(uri, "text/html")
                 addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                 addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
             }
             context.startActivity(android.content.Intent.createChooser(intent, "Open in Browser"))
         } catch (ex: Exception) {
             android.widget.Toast.makeText(context, "No app available to open PDF: ${ex.message}", android.widget.Toast.LENGTH_LONG).show()
         }
    }
}


@Composable
fun PdfPreviewWebView(
    base64Pdf: String,
    fileEntity: RecentFileEntity,
    isNightMode: Boolean,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    onPageChanged: (Int, Int) -> Unit = { _, _ -> },
    scrollToPage: Int? = null,
    onScrollProgressHandled: () -> Unit = {}
) {
    var isWebViewLoading by remember(base64Pdf) { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    
    val htmlContent = remember(base64Pdf, isNightMode) {
        val bgColor = if (isNightMode) "#151514" else "#faf9f5"
        val containerBg = if (isNightMode) "#1d1b19" else "white"
        
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
            <script src="file:///android_asset/pdfjs/build/pdf.js"></script>
            <style>
                body {
                    margin: 0;
                    padding: 12px;
                    background-color: $bgColor;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    font-family: -apple-system, sans-serif;
                    user-select: text !important;
                    -webkit-user-select: text !important;
                }
                .page-container {
                    position: relative;
                    margin-bottom: 20px;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                    background-color: $containerBg;
                    border-radius: 8px;
                    overflow: hidden;
                    max-width: 100%;
                }
                canvas {
                    display: block;
                    width: 100%;
                    height: auto !important;
                }
                .textLayer {
                    position: absolute;
                    left: 0;
                    top: 0;
                    right: 0;
                    bottom: 0;
                    overflow: hidden;
                    opacity: 1.0;
                    line-height: 1.0;
                    pointer-events: auto !important;
                    user-select: text !important;
                    -webkit-user-select: text !important;
                }
                .textLayer > span {
                    color: transparent !important;
                    position: absolute;
                    white-space: pre;
                    cursor: text !important;
                    transform-origin: 0% 0%;
                    user-select: text !important;
                    -webkit-user-select: text !important;
                }
                .textLayer ::selection {
                    background: rgba(0, 100, 255, 0.3) !important;
                }
            </style>
        </head>
        <body>
            <div id="viewer"></div>
            <script>
                function base64ToUint8Array(base64) {
                    var raw = window.atob(base64);
                    var rawLength = raw.length;
                    var array = new Uint8Array(new ArrayBuffer(rawLength));
                    for(var i = 0; i < rawLength; i++) {
                        array[i] = raw.charCodeAt(i);
                    }
                    return array;
                }

                var totalPagesCount = 0;

                window.scrollToPage = function(pageNum) {
                    var el = document.getElementById('page-' + pageNum);
                    if (el) {
                        el.scrollIntoView({behavior: 'smooth'});
                    }
                };

                // Listen to scroll to update current page
                window.addEventListener('scroll', function() {
                    var pages = document.querySelectorAll('.page-container');
                    var scrollPos = window.scrollY + window.innerHeight / 3;
                    for (var i = 0; i < pages.length; i++) {
                        var page = pages[i];
                        var top = page.offsetTop;
                        var bottom = top + page.offsetHeight;
                        if (scrollPos >= top && scrollPos <= bottom) {
                            var currentPage = i + 1;
                            if (window.Android && window.Android.onPageChanged) {
                                window.Android.onPageChanged(currentPage, pages.length);
                            }
                            break;
                        }
                    }
                });

                try {
                    var pdfData = base64ToUint8Array("$base64Pdf");
                    pdfjsLib.GlobalWorkerOptions.workerSrc = 'file:///android_asset/pdfjs/build/pdf.worker.js';

                    var loadingTask = pdfjsLib.getDocument({data: pdfData});
                    loadingTask.promise.then(function(pdf) {
                        var viewer = document.getElementById('viewer');
                        var pagesToRender = pdf.numPages;
                        totalPagesCount = pagesToRender;
                        var renderedPages = 0;
                        
                        function renderPage(pageNum) {
                            pdf.getPage(pageNum).then(function(page) {
                                // Dynamic scale matching device DPI (bounded 2.0 to 3.0) for razor sharp view on zooming
                                var dpr = window.devicePixelRatio || 2.0;
                                var scale = Math.min(3.0, Math.max(2.0, dpr));
                                var viewport = page.getViewport({scale: scale});
                                
                                var pageDiv = document.createElement('div');
                                pageDiv.className = 'page-container';
                                pageDiv.id = 'page-' + pageNum;
                                pageDiv.style.width = '100%';
                                
                                var canvas = document.createElement('canvas');
                                var context = canvas.getContext('2d');
                                canvas.height = viewport.height;
                                canvas.width = viewport.width;
                                
                                var renderContext = {
                                    canvasContext: context,
                                    viewport: viewport
                                };
                                
                                pageDiv.appendChild(canvas);
                                
                                var textLayerDiv = document.createElement('div');
                                textLayerDiv.className = 'textLayer';
                                pageDiv.appendChild(textLayerDiv);
                                
                                viewer.appendChild(pageDiv);
                                
                                // Render page graphics
                                var renderTask = page.render(renderContext);
                                renderTask.promise.then(function() {
                                    // Fetch text items to render selectable/copyable text Layer overlay
                                    return page.getTextContent();
                                }).then(function(textContent) {
                                    return pdfjsLib.renderTextLayer({
                                        textContent: textContent,
                                        container: textLayerDiv,
                                        viewport: viewport,
                                        textDivs: []
                                    }).promise;
                                }).then(function() {
                                    renderedPages++;
                                    if (renderedPages === pagesToRender) {
                                        if (window.Android && window.Android.onDocumentLoaded) {
                                            window.Android.onDocumentLoaded(pagesToRender);
                                        }
                                    }
                                    if (pageNum < pagesToRender) {
                                        renderPage(pageNum + 1);
                                    }
                                }).catch(function(err) {
                                    console.error("Error drawing textLayer: ", err);
                                    // Fallback if text layer fails so the document remains available
                                    renderedPages++;
                                    if (renderedPages === pagesToRender) {
                                        if (window.Android && window.Android.onDocumentLoaded) {
                                            window.Android.onDocumentLoaded(pagesToRender);
                                        }
                                    }
                                    if (pageNum < pagesToRender) {
                                        renderPage(pageNum + 1);
                                    }
                                });
                            });
                        }
                        
                        renderPage(1);
                    }, function (reason) {
                        if (window.Android && window.Android.onDocumentError) {
                            window.Android.onDocumentError(reason.message);
                        }
                    });
                } catch (e) {
                    if (window.Android && window.Android.onDocumentError) {
                        window.Android.onDocumentError(e.message);
                    }
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    LaunchedEffect(scrollToPage) {
        if (scrollToPage != null) {
            val pageNum = scrollToPage + 1
            webViewRef?.evaluateJavascript("if (typeof window.scrollToPage === 'function') { window.scrollToPage($pageNum); }", null)
            onScrollProgressHandled()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5))) {
        val context = androidx.compose.ui.platform.LocalContext.current
        
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    webViewRef = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    setBackgroundColor(if (isNightMode) 0xFF151514.toInt() else 0xFFfaf9f5.toInt())
                    
                    addJavascriptInterface(object : Any() {
                        @android.webkit.JavascriptInterface
                        fun onDocumentLoaded(totalPages: Int) {
                            post { 
                                isWebViewLoading = false
                                onPageChanged(1, totalPages)
                            }
                        }
                        @android.webkit.JavascriptInterface
                        fun onPageChanged(currentPage: Int, totalPages: Int) {
                            post {
                                onPageChanged(currentPage, totalPages)
                            }
                        }
                        @android.webkit.JavascriptInterface
                        fun onDocumentError(error: String) {
                            post { 
                                isWebViewLoading = false
                                android.widget.Toast.makeText(context, "Error: $error", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }, "Android")
                    
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            postDelayed({ isWebViewLoading = false }, 500)
                        }
                    }
                    loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                }
            },
            update = { webView ->
                // No-op
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isWebViewLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5)),
                contentAlignment = Alignment.Center
            ) {
                com.example.ui.component.PremiumLoadingIndicator(text = "Preparing PDF Document...")
            }
        }
    }
}

@Composable
fun DocxPreviewWebView(
    base64Docx: String,
    fileEntity: RecentFileEntity,
    isNightMode: Boolean,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onPageChanged: (Int, Int) -> Unit = { _, _ -> },
    scrollToPage: Int? = null,
    onScrollProgressHandled: () -> Unit = {}
) {
    var isWebViewLoading by remember(base64Docx) { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(scrollToPage) {
        if (scrollToPage != null) {
            val pageNum = scrollToPage + 1
            webViewRef?.evaluateJavascript("if (typeof window.scrollToPage === 'function') { window.scrollToPage($pageNum); }", null)
            onScrollProgressHandled()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5))) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    webViewRef = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    setBackgroundColor(if (isNightMode) 0xFF151514.toInt() else 0xFFfaf9f5.toInt())
                    
                    addJavascriptInterface(object : Any() {
                        @android.webkit.JavascriptInterface
                        fun onDocumentLoaded(totalPages: Int) {
                            post { 
                                isWebViewLoading = false
                                onPageChanged(1, totalPages)
                            }
                        }
                        @android.webkit.JavascriptInterface
                        fun onPageChanged(currentPage: Int, totalPages: Int) {
                            post {
                                onPageChanged(currentPage, totalPages)
                            }
                        }
                        @android.webkit.JavascriptInterface
                        fun onDocumentError(error: String) {
                            post { 
                                isWebViewLoading = false
                                android.widget.Toast.makeText(context, "Error: $error", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                        @android.webkit.JavascriptInterface
                        fun exitWithoutSaving() {
                            post {
                                viewModel.closeCurrentFile()
                            }
                        }
                    }, "Android")
                    
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            postDelayed({ isWebViewLoading = false }, 500)
                            view?.evaluateJavascript("loadDocx('$base64Docx', $isNightMode);", null)
                        }
                    }
                    loadUrl("file:///android_asset/editor/docx_editor.html")
                }
            },
            update = { webView ->
                // No-op
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isWebViewLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5)),
                contentAlignment = Alignment.Center
            ) {
                com.example.ui.component.PremiumLoadingIndicator(text = "Loading Editor...")
            }
        }
    }
}

@Composable
fun PptxPreviewWebView(
    base64Pptx: String,
    fileEntity: RecentFileEntity,
    isNightMode: Boolean,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var isWebViewLoading by remember(base64Pptx) { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = modifier.fillMaxSize().background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5))) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    webViewRef = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                        useWideViewPort = true
                        loadWithOverviewMode = true
                    }
                    setBackgroundColor(if (isNightMode) 0xFF151514.toInt() else 0xFFfaf9f5.toInt())
                    
                    addJavascriptInterface(object : Any() {
                        @android.webkit.JavascriptInterface
                        fun exitWithoutSaving() {
                            post {
                                viewModel.closeCurrentFile()
                            }
                        }
                    }, "Android")
                    
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            postDelayed({ isWebViewLoading = false }, 500)
                            view?.evaluateJavascript("loadPptx('$base64Pptx', $isNightMode);", null)
                        }
                    }
                    loadUrl("file:///android_asset/editor/pptx_editor.html")
                }
            },
            update = { webView ->
                // No-op
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isWebViewLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isNightMode) Color(0xFF151514) else Color(0xFFfaf9f5)),
                contentAlignment = Alignment.Center
            ) {
                com.example.ui.component.PremiumLoadingIndicator(text = "Loading Presentation...")
            }
        }
    }
}

