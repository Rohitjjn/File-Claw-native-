const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

// Fix missing imports
if (!content.includes('import androidx.compose.foundation.lazy.items\n')) {
    content = content.replace('import androidx.compose.foundation.lazy.itemsIndexed', 'import androidx.compose.foundation.lazy.itemsIndexed\nimport androidx.compose.foundation.lazy.items');
}

// Fix FileManager.parseDocx -> FileManager().parseDocx
content = content.replace(/FileManager\.parseDocx/g, 'FileManager().parseDocx');

// Fix ZoomableContainer types
content = content.replace(/scale = \(scale \* zoom\)\.coerceIn\(1f, 5f\)/g, 'val s: Float = scale; val z: Float = zoom; scale = (s * z).coerceIn(1f, 5f)');
content = content.replace(/androidx\.compose\.foundation\.gestures\.detectTransformGestures \{ _, pan, zoom, _ ->/g, 'androidx.compose.foundation.gestures.detectTransformGestures { _: androidx.compose.ui.geometry.Offset, pan: androidx.compose.ui.geometry.Offset, zoom: Float, _: Float ->');

// Fix PdfPreview in FilePreviewScreen
const oldPdfBlock = `"pdf" -> {
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
            }`;
const newPdfBlock = `"pdf" -> {
                PdfPreview(
                    fileEntity = fileEntity,
                    isNightMode = false,
                    scrollToPage = null,
                    onScrollToPageHandled = {},
                    onLinkClicked = {},
                    modifier = Modifier.fillMaxSize(),
                    onBackClick = onBack
                )
            }`;
content = content.replace(oldPdfBlock, newPdfBlock);

fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
