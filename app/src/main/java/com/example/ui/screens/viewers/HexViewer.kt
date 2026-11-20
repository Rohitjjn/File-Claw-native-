package com.example.ui.screens.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RecentFileEntity
import com.example.ui.component.formatFileSize
import com.example.ui.component.formatElapsedTime
import java.io.File
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.core.hex.HexDump
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.example.R
import androidx.compose.ui.res.stringResource
import com.example.ui.theme.MonoFamily

@Composable
fun HexViewer(
    filePath: String,
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier
) {
    // AUD-003: paged 64 KB windows with absolute offsets + jump-to-offset. The file
    // is never fully loaded; one window is read at a time on Dispatchers.IO.
    val totalBytes = remember(filePath) { runCatching { File(filePath).length() }.getOrDefault(0L) }
    val windowCount = remember(totalBytes) { HexDump.windowCount(totalBytes) }
    var windowIndex by rememberSaveable(filePath) { mutableIntStateOf(0) }
    var window by remember { mutableStateOf<HexDump.HexWindow?>(null) }
    var jumpText by remember(filePath) { mutableStateOf("") }
    val hexInvalidOffset = stringResource(R.string.hex_invalid_offset)
    var jumpError by remember(filePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(filePath, windowIndex) {
        window = null
        window = withContext(Dispatchers.IO) {
            runCatching { HexDump.loadWindow(File(filePath), windowIndex) }.getOrNull()
        }
    }

    fun jumpToOffset(raw: String) {
        val target = HexDump.parseOffset(raw)
        if (target == null) {
            jumpError = hexInvalidOffset
            return
        }
        if (totalBytes > 0 && target >= totalBytes) {
            jumpError = "Offset is past the end of the file"
            return
        }
        jumpError = null
        jumpText = ""
        windowIndex = (target / HexDump.WINDOW_BYTES).toInt()
    }

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

        // Window controls: prev / position / next + jump-to-offset
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(onClick = { windowIndex = 0 }, enabled = windowIndex > 0) { Text("First") }
            TextButton(onClick = { windowIndex = (windowIndex - 1).coerceAtLeast(0) }, enabled = windowIndex > 0) { Text("‹ Prev") }
            Text(
                text = "Window ${windowIndex + 1} of $windowCount",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            TextButton(onClick = { windowIndex = (windowIndex + 1).coerceAtMost(windowCount - 1) }, enabled = windowIndex < windowCount - 1) { Text("Next ›") }
            TextButton(onClick = { windowIndex = windowCount - 1 }, enabled = windowIndex < windowCount - 1) { Text("Last") }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = jumpText,
                onValueChange = { jumpText = it; jumpError = null },
                singleLine = true,
                placeholder = { Text("Jump to offset (0x1A00 or 6656)", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { jumpToOffset(jumpText) }),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { jumpToOffset(jumpText) }) { Text("Go") }
        }
        if (jumpError != null) {
            Text(
                text = jumpError!!,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }
        Text(
            text = window?.let { "Offset 0x${it.offset.toString(16).uppercase().padStart(8, '0')} • ${it.bytesShown} of $totalBytes bytes" } ?: "Reading…",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E)) // Dark code editor styled background for hex contrast
                .padding(8.dp)
        ) {
            val current = window
            if (current == null) {
                item { Text("Reading…", color = Color(0xFF9CDCFE), fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
            } else if (current.hexRows.isEmpty()) {
                item { Text(stringResource(R.string.hex_empty_file), color = Color(0xFF9CDCFE), fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
            } else {
                items(current.hexRows.size, key = { it }, contentType = { "HexRow" }) { index ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).animateItemPlacement(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = current.hexRows[index],
                            fontFamily = MonoFamily,
                            fontSize = 11.sp,
                            color = Color(0xFFD4D4D4)
                        )
                        Text(
                            text = "| ${current.asciiRows[index]}",
                            fontFamily = MonoFamily,
                            fontSize = 11.sp,
                            color = Color(0xFF9CDCFE),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
