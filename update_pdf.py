import sys

def process():
    file_path = 'app/src/main/java/com/example/ui/screens/FilePreviewScreen.kt'
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
        
    # find start @Composable for PdfPageItem
    start_idx = -1
    for i, line in enumerate(lines):
        if line.startswith('fun PdfPageItem('):
            start_idx = i - 1 # @Composable is before it
            break
            
    # find end of PdfPreview
    end_idx = -1
    for i in range(start_idx, len(lines)):
        if line.startswith('fun AudioPlayer('):
            # @Composable is before it, so end_idx is i - 2
            end_idx = i - 2
            break
        line = lines[i]
        
    print(f"Indices: {start_idx} to {end_idx}")
    
    new_code = """
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
    onPageChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var totalPages by remember { mutableStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    
    val originalFile = remember(fileEntity) { java.io.File(fileEntity.path) }
    var activePdfFile by remember { mutableStateOf<java.io.File?>(null) }
    
    var isPasswordRequired by remember { mutableStateOf(false) }
    var inputPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(originalFile) {
        withContext(Dispatchers.IO) {
            try {
                if (!originalFile.exists()) {
                    loadError = "PDF file not found on disk."
                    return@withContext
                }

                var isEncrypted = false
                try {
                    PDDocument.load(originalFile).use { doc ->
                        isEncrypted = doc.isEncrypted
                    }
                } catch (e: Exception) {
                    isEncrypted = true
                }

                if (isEncrypted) {
                    var canOpenNatively = false
                    try {
                        android.os.ParcelFileDescriptor.open(originalFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                            android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                                totalPages = renderer.pageCount
                                activePdfFile = originalFile
                                canOpenNatively = true
                            }
                        }
                    } catch (e: Exception) {
                        canOpenNatively = false
                    }

                    if (!canOpenNatively) {
                        isPasswordRequired = true
                    }
                } else {
                    activePdfFile = originalFile
                }
            } catch (e: Exception) {
                loadError = e.message ?: "Failed to read PDF document."
            }
        }
    }

    LaunchedEffect(activePdfFile) {
        val f = activePdfFile ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                        totalPages = renderer.pageCount
                    }
                }
                loadError = null
            } catch (e: Exception) {
                loadError = e.message ?: "Failed to render PDF document."
            }
        }
    }

    val attemptUnlock = {
        passwordError = null
        coroutineScope.launch {
            val decrypted = withContext(Dispatchers.IO) {
                try {
                    PDDocument.load(originalFile, inputPassword).use { doc ->
                        if (doc.isEncrypted) {
                            doc.setAllSecurityToBeRemoved(true)
                        }
                        val outDir = java.io.File(context.cacheDir, "decrypted_pdf")
                        if (!outDir.exists()) outDir.mkdirs()
                        val decryptedFile = java.io.File(outDir, "decrypted_${originalFile.name}")
                        doc.save(decryptedFile)
                        decryptedFile
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (decrypted != null && decrypted.exists()) {
                activePdfFile = decrypted
                isPasswordRequired = false
            } else {
                passwordError = "Incorrect password. Please try again."
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isNightMode) Color(0xFF121212) else Color(0xFFF4F4F9))
    ) {
        if (isPasswordRequired) {
            AlertDialog(
                onDismissRequest = { /* Keep dialog persistent */ },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Password Protected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Password Protected PDF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "This file '${fileEntity.name}' is encrypted. Enter the password to open it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = inputPassword,
                            onValueChange = { inputPassword = it },
                            label = { Text("Password") },
                            placeholder = { Text("Enter PDF password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = passwordError != null,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                                    )
                                }
                            }
                        )
                        if (passwordError != null) {
                            Text(
                                text = passwordError ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = attemptUnlock, enabled = inputPassword.isNotEmpty()) {
                        Text("Unlock")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (context is androidx.activity.ComponentActivity) {
                            context.onBackPressedDispatcher.onBackPressed()
                        }
                    }) {
                        Text("Cancel")
                    }
                }
            )
        } else if (totalPages == 0 && loadError == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Opening PDF natively offline...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }
        } else if (loadError != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: $loadError", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            }
        } else {
            val fileToRender = activePdfFile ?: originalFile
            
            // ZOOM AND PAN STATE
            var scale by remember { mutableFloatStateOf(1f) }
            var offsetX by remember { mutableFloatStateOf(0f) }
            var offsetY by remember { mutableFloatStateOf(0f) }
            
            val minScale = 0.1f
            val maxScale = 5.0f
            
            var widthPx by remember { mutableStateOf(0) }
            var heightPx by remember { mutableStateOf(0) }
            
            val animatedScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = scale,
                animationSpec = androidx.compose.animation.core.spring(
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow, 
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
                ),
                label = "zoom"
            )
            
            val animatedOffsetX by androidx.compose.animation.core.animateFloatAsState(
                targetValue = offsetX,
                animationSpec = androidx.compose.animation.core.spring(
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy
                ),
                label = "offsetX"
            )
            
            val animatedOffsetY by androidx.compose.animation.core.animateFloatAsState(
                targetValue = offsetY,
                animationSpec = androidx.compose.animation.core.spring(
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy
                ),
                label = "offsetY"
            )

            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = 0,
                pageCount = { totalPages }
            )

            LaunchedEffect(scrollToPage) {
                if (scrollToPage != null && scrollToPage >= 0 && scrollToPage < totalPages) {
                    pagerState.scrollToPage(scrollToPage)
                    onScrollToPageHandled()
                }
            }

            LaunchedEffect(pagerState.currentPage, totalPages) {
                if (totalPages > 0) {
                    onPageChanged(pagerState.currentPage + 1, totalPages)
                }
            }
            
            // LRU Cache for bitmaps
            val pageBitmapCache = remember { android.util.LruCache<Int, Bitmap>(3) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { layoutCoordinates ->
                        widthPx = layoutCoordinates.size.width
                        heightPx = layoutCoordinates.size.height
                    }
                    .pointerInput(scale, widthPx, heightPx) {
                        detectTapGestures(
                            onDoubleTap = { centroid ->
                                val targetScale = if (scale > 1.5f) 1f else 2.5f
                                scale = targetScale.coerceIn(minScale, maxScale)
                                
                                if (targetScale > 1f) {
                                    val centerX = widthPx / 2f
                                    val centerY = heightPx / 2f
                                    val newOffsetX = (centerX - centroid.x) * (scale - 1)
                                    val newOffsetY = (centerY - centroid.y) * (scale - 1)
                                    
                                    val maxX = (widthPx * (scale - 1)) / 2f
                                    val maxY = (heightPx * (scale - 1)) / 2f
                                    offsetX = newOffsetX.coerceIn(-maxX, maxX)
                                    offsetY = newOffsetY.coerceIn(-maxY, maxY)
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            },
                            onTap = { onSingleTap() }
                        )
                    }
                    .pointerInput(scale, widthPx, heightPx) {
                        awaitEachGesture {
                            val firstDown = awaitFirstDown()
                            var prevPosition = firstDown.position
                            var isZooming = false
                            
                            while (true) {
                                val event = awaitPointerEvent()
                                val changes = event.changes
                                val pressedCount = changes.count { it.pressed }
                                if (pressedCount == 0) break
                                
                                val maxOffsetX = if (scale > 1f) (widthPx * (scale - 1f)) / 2f else 0f
                                val maxOffsetY = if (scale > 1f) (heightPx * (scale - 1f)) / 2f else 0f
                                
                                if (pressedCount >= 2) {
                                    isZooming = true
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    
                                    scale = (scale * zoom).coerceIn(minScale, maxScale)
                                    
                                    if (scale > 1f) {
                                        val currentMaxX = (widthPx * (scale - 1f)) / 2f
                                        val currentMaxY = (heightPx * (scale - 1f)) / 2f
                                        offsetX = (offsetX + pan.x).coerceIn(-currentMaxX, currentMaxX)
                                        offsetY = (offsetY + pan.y).coerceIn(-currentMaxY, currentMaxY)
                                    } else if (scale == 1f) {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                    changes.forEach { it.consume() }
                                } else {
                                    val pointer = changes.first { it.pressed }
                                    if (isZooming) {
                                        prevPosition = pointer.position
                                        isZooming = false
                                    }
                                    
                                    val currentPos = pointer.position
                                    val pan = currentPos - prevPosition
                                    prevPosition = currentPos
                                    
                                    if (scale > 1f) {
                                        pointer.consume()
                                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    }
                                }
                            }
                        }
                    }
            ) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    beyondBoundsPageCount = 1,
                    userScrollEnabled = scale <= 1.05f, 
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    PdfPageCanvas(
                        file = fileToRender,
                        pageIndex = pageIndex,
                        scale = animatedScale,
                        offsetX = animatedOffsetX,
                        offsetY = animatedOffsetY,
                        isNightMode = isNightMode,
                        pageBitmapCache = pageBitmapCache,
                        screenWidthPx = widthPx,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
"""
    if start_idx != -1 and end_idx != -1:
        new_lines = lines[:start_idx] + [new_code + "\n"] + lines[end_idx:]
        with open(file_path, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
        print("Success")
    else:
        print("Failed to find boundaries")

process()
