package com.example.ui.preview.chrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

const val PREVIEW_OVERFLOW_MENU_TEST_TAG = "preview_overflow_menu"

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
    val emphasized: Boolean = false
)

private object PreviewMenuPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val desiredX = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val x = desiredX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom
        val above = anchorBounds.top - popupContentSize.height
        val desiredY = if (below + popupContentSize.height <= windowSize.height) below else above
        val y = desiredY.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

/** One rounded, white, elevated overflow popup shared by preview routes. */
@Composable
fun PreviewOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isPdf: Boolean,
    isDocx: Boolean,
    isEditMode: Boolean,
    hasOutline: Boolean,
    isTextFile: Boolean,
    actions: PreviewOverflowMenuActions
) {
    if (!expanded) return
    val typography = MaterialTheme.typography
    val menuShape = RoundedCornerShape(16.dp)
    val items = buildList {
        add(MenuItemSpec("Share file", Icons.Default.Share, actions.share))
        if (isEditMode) {
            add(MenuItemSpec("Save changes", Icons.Default.Save, actions.saveChanges, emphasized = true))
            add(MenuItemSpec("Export modified copy…", Icons.Outlined.DriveFileMove, actions.exportCopy))
        }
        if (isPdf || isDocx) {
            add(MenuItemSpec("Go to Page", Icons.Outlined.InsertDriveFile, actions.jumpToPage))
            if (isPdf) add(MenuItemSpec("Open in System Viewer", Icons.Outlined.Language, actions.openSystemViewer))
        }
        if (hasOutline) add(MenuItemSpec("Document Outline", Icons.AutoMirrored.Outlined.Toc, actions.openOutline))
        if (isTextFile) {
            add(MenuItemSpec("Viewer Appearance", Icons.Outlined.Palette, actions.openAppearance))
            add(MenuItemSpec("Go to Line...", Icons.Outlined.FormatLineSpacing, actions.goToLine))
        }
        add(MenuItemSpec("Print / Export as PDF", Icons.Outlined.Print, actions.printOrExport))
        add(MenuItemSpec("Properties", Icons.Outlined.Info, actions.properties))
        add(MenuItemSpec("Remove from history", Icons.Default.Delete, actions.removeFromHistory))
    }

    Popup(
        popupPositionProvider = PreviewMenuPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        MaterialTheme(colorScheme = lightColorScheme(), typography = typography) {
            Surface(
                modifier = Modifier.testTag(PREVIEW_OVERFLOW_MENU_TEST_TAG),
                shape = menuShape,
                color = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 180.dp, max = 215.dp)
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clickable(role = Role.Button) {
                                    onDismissRequest()
                                    item.onClick()
                                }
                                .semantics(mergeDescendants = true) { }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(item.icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                item.label,
                                style = typography.labelMedium.copy(
                                    fontSize = 13.5.sp,
                                    fontWeight = if (item.emphasized) androidx.compose.ui.text.font.FontWeight.SemiBold else typography.labelMedium.fontWeight
                                ),
                                color = Color.Black,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
