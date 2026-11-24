package com.example.ui.preview.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Callbacks for preview actions; the chrome component owns no ViewModel or screen state. */
data class PreviewOverflowMenuActions(
    val share: () -> Unit,
    val saveChanges: () -> Unit,
    val exportCopy: () -> Unit,
    val jumpToPage: () -> Unit,
    val openSystemViewer: () -> Unit,
    val openOutline: () -> Unit,
    val openAppearance: () -> Unit,
    val goToLine: () -> Unit,
    val printOrExport: () -> Unit,
    val properties: () -> Unit,
    val removeFromHistory: () -> Unit
)

private data class MenuItemSpec(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val emphasized: Boolean = false
)

/** White elevated menu shared by preview routes; DOCX deliberately has no theme toggle. */
@Composable
fun PreviewOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isPdf: Boolean,
    isDocx: Boolean,
    isEditMode: Boolean,
    hasOutline: Boolean,
    isTextFile: Boolean,
    currentPage: Int,
    pageCount: Int,
    actions: PreviewOverflowMenuActions
) {
    val typography = MaterialTheme.typography
    val items = buildList {
        add(MenuItemSpec("Share file", Icons.Default.Share, actions.share))
        if (isEditMode) {
            add(MenuItemSpec("Save changes", Icons.Default.Save, actions.saveChanges, emphasized = true))
            add(MenuItemSpec("Export modified copy…", Icons.Outlined.DriveFileMove, actions.exportCopy))
        }
        if (isPdf || isDocx) {
            val count = if (isDocx) "≈$pageCount" else "$pageCount"
            add(MenuItemSpec("Go to Page ($currentPage / $count)", Icons.Outlined.InsertDriveFile, actions.jumpToPage))
            if (isPdf) add(MenuItemSpec("Open in System Viewer", Icons.Outlined.Language, actions.openSystemViewer))
        }
        if (hasOutline) add(MenuItemSpec("Document Outline", Icons.AutoMirrored.Outlined.Toc, actions.openOutline))
        if (isTextFile) {
            add(MenuItemSpec("Viewer Appearance", Icons.Outlined.Palette, actions.openAppearance))
            add(MenuItemSpec("Go to Line...", Icons.Outlined.FormatLineSpacing, actions.goToLine))
        }
        add(MenuItemSpec("Print / Export as PDF", Icons.Outlined.Print, actions.printOrExport))
        add(MenuItemSpec("Properties", Icons.Outlined.Info, actions.properties))
        add(MenuItemSpec("Remove from history", Icons.Default.Delete, actions.removeFromHistory, destructive = true))
    }
    MaterialTheme(colorScheme = lightColorScheme(), typography = typography) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = Modifier.background(Color.White, RoundedCornerShape(18.dp))
        ) {
            items.forEach { item ->
                val tint = if (item.destructive) Color(0xFFB3261E) else Color.Black
                DropdownMenuItem(
                    text = {
                        Text(
                            item.label,
                            fontSize = 13.sp,
                            fontWeight = if (item.emphasized) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                            color = tint
                        )
                    },
                    onClick = {
                        onDismissRequest()
                        item.onClick()
                    },
                    leadingIcon = {
                        Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}
