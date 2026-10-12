const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

// Find the Box inside Scaffold
const scaffoldBoxStartStr = "Box(modifier = Modifier.padding(padding).fillMaxSize()) {";
const scaffoldBoxStartIndex = content.indexOf(scaffoldBoxStartStr);
if (scaffoldBoxStartIndex === -1) {
    console.log("Could not find Scaffold Box start");
    process.exit(1);
}

// Find the end of FilePreviewScreen
// It should be followed by the end of the file.
const eofStr = "    }\n}";
const scaffoldBoxEndIndex = content.lastIndexOf("    }\n}");

if (scaffoldBoxEndIndex === -1) {
    console.log("Could not find Scaffold Box end");
    process.exit(1);
}

const replacementBox = `Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentState) {
                is com.example.viewmodel.MainViewModel.FileContentState.TextSuccess -> {
                    val state = currentState as com.example.viewmodel.MainViewModel.FileContentState.TextSuccess
                    if (state.file.extension.lowercase() == "md") {
                        MarkdownPreview(state.content, Modifier.fillMaxSize())
                    } else {
                        val scrollState = androidx.compose.foundation.rememberScrollState()
                        Text(
                            text = state.content,
                            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is com.example.viewmodel.MainViewModel.FileContentState.ZipSuccess -> {
                    val state = currentState as com.example.viewmodel.MainViewModel.FileContentState.ZipSuccess
                    ArchivePreview(state.root, state.file, viewModel, Modifier.fillMaxSize())
                }
                is com.example.viewmodel.MainViewModel.FileContentState.ArchivePasswordRequired -> {
                    val state = currentState as com.example.viewmodel.MainViewModel.FileContentState.ArchivePasswordRequired
                    PasswordPromptScreen(state.file, viewModel)
                }
                else -> {
                    val ext = fileEntity.extension.lowercase()
                    if (ext in listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")) {
                        ImagePreview(fileEntity)
                    } else if (ext in listOf("mp3", "wav", "m4a", "ogg")) {
                        AudioPlayer(fileEntity, Modifier.fillMaxSize())
                    } else if (ext in listOf("mp4", "mkv", "avi")) {
                        VideoPlayer(fileEntity, Modifier.fillMaxSize())
                    } else if (ext == "pdf") {
                        var base64 by remember { mutableStateOf("") }
                        LaunchedEffect(fileEntity) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val bytes = File(fileEntity.path).readBytes()
                                    base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                } catch (e: Exception) {}
                            }
                        }
                        if (base64.isNotEmpty()) {
                            PdfPreview(
                            fileEntity = fileEntity,
                            isNightMode = false,
                            scrollToPage = null,
                            onScrollToPageHandled = {},
                            onLinkClicked = {},
                            modifier = Modifier.fillMaxSize(),
                            onBackClick = onBackClick
                        )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    } else if (ext == "docx") {
                        DocxPreview(fileEntity)
                    } else if (ext in listOf("csv", "xlsx", "xls")) {
                        CsvPreview(fileEntity)
                    } else if (ext in listOf("pptx", "ppt")) {
                        var base64 by remember { mutableStateOf("") }
                        LaunchedEffect(fileEntity) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val bytes = File(fileEntity.path).readBytes()
                                    base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                } catch (e: Exception) {}
                            }
                        }
                        if (base64.isNotEmpty()) {
                            PptxPreviewWebView(base64, fileEntity, false, viewModel)
                        } else {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    } else if (ext in listOf("html", "htm", "txt", "xml", "json")) {
                        WebViewPreview(fileEntity)
                    } else {
                        var hex by remember { mutableStateOf(emptyList<String>()) }
                        var ascii by remember { mutableStateOf(emptyList<String>()) }
                        LaunchedEffect(fileEntity) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val bytes = File(fileEntity.path).readBytes().take(1024).toByteArray()
                                    val chunked = bytes.toList().chunked(16)
                                    hex = chunked.map { chunk -> chunk.joinToString(" ") { String.format("%02X", it) } }
                                    ascii = chunked.map { chunk -> chunk.joinToString("") { if (it in 32..126) it.toInt().toChar().toString() else "." } }
                                } catch (e: Exception) {}
                            }
                        }
                        if (hex.isNotEmpty()) {
                            HexViewer(hex, ascii, fileEntity)
                        } else {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }`;

