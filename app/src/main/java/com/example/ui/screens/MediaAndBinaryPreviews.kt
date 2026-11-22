package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.component.DocumentOutlineBottomSheet
import com.example.ui.component.AppearanceBottomSheet
import com.example.ui.component.FilePropertiesBottomSheet
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
import com.example.core.io.TextLoader
import com.example.core.io.TextWindowReader
import com.example.core.text.TextFileSearchEngine
import com.example.core.text.TextFileSearchMatch
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FormatIndentIncrease
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.component.DocumentOutlineParser
import com.example.ui.component.ViewerSyntaxTheme
import com.example.ui.component.ViewerAppearancePrefs
import com.example.core.format.DocxZoomPolicy
import com.example.core.format.PdfRenderBudgetPolicy
import com.example.core.format.PinchGestureGate
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import android.media.MediaPlayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Slider
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode

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

internal fun shareFile(context: android.content.Context, file: File, extension: String) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = when (extension.lowercase()) {
            "pdf" -> "application/pdf"
            "docx", "docm", "dotx", "dotm" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
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
