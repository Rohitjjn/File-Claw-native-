package com.example.ui.preview.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatIndentIncrease
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The preview route that determines the compact action set. */
enum class PreviewTrayKind { PDF, DOCX, IMAGE, TEXT }
enum class PreviewTrayAction {
    JUMP_PAGE, ROTATE, FIND, PRINT, SHARE, EDIT_DOCUMENT,
    UNDO, REDO, INDENT, BOLD, ITALIC, HISTORY, SAVE, JUMP_LINE, COPY, ENTER_EDIT
}

data class PreviewTrayState(
    val kind: PreviewTrayKind,
    val isEditMode: Boolean,
    val isSearchOpen: Boolean,
    val currentPage: Int = 1,
    val pageCount: Int = 1,
    val hasUnsavedEdits: Boolean = false,
    val hasUndo: Boolean = false,
    val hasRedo: Boolean = false,
    val canEdit: Boolean = false
)

/** Transparent floating sibling overlay; visual controls are independent of coordinator state. */
@Composable
fun PreviewFloatingTray(
    visible: Boolean,
    liftForIme: Boolean,
    state: PreviewTrayState,
    onAction: (PreviewTrayAction) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().then(
                if (liftForIme) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                else Modifier.navigationBarsPadding()
            ).padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color.White.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.12f)),
                shadowElevation = 8.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    when (state.kind) {
                        PreviewTrayKind.PDF -> {
                            PagePill(state, onAction)
                            TrayIcon(Icons.Outlined.RotateRight, "Switch portrait/landscape", PreviewTrayAction.ROTATE, onAction)
                            TrayIcon(Icons.Outlined.Search, "Find in document", PreviewTrayAction.FIND, onAction, state.isSearchOpen)
                            TrayIcon(Icons.Outlined.Print, "Print or Save as PDF", PreviewTrayAction.PRINT, onAction)
                            TrayIcon(Icons.Outlined.Share, "Share PDF", PreviewTrayAction.SHARE, onAction)
                        }
                        PreviewTrayKind.DOCX -> {
                            PagePill(state, onAction)
                            TrayIcon(Icons.Outlined.RotateRight, "Switch portrait/landscape", PreviewTrayAction.ROTATE, onAction)
                            TrayIcon(Icons.Outlined.Search, "Find in document", PreviewTrayAction.FIND, onAction, state.isSearchOpen)
                            TrayIcon(Icons.Outlined.Print, "Print document", PreviewTrayAction.PRINT, onAction)
                            TrayIcon(Icons.Default.Edit, "Edit", PreviewTrayAction.EDIT_DOCUMENT, onAction)
                        }
                        PreviewTrayKind.IMAGE -> {
                            Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Image, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Photo Viewer", fontSize = 12.sp, color = Color.Black)
                                }
                            }
                            TrayIcon(Icons.Outlined.Print, "Print photo", PreviewTrayAction.PRINT, onAction)
                            TrayIcon(Icons.Outlined.Share, "Share image", PreviewTrayAction.SHARE, onAction)
                        }
                        PreviewTrayKind.TEXT -> if (state.isEditMode) {
                            TrayIcon(Icons.Default.Undo, "Undo", PreviewTrayAction.UNDO, onAction, enabled = state.hasUndo)
                            TrayIcon(Icons.Default.Redo, "Redo", PreviewTrayAction.REDO, onAction, enabled = state.hasRedo)
                            TrayIcon(Icons.Outlined.FormatIndentIncrease, "Indent", PreviewTrayAction.INDENT, onAction)
                            TrayIcon(Icons.Default.FormatBold, "Bold", PreviewTrayAction.BOLD, onAction)
                            TrayIcon(Icons.Default.FormatItalic, "Italic", PreviewTrayAction.ITALIC, onAction)
                            TrayIcon(Icons.Default.History, "Edit history", PreviewTrayAction.HISTORY, onAction, enabled = state.hasUndo)
                            Surface(
                                shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onAction(PreviewTrayAction.SAVE) }
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
                                }
                            }
                        } else {
                            TrayIcon(Icons.Outlined.Search, "Find in document", PreviewTrayAction.FIND, onAction, state.isSearchOpen)
                            TrayIcon(Icons.Outlined.FormatLineSpacing, "Jump to line", PreviewTrayAction.JUMP_LINE, onAction)
                            TrayIcon(Icons.Outlined.Print, "Print or export as PDF", PreviewTrayAction.PRINT, onAction)
                            TrayIcon(Icons.Outlined.ContentCopy, "Copy all text", PreviewTrayAction.COPY, onAction)
                            if (state.canEdit) TrayIcon(Icons.Default.Edit, "Edit", PreviewTrayAction.ENTER_EDIT, onAction)
                            TrayIcon(Icons.Outlined.Share, "Share file", PreviewTrayAction.SHARE, onAction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagePill(state: PreviewTrayState, onAction: (PreviewTrayAction) -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp), color = Color.White,
        modifier = Modifier.clickable { onAction(PreviewTrayAction.JUMP_PAGE) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Icon(Icons.Default.MenuBook, "Page indicator", modifier = Modifier.size(16.dp), tint = Color.Black)
            Spacer(Modifier.width(6.dp))
            Text("${state.currentPage} / ${if (state.kind == PreviewTrayKind.DOCX) "≈" else ""}${state.pageCount.coerceAtLeast(1)}", fontSize = 12.sp, color = Color.Black)
        }
    }
}

@Composable
private fun TrayIcon(
    icon: ImageVector,
    description: String,
    action: PreviewTrayAction,
    onAction: (PreviewTrayAction) -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    IconButton(onClick = { onAction(action) }, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(icon, description, modifier = Modifier.size(18.dp), tint = if (selected) MaterialTheme.colorScheme.primary else Color.Black)
    }
}
