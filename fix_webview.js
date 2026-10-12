const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

// The WebViewPreview is broken, let's fix it by rewriting it completely.
const webViewPreviewRegex = /@Composable\s*fun WebViewPreview[\s\S]*?(?=@Composable\s*fun PptxPreviewWebView)/;

const fixedWebViewPreview = `
@Composable
fun WebViewPreview(
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.ClaudeOnyx
    val bgColor = if (isDark) com.example.ui.theme.ClaudeOnyx else Color.White

    var htmlContent by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isWebViewLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileEntity) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = java.io.File(fileEntity.path)
                val text = file.readText(Charsets.UTF_8)
                val ext = fileEntity.extension.lowercase()
                
                // Super basic syntax highlighting container for code files
                val escapedText = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                
                htmlContent = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
                        <style>
                            body {
                                margin: 0;
                                padding: 16px;
                                background-color: \${if (isDark) "#151514" else "#ffffff"};
                                color: \${if (isDark) "#E2E2E2" else "#1A1A1A"};
                                font-family: 'JetBrains Mono', monospace, sans-serif;
                                font-size: 14px;
                                line-height: 1.5;
                                word-wrap: break-word;
                                white-space: pre-wrap;
                            }
                        </style>
                    </head>
                    <body>\$escapedText</body>
                    </html>
                """.trimIndent()
                
            } catch (e: Exception) {
                loadError = e.message
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (loadError != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: \$loadError", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            }
        } else if (htmlContent != null) {
            AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = false
                        
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                isWebViewLoading = false
                            }
                        }
                        
                        loadDataWithBaseURL(null, htmlContent!!, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        if (isWebViewLoading && loadError == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.example.ui.component.PremiumLoadingIndicator(text = "Loading content...")
            }
        }
    }
}
`;

content = content.replace(webViewPreviewRegex, fixedWebViewPreview);
fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
