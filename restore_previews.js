const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

const missingComponents = `
@Composable
fun DocxPreview(
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var elements by remember { mutableStateOf<List<DocxElement>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileEntity) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                elements = FileManager.parseDocx(fileEntity.path, context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
            items(elements ?: emptyList()) { element ->
                when (element) {
                    is DocxElement.Paragraph -> {
                        Text(element.text, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    is DocxElement.Image -> {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(File(element.imagePath))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CsvPreview(
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier
) {
    var isWebViewLoading by remember { mutableStateOf(true) }
    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>CSV Preview</title>
            <script src="https://cdn.jsdelivr.net/npm/papaparse@5.3.2/papaparse.min.js"></script>
            <style>
                body { font-family: sans-serif; padding: 16px; margin: 0; background: #fff; color: #000; }
                table { border-collapse: collapse; width: 100%; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #f2f2f2; }
                @media (prefers-color-scheme: dark) {
                    body { background: #121212; color: #fff; }
                    th, td { border-color: #333; }
                    th { background-color: #1e1e1e; }
                }
            </style>
        </head>
        <body>
            <div id="table-container">Loading CSV...</div>
            <script>
                fetch('/local_file/file.csv')
                    .then(response => response.text())
                    .then(csvText => {
                        Papa.parse(csvText, {
                            complete: function(results) {
                                let html = '<table>';
                                results.data.forEach((row, i) => {
                                    html += '<tr>';
                                    row.forEach(cell => {
                                        if (i === 0) html += '<th>' + cell + '</th>';
                                        else html += '<td>' + cell + '</td>';
                                    });
                                    html += '</tr>';
                                });
                                html += '</table>';
                                document.getElementById('table-container').innerHTML = html;
                            }
                        });
                    });
            </script>
        </body>
        </html>
    """.trimIndent()

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    
                    val assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                        .addPathHandler("/local_file/", androidx.webkit.WebViewAssetLoader.PathHandler { path ->
                            if (path == "file.csv") {
                                android.webkit.WebResourceResponse("text/csv", "utf-8", File(fileEntity.path).inputStream())
                            } else null
                        })
                        .build()
                        
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): android.webkit.WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request?.url!!) ?: super.shouldInterceptRequest(view, request)
                        }
                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                            isWebViewLoading = false
                        }
                    }
                    loadDataWithBaseURL("https://appassets.androidplatform.net/", htmlContent, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (isWebViewLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    fileEntity: RecentFileEntity,
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileEntity.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { shareFile(context, File(fileEntity.path), fileEntity.extension) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                    PdfPreviewWebView(base64, fileEntity, false)
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
                    PptxPreviewWebView(base64, fileEntity, false, mainViewModel)
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
}
`;

content = content + "\n" + missingComponents;
fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
