const fs = require('fs');
let content = fs.readFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', 'utf8');

const oldImagePreview = content.substring(
    content.indexOf('@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)\n@Composable\nfun ImagePreview('),
    content.indexOf('// PDF Viewer Component')
);

const newImagePreview = `@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ImagePreview(
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var imageFiles by remember(fileEntity) { mutableStateOf(listOf(java.io.File(fileEntity.path))) }
    var initialIndex by remember { mutableStateOf(0) }
    var filesLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(fileEntity) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val parentDir = java.io.File(fileEntity.path).parentFile
            val files = parentDir?.listFiles()?.filter { 
                val ext = it.extension.lowercase()
                ext in listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
            }?.sortedBy { it.name } ?: listOf(java.io.File(fileEntity.path))
            
            val idx = files.indexOfFirst { it.absolutePath == fileEntity.path }
            initialIndex = if (idx == -1) 0 else idx
            imageFiles = files
            filesLoaded = true
        }
    }

    if (!filesLoaded) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialIndex,
        pageCount = { imageFiles.size }
    )

    val currentFile = imageFiles.getOrNull(pagerState.currentPage) ?: java.io.File(fileEntity.path)

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != initialIndex) {
            kotlinx.coroutines.delay(1000)
            val newFile = imageFiles[pagerState.currentPage]
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val dao = com.example.data.AppDatabase.getDatabase(context).recentFileDao()
                val existing = dao.getRecentFileByPath(fileEntity.path)
                if (existing != null) {
                    dao.deleteRecentFileByPath(existing.path)
                    dao.insertRecentFile(existing.copy(
                        path = newFile.absolutePath, 
                        name = newFile.name, 
                        extension = newFile.extension.lowercase(), 
                        size = newFile.length(),
                        lastOpened = System.currentTimeMillis(),
                        id = 0
                    ))
                }
            }
        }
    }

    Column(modifier = modifier) {
        // Top Bar
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
                    text = currentFile.extension.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(
                text = "\${formatFileSize(currentFile.length())} • \${currentFile.name}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "\${pagerState.currentPage + 1} / \${imageFiles.size}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Pager
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val imgFile = imageFiles[page]
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offset += pan
                            } else {
                                offset = androidx.compose.ui.geometry.Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imgFile,
                    contentDescription = null,
                    contentScale = if (scale > 1f) ContentScale.Fit else ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }
        }
    }
}
`;

content = content.replace(oldImagePreview, newImagePreview + "\n\n");
fs.writeFileSync('/app/applet/app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt', content);
