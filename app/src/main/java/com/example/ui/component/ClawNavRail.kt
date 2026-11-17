package com.example.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Expanded-width (>= 840 dp) navigation rail — docs/UI_FRONTIER.md §5.2.
 * Compact/medium widths keep the slim top chrome instead (see MainActivity).
 * 72 dp wide, icon + label per stop, single accent on the active destination.
 */
@Composable
fun ClawNavRail(
    isHome: Boolean,
    isAllFiles: Boolean,
    isSearch: Boolean,
    isSettings: Boolean,
    onHome: () -> Unit,
    onAllFiles: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.width(72.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        header = null
    ) {
        RailStop(
            icon = Icons.Outlined.Home,
            label = stringResource(R.string.nav_rail_home),
            selected = isHome,
            onClick = onHome
        )
        RailStop(
            icon = Icons.Outlined.Folder,
            label = stringResource(R.string.nav_rail_all_files),
            selected = isAllFiles,
            onClick = onAllFiles
        )
        RailStop(
            icon = Icons.Outlined.Search,
            label = stringResource(R.string.nav_rail_search),
            selected = isSearch,
            onClick = onSearch
        )
        RailStop(
            icon = Icons.Outlined.Settings,
            label = stringResource(R.string.nav_rail_settings),
            selected = isSettings,
            onClick = onSettings
        )
    }
}

@Composable
private fun RailStop(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
        alwaysShowLabel = true
    )
}
