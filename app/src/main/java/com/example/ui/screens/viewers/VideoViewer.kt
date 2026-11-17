package com.example.ui.screens.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import androidx.compose.ui.res.stringResource

@Composable
fun VideoPlayer(
    fileEntity: RecentFileEntity,
    modifier: Modifier = Modifier,
    onOpenHex: (() -> Unit)? = null
) {
    val context = LocalContext.current
    // AUD-008: codec failure used to leave a dead 260 dp black box. Now it flips to
    // an inline honest error with Open-with / hex fallbacks.
    val videoDecodeFailed = stringResource(R.string.video_decode_failed)
    var videoError by remember(fileEntity) { mutableStateOf<String?>(null) }
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
        
        if (videoError == null) {
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoPath(fileEntity.path)
                        val controller = android.widget.MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnErrorListener { _, _, _ ->
                            videoError = videoDecodeFailed
                            true
                        }
                        start()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(260.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().height(260.dp)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.video_playback_failed_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = videoError!!,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { openWithFile(context, File(fileEntity.path), fileEntity.extension) }) {
                        Text(stringResource(R.string.action_open_with))
                    }
                    if (onOpenHex != null) {
                        OutlinedButton(onClick = onOpenHex) { Text(stringResource(R.string.action_view_as_hex)) }
                    }
                }
            }
        }
    }
}
