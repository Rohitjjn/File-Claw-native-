package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.input.pointer.pointerInput
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onNavigateToEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileState by viewModel.currentFileState.collectAsState()
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

    val isHtmlFile = remember(activeFile) {
        activeFile?.extension?.lowercase() in setOf("html", "htm")
    }

    var isWebRenderActive by remember(activeFile) {
        mutableStateOf(isHtmlFile)
    }

    val canBeRenderedInWeb = remember(activeFile) {
        activeFile?.extension?.lowercase() in setOf("html", "htm", "js", "ts", "css", "xml", "txt", "json", "md")
    }
    
    var showPropertiesDialog by remember { mutableStateOf(false) }

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

                            if (canBeRenderedInWeb) {
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
        },
        bottomBar = {
            // Only show edit controls for editable text/markdown files
            val isEditable = when (val state = fileState) {
                is MainViewModel.FileContentState.TextSuccess -> {
                    state.file.extension.lowercase() != "zip" && state.file.extension.lowercase() != "csv"
                }
                else -> false
            }

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
                    // Properties trigger
                    OutlinedButton(
                        onClick = { showPropertiesDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Properties", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    // Edit trigger if valid text asset
                    if (isEditable) {
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
                            Text("Edit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                .padding(innerPadding)
        ) {
            when (val state = fileState) {
                is MainViewModel.FileContentState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is MainViewModel.FileContentState.ZipSuccess -> {
                    ZipPreview(
                        zipRoot = state.root,
                        fileEntity = state.file,
                        onZipEntryClick = { node ->
                            viewModel.openZipEntry(state.file, node)
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
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is MainViewModel.FileContentState.DocxSuccess -> {
                    DocxPreview(
                        docxElements = state.elements,
                        fileEntity = state.file,
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
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
    val primaryColor = MaterialTheme.colorScheme.primary
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground
    val bodySize = when (fontSizeSetting) {
        "Small" -> 13.sp
        "Large" -> 18.sp
        else -> 15.sp
    }

    val parsedElements = remember(markdownText) {
        val elements = mutableListOf<MarkdownElement>()
        val lines = markdownText.split("\n")
        var idx = 0
        while (idx < lines.size) {
            val line = lines[idx]
            val trimmedLine = line.trim()
            
            if (trimmedLine.startsWith("```")) {
                val language = trimmedLine.substring(3).trim()
                val codeBuilder = StringBuilder()
                idx++
                while (idx < lines.size) {
                    val currentLine = lines[idx]
                    if (currentLine.trim().startsWith("```")) {
                        break
                    }
                    if (codeBuilder.isNotEmpty()) {
                        codeBuilder.append("\n")
                    }
                    codeBuilder.append(currentLine)
                    idx++
                }
                elements.add(MarkdownElement.CodeBlock(language = language, content = codeBuilder.toString()))
            } else if (trimmedLine.startsWith(">")) {
                val quoteBuilder = StringBuilder()
                while (idx < lines.size && lines[idx].trim().startsWith(">")) {
                    val qLine = lines[idx].trim()
                    val textContent = if (qLine.startsWith("> ")) qLine.substring(2) else qLine.substring(1)
                    if (quoteBuilder.isNotEmpty()) {
                        quoteBuilder.append("\n")
                    }
                    quoteBuilder.append(textContent)
                    idx++
                }
                idx--
                elements.add(MarkdownElement.Blockquote(quoteBuilder.toString()))
            } else if (trimmedLine.startsWith("# ")) {
                elements.add(MarkdownElement.Header(1, trimmedLine.substring(2)))
            } else if (trimmedLine.startsWith("## ")) {
                elements.add(MarkdownElement.Header(2, trimmedLine.substring(3)))
            } else if (trimmedLine.startsWith("### ")) {
                elements.add(MarkdownElement.Header(3, trimmedLine.substring(4)))
            } else if (trimmedLine.startsWith("* ") || trimmedLine.startsWith("- ")) {
                elements.add(MarkdownElement.ListItem(trimmedLine.substring(2)))
            } else if (trimmedLine.startsWith("---")) {
                elements.add(MarkdownElement.Rule)
            } else if (trimmedLine.startsWith("|")) {
                val tableRows = mutableListOf<String>()
                while (idx < lines.size && lines[idx].trim().startsWith("|")) {
                    tableRows.add(lines[idx].trim())
                    idx++
                }
                idx--
                if (tableRows.isNotEmpty()) {
                    elements.add(MarkdownElement.Table(tableRows))
                }
            } else if (trimmedLine.isNotEmpty()) {
                elements.add(MarkdownElement.Paragraph(line))
            }
            idx++
        }
        elements
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tag info row bunder
        item(key = "header_info", contentType = "info_row") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
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
                    text = "Editable",
                    fontSize = 11.sp,
                    color = Color(0xFF427A5B),
                    fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        itemsIndexed(
            items = parsedElements,
            key = { index, element ->
                when (element) {
                    is MarkdownElement.Header -> "h_${index}_${element.level}"
                    is MarkdownElement.ListItem -> "li_${index}"
                    is MarkdownElement.Rule -> "rule_${index}"
                    is MarkdownElement.Table -> "table_${index}"
                    is MarkdownElement.Paragraph -> "p_${index}"
                    is MarkdownElement.CodeBlock -> "code_${index}"
                    is MarkdownElement.Blockquote -> "quote_${index}"
                }
            },
            contentType = { _, element ->
                when (element) {
                    is MarkdownElement.Header -> "header"
                    is MarkdownElement.ListItem -> "list_item"
                    is MarkdownElement.Rule -> "rule"
                    is MarkdownElement.Table -> "table"
                    is MarkdownElement.Paragraph -> "paragraph"
                    is MarkdownElement.CodeBlock -> "code_block"
                    is MarkdownElement.Blockquote -> "blockquote"
                }
            }
        ) { _, element ->
            when (element) {
                is MarkdownElement.Header -> {
                    val size = when (element.level) {
                        1 -> 24.sp
                        2 -> 20.sp
                        else -> 16.sp
                    }
                    Column(modifier = Modifier.padding(vertical = if (element.level == 1) 8.dp else 4.dp)) {
                        Text(
                            text = element.text,
                            fontSize = size,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (element.level == 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), thickness = 1.5.dp)
                        }
                    }
                }
                is MarkdownElement.ListItem -> {
                    val parsedText = remember(element.text, primaryColor, onBackgroundColor) {
                        parseInlineMarkdown(element.text, primaryColor, onBackgroundColor)
                    }
                    Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            text = "•",
                            fontSize = bodySize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Text(
                            text = parsedText,
                            fontSize = bodySize,
                            lineHeight = bodySize * 1.45f,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                is MarkdownElement.Rule -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                is MarkdownElement.Table -> {
                    MarkdownTableRenderer(tableRows = element.rows)
                }
                is MarkdownElement.Paragraph -> {
                    val parsedText = remember(element.text, primaryColor, onBackgroundColor) {
                        parseInlineMarkdown(element.text, primaryColor, onBackgroundColor)
                    }
                    Text(
                        text = parsedText,
                        fontSize = bodySize,
                        lineHeight = bodySize * 1.5f,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                is MarkdownElement.CodeBlock -> {
                    MarkdownCodeBlockRenderer(
                        language = element.language,
                        content = element.content,
                        fontSize = bodySize * 0.9f
                    )
                }
                is MarkdownElement.Blockquote -> {
                    val parsedText = remember(element.text, primaryColor, onBackgroundColor) {
                        parseInlineMarkdown(element.text, primaryColor, onBackgroundColor)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                            )
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 4.dp)
                    ) {
                        Spacer(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = parsedText,
                            fontSize = bodySize,
                            fontStyle = FontStyle.Italic,
                            lineHeight = bodySize * 1.5f,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp, end = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

val StyleSpanBold = SpanStyle(fontWeight = FontWeight.Bold)

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

    LaunchedEffect(isAllCopied) {
        if (isAllCopied) {
            kotlinx.coroutines.delay(1800)
            isAllCopied = false
        }
    }

    val bodySize = when (fontSizeSetting) {
        "Small" -> 11.sp
        "Large" -> 16.sp
        else -> 13.sp
    }

    val lines = remember(codeText) { codeText.split("\n") }
    val horScrollState = if (!wrapSetting) rememberScrollState() else null

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
                text = "Editable",
                fontSize = 11.sp,
                color = Color(0xFF427A5B),
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Monospace panel with high-performance LazyColumn for individual line rendering
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val keywordColor = MaterialTheme.colorScheme.primary
            val typeColor = Color(0xFF3B67A4)
            val annotationColor = Color(0xFF7F52FF)
            val commentColor = Color(0xFF7B7875)
 
            val keywordStyle = SpanStyle(
                color = keywordColor,
                fontWeight = FontWeight.Bold
            )
            val typeStyle = SpanStyle(
                color = typeColor,
                fontWeight = FontWeight.SemiBold
            )
            val annotationStyle = SpanStyle(
                color = annotationColor,
                fontWeight = FontWeight.SemiBold
            )
            val commentStyle = SpanStyle(
                color = commentColor,
                fontStyle = FontStyle.Italic
            )
 
            val wordRegex = remember { Regex("[a-zA-Z0-9_]+|[^a-zA-Z0-9_]") }

            val annotatedLines = remember(codeText, keywordColor, typeColor, annotationColor, commentColor) {
                lines.map { line ->
                    buildAnnotatedString {
                        if (line.trim().startsWith("//") || line.trim().startsWith("/*") || line.trim().startsWith("*")) {
                            withStyle(commentStyle) {
                                append(line)
                            }
                        } else {
                            val matches = wordRegex.findAll(line)
                            matches.forEach { match ->
                                val word = match.value
                                when (word) {
                                    "val", "var", "fun", "class", "package", "import", "private", "override", "suspend", "interface", "null", "if", "else", "when", "return", "true", "false", "for", "while" -> {
                                        withStyle(keywordStyle) { append(word) }
                                    }
                                    "String", "Int", "Boolean", "Long", "Double", "Color", "MaterialTheme", "Composable", "Modifier", "RecentFileEntity", "RecentFileDao", "Flow", "List" -> {
                                        withStyle(typeStyle) { append(word) }
                                    }
                                    "Annotation", "Database", "Entity", "PrimaryKey", "Dao", "Query", "Insert" -> {
                                        withStyle(annotationStyle) { append(word) }
                                    }
                                    else -> append(word)
                                }
                            }
                        }
                    }
                }
            }
 
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                itemsIndexed(annotatedLines) { index, annotatedLine ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showLineNumbers) {
                            Text(
                                text = "${index + 1}",
                                fontSize = bodySize * 0.85f,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .width(32.dp)
                            )
                        }
                        
                        val rowScroll = if (horScrollState != null) rememberScrollState() else null
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .let { 
                                    if (rowScroll != null) it.horizontalScroll(rowScroll) else it 
                                }
                        ) {
                            Text(
                                text = annotatedLine,
                                fontFamily = FontFamily.Monospace,
                                fontSize = bodySize,
                                lineHeight = bodySize * 1.5f,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (rowScroll != null) 1 else Int.MAX_VALUE
                            )
                        }
                    }
                }
            }
        }
    }
}

// 3. SPREADSHEET CSV SCREEN COMPONENT
@Composable
fun CsvPreview(
    csvRows: List<List<String>>,
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier
) {
    val verScroll = rememberScrollState()
    val horScroll = rememberScrollState()

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
                text = "Read-only",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Excel Scroll View
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(verScroll)
                .horizontalScroll(horScroll)
        ) {
            Column {
                csvRows.forEachIndexed { rIdx, row ->
                    val isHeader = (rIdx == 0)
                    val rowColor = if (isHeader) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else if (rIdx % 2 == 1) {
                        MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }

                    Row(
                        modifier = Modifier
                            .background(rowColor)
                            .height(IntrinsicSize.Min)
                    ) {
                        // Empty spacer tag corner
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .fillMaxHeight()
                                .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isHeader) "" else "$rIdx",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }

                        row.forEachIndexed { cIdx, cell ->
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 110.dp, max = 220.dp)
                                    .fillMaxHeight()
                                    .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = cell,
                                    fontSize = 13.sp,
                                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 4. TREE-LISTING ARCHIVE PREVIEW COMPONENT
@Composable
fun ZipPreview(
    zipRoot: FileManager.ZipNode,
    fileEntity: RecentFileEntity,
    onZipEntryClick: (FileManager.ZipNode) -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep track of which folders are expanded
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    
    // Automatically expand the root ZIP node
    LaunchedEffect(zipRoot) {
        expandedStates[""] = true
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
                    text = "ZIP",
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
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Nested Header card
            item {
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
            buildNodesList(zipRoot, 0, expandedStates, nodesList)

            items(nodesList, key = { it.node.path + "_" + it.depth }) { (node, depth) ->
                val isExpanded = expandedStates[node.path] ?: false
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (node.isDirectory) {
                                expandedStates[node.path] = !isExpanded
                            } else {
                                onZipEntryClick(node)
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
    expandedStates: Map<String, Boolean>,
    result: MutableList<NodeWithDepth>
) {
    // We omit the root node itself since it is already rendered in the beautiful header card!
    if (node.path.isNotEmpty()) {
        result.add(NodeWithDepth(node, currentDepth))
    }

    val isExpanded = expandedStates[node.path] ?: false
    if (isExpanded || node.path.isEmpty()) {
        node.children.forEach { child ->
            buildNodesList(child, if (node.path.isEmpty()) 0 else currentDepth + 1, expandedStates, result)
        }
    }
}

@Composable
fun ZoomableContainer(
    maxScale: Float = 5f,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(scale: Float, scrollEnabled: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    
    val scale = scaleAnim.value
    val offsetX = offsetXAnim.value
    val offsetY = offsetYAnim.value
    
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
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                // Stable double-tap gesture binding, never cancelled during zooms
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            val currentScale = scaleAnim.value
                            val targetScale = if (currentScale > 1.1f) 1f else 2.5f
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
                                launch {
                                    scaleAnim.animateTo(
                                        targetScale,
                                        tween(300, easing = FastOutSlowInEasing)
                                    )
                                }
                                launch {
                                    offsetXAnim.animateTo(
                                        targetOffsetX,
                                        tween(300, easing = FastOutSlowInEasing)
                                    )
                                }
                                launch {
                                    offsetYAnim.animateTo(
                                        targetOffsetY,
                                        tween(300, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        }
                    )
                }
                // Persistent transform gesture stream (pinch & rotate & pan), never disconnected across recompositions
                .pointerInput(Unit) {
                    detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                        val currentScale = scaleAnim.value
                        val newScale = (currentScale * zoom).coerceIn(1f, maxScale)
                        val maxTx = (widthPx * (newScale - 1f)) / 2f
                        val maxTy = (heightPx * (newScale - 1f)) / 2f
                        val nextOffsetX = if (newScale == 1f) 0f else (offsetXAnim.value + pan.x).coerceIn(-maxTx, maxTx)
                        val nextOffsetY = if (newScale == 1f) 0f else (offsetYAnim.value + pan.y).coerceIn(-maxTy, maxTy)
                        
                        coroutineScope.launch {
                            scaleAnim.snapTo(newScale)
                            offsetXAnim.snapTo(nextOffsetX)
                            offsetYAnim.snapTo(nextOffsetY)
                        }
                    }
                }
        ) {
            content(scale, scrollEnabled)
        }
    }
}

@Composable
fun ImagePreview(
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ZoomableContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) { _, _ ->
                    AsyncImage(
                        model = java.io.File(fileEntity.path),
                        contentDescription = "Image preview",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = fileEntity.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${fileEntity.extension.uppercase()}  •  ${formatFileSize(fileEntity.size)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun PdfPreview(
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pageCount by remember { mutableStateOf(0) }
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileEntity) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(fileEntity.path)
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount
                val pageBitmaps = mutableListOf<Bitmap>()
                // Render up to 50 pages or all to keep it highly responsive and prevent out of memory
                val loadLimit = minOf(renderer.pageCount, 50)
                for (i in 0 until loadLimit) {
                    val page = renderer.openPage(i)
                    // High-resolution rendering factor of 3.0f ensures ultra-sharp detail on high zoom scales
                    val width = (context.resources.displayMetrics.widthPixels * 3.0f).toInt()
                    val height = (page.height * (width.toFloat() / page.width)).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    
                    // Clear with white background since PDFs usually have transparent/white layout
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pageBitmaps.add(bitmap)
                    page.close()
                }
                renderer.close()
                pfd.close()
                bitmaps = pageBitmaps
                isLoading = false
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to render PDF"
                isLoading = false
            }
        }
    }

    ZoomableContainer(
        modifier = modifier.fillMaxSize()
    ) { scale, scrollEnabled ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = errorMessage ?: "Error loading PDF", color = MaterialTheme.colorScheme.error)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                userScrollEnabled = scrollEnabled,
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    count = bitmaps.size,
                    key = { index -> "pdf_page_$index" },
                    contentType = { "pdf_page" }
                ) { index ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column {
                            Image(
                                bitmap = bitmaps[index].asImageBitmap(),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                contentScale = ContentScale.FillWidth
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1F1F1))
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Page ${index + 1} of $pageCount",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
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
            }
            mediaPlayer = mp
            duration = mp.duration
            
            while (true) {
                if (mp.isPlaying) {
                    currentPos = mp.currentPosition
                }
                delay(500)
            }
        } catch (e: Exception) {
            // handle error
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
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
                if (isPlaying) {
                    mp.pause()
                } else {
                    mp.start()
                }
                isPlaying = !isPlaying
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
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E)) // Dark code editor styled background for hex contrast
                .padding(8.dp)
        ) {
            items(hexRows.size) { index ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
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
            "com.example.fileprovider",
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
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.ClaudeOnyx
    val bgColor = if (isDark) com.example.ui.theme.ClaudeOnyx else Color.White

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    
                    // Enable local file access
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    
                    setBackgroundColor(if (isDark) 0xFF181715.toInt() else 0xFFFAF9F5.toInt())
                    webViewClient = android.webkit.WebViewClient()
                }
            },
            update = { webView ->
                val ext = fileEntity.extension.lowercase()
                if (ext == "html" || ext == "htm") {
                    webView.loadUrl("file://" + fileEntity.path)
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
                    .widthIn(max = 800.dp),
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

