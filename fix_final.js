const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

// Fix PdfPreviewWebView
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

// Fix detectTransformGestures
content = content.replace(/androidx\.compose\.foundation\.gestures\.detectTransformGestures/g, 'detectTransformGestures');

// Fix when branches and imagePath in DocxPreview
const oldDocxWhen = `when (element) {
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
                }`;

const newDocxWhen = `when (element) {
                    is DocxElement.Paragraph -> {
                        Text(element.text, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    is DocxElement.Image -> {
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(File(element.localPath))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(bottom = 8.dp)
                        )
                    }
                    is DocxElement.Table -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            element.rows.forEach { row ->
                                Row(modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, Color.Gray))) {
                                    row.forEach { cell ->
                                        Text(cell, modifier = Modifier.weight(1f).padding(4.dp).border(BorderStroke(0.5.dp, Color.LightGray)))
                                    }
                                }
                            }
                        }
                    }
                    is DocxElement.PageBreak -> {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    }
                }`;

content = content.replace(oldDocxWhen, newDocxWhen);

fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