let newContent = content.substring(0, scaffoldBoxStartIndex) + replacementBox + "\n    }\n}\n\n" + 
`@Composable
fun MarkdownPreview(markdownContent: String, modifier: Modifier = Modifier) {
    var htmlContent by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    
    androidx.compose.runtime.LaunchedEffect(markdownContent) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val flavour = org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor()
                val parsedTree = org.intellij.markdown.parser.MarkdownParser(flavour).buildMarkdownTreeFromString(markdownContent)
                val html = org.intellij.markdown.html.HtmlGenerator(markdownContent, parsedTree, flavour).generateHtml()
                htmlContent = """
                    <html>
                    <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { font-family: sans-serif; padding: 16px; line-height: 1.6; color: #333; }
                        pre { background: #f4f4f4; padding: 12px; border-radius: 8px; overflow-x: auto; }
                        code { font-family: monospace; background: #f4f4f4; padding: 2px 4px; border-radius: 4px; }
                        img { max-width: 100%; height: auto; }
                        blockquote { border-left: 4px solid #ccc; margin-left: 0; padding-left: 16px; color: #666; }
                    </style>
                    </head>
                    <body>
                    $html
                    </body>
                    </html>
                """.trimIndent()
            } catch (e: Exception) {
                htmlContent = "<html><body>Error parsing markdown: \${e.message}</body></html>"
            }
        }
    }
    
    if (htmlContent.isNotEmpty()) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                }
            },
            modifier = modifier.fillMaxSize()
        )
    } else {
        androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
    }
}

@Composable
fun ArchivePreview(
    root: com.example.services.FileManager.ZipNode,
    fileEntity: com.example.data.RecentFileEntity,
    viewModel: com.example.viewmodel.MainViewModel,
    modifier: Modifier = Modifier
) {
    val expandedPaths by viewModel.expandedZipPaths.collectAsState()
    
    androidx.compose.foundation.lazy.LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            androidx.compose.material3.Text("Archive Contents", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        }
        items(root.children.size) { index ->
            ZipNodeItem(
                node = root.children[index],
                level = 0,
                expandedPaths = expandedPaths,
                onToggle = { viewModel.toggleZipPathExpanded(it) },
                onClick = { if (!it.isDirectory) viewModel.openZipEntry(fileEntity, it) }
            )
        }
    }
}

@Composable
fun ZipNodeItem(
    node: com.example.services.FileManager.ZipNode,
    level: Int,
    expandedPaths: Set<String>,
    onToggle: (String) -> Unit,
    onClick: (com.example.services.FileManager.ZipNode) -> Unit
) {
    val isExpanded = expandedPaths.contains(node.path)
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .androidx.compose.foundation.clickable {
                if (node.isDirectory) onToggle(node.path) else onClick(node)
            }
            .padding(start = (16 + level * 16).dp, top = 8.dp, bottom = 8.dp, end = 16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = if (node.isDirectory) {
                if (isExpanded) androidx.compose.material.icons.Icons.Filled.FolderOpen else androidx.compose.material.icons.Icons.Filled.Folder
            } else androidx.compose.material.icons.Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = if (node.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.layout.Column {
            Text(node.name, style = MaterialTheme.typography.bodyLarge)
            if (!node.isDirectory) {
                Text("\${node.size} bytes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (node.isDirectory && isExpanded) {
        node.children.forEach { child ->
            ZipNodeItem(child, level + 1, expandedPaths, onToggle, onClick)
        }
    }
}

@Composable
fun PasswordPromptScreen(fileEntity: com.example.data.RecentFileEntity, viewModel: com.example.viewmodel.MainViewModel) {
    var password by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(64.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Text("Archive requires a password", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
            androidx.compose.material3.OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { androidx.compose.material3.Text("Password") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                singleLine = true
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = { viewModel.openFile(fileEntity, password) }) {
                androidx.compose.material3.Text("Unlock")
            }
        }
    }
}
`;

fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', newContent);
console.log("Rewrite successful.");
