package com.example.ui.screens.viewers

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RecentFileEntity
import java.io.File
import android.media.MediaPlayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Slider
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.contentDescription
import com.example.R
import androidx.compose.ui.res.stringResource

@Composable
fun AudioPlayer(
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier,
    onOpenHex: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(1) }
    // AUD-007: prepare() used to block the main thread and any failure was swallowed,
    // leaving a dead 0:00 player. Now decode is async and failures surface inline.
    val audioDecodeFailed = stringResource(R.string.audio_decode_failed)
    var audioError by remember(fileEntity) { mutableStateOf<String?>(null) }

    LaunchedEffect(fileEntity) {
        val mp = MediaPlayer()
        try {
            mp.setDataSource(fileEntity.path)
            mp.setOnErrorListener { _, _, _ ->
                isPlaying = false
                audioError = audioDecodeFailed
                true
            }
            mp.setOnCompletionListener { isPlaying = false }
            mp.setOnPreparedListener { prepared ->
                mediaPlayer = prepared
                duration = prepared.duration.coerceAtLeast(1)
            }
            mp.prepareAsync() // decode off the main thread (AUD-007)
        } catch (e: Exception) {
            try { mp.release() } catch (ignored: Exception) {}
            audioError = audioDecodeFailed
        }
    }

    LaunchedEffect(mediaPlayer) {
        while (true) {
            delay(500)
            val mp = mediaPlayer ?: break
            try {
                if (mp.isPlaying) currentPos = mp.currentPosition
            } catch (e: Exception) {
                break
            }
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
        if (audioError != null) {
            // Honest failure state instead of a dead player (AUD-006/007).
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.audio_playback_failed_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = audioError!!,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = fileEntity.name,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { openWithFile(context, File(fileEntity.path), fileEntity.extension) }) {
                    Text(stringResource(R.string.action_open_with))
                }
                if (onOpenHex != null) {
                    OutlinedButton(onClick = onOpenHex) { Text(stringResource(R.string.action_view_as_hex)) }
                }
            }
            return@Column
        }

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
