package com.example.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ClaudeMutedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaudeAppBar(
    title: String,
    onNavIconClick: (() -> Unit)? = null,
    navIcon: ImageVector? = Icons.Default.Menu,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .testTag("claude_app_bar")
            .fillMaxWidth()
    ) {
        Column {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onNavIconClick != null && navIcon != null) {
                        IconButton(onClick = onNavIconClick, modifier = Modifier.testTag("app_bar_nav_icon")) {
                            Icon(
                                imageVector = navIcon,
                                contentDescription = "Navigation Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                windowInsets = WindowInsets.statusBars
            )
        }
    }
}

@Composable
fun ClaudeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color? = null,
    testTag: String = "claude_button"
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp), // Premium crisp Claude styling (rounded.md)
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor ?: MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = (containerColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier
            .testTag(testTag)
            .minimumInteractiveComponentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}


@Composable
fun ClaudeCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .let {
                if (onClick != null) {
                    it.clickable(onClick = onClick)
                } else {
                    it
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun FileIcon(
    extension: String,
    modifier: Modifier = Modifier,
    size: Int = 40,
    isSample: Boolean = false,
    isRecent: Boolean = true
) {
    val isDark = MaterialTheme.colorScheme.background == com.example.ui.theme.ClaudeOnyx
    
    val iconBgColor = if (isRecent) {
        if (isDark) Color(0xFF3B2520) else Color(0xFFFBECE5)
    } else {
        if (isDark) Color(0xFF1F2E2B) else Color(0xFFE6F5F2)
    }

    val iconColor = if (isRecent) {
        Color(0xFFCC785C)
    } else {
        Color(0xFF5DB8A6)
    }

    val iconVector = when (extension.lowercase()) {
        "md" -> Icons.Outlined.Book
        "zip" -> Icons.Outlined.FolderZip
        "csv" -> Icons.Outlined.TableChart
        "kt" -> Icons.Outlined.Code
        "txt" -> Icons.Outlined.Description
        "pdf" -> Icons.Outlined.PictureAsPdf
        "docx" -> Icons.Outlined.InsertDriveFile
        "pptx" -> Icons.Outlined.BackupTable
        "ppt" -> Icons.Outlined.BackupTable
        "mp3" -> Icons.Outlined.MusicNote
        "wav" -> Icons.Outlined.MusicNote
        "m4a" -> Icons.Outlined.MusicNote
        "mp4" -> Icons.Outlined.PlayCircle
        "mkv" -> Icons.Outlined.PlayCircle
        "webm" -> Icons.Outlined.PlayCircle
        else -> Icons.Outlined.InsertDriveFile
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = iconBgColor,
        border = BorderStroke(1.dp, iconColor.copy(alpha = 0.2f)),
        modifier = modifier.size(size.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = iconVector,
                contentDescription = "$extension icon",
                tint = iconColor,
                modifier = Modifier.size((size * 0.55).dp)
            )
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}

fun formatElapsedTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 60 * 1000) return "just now"
    val mins = diff / (60 * 1000)
    if (mins < 60) return if (mins == 1L) "1 min ago" else "$mins mins ago"
    val hours = mins / 60
    if (hours < 24) return if (hours == 1L) "1 hour ago" else "$hours hours ago"
    val days = hours / 24
    return if (days == 1L) "1 day ago" else "$days days ago"
}

