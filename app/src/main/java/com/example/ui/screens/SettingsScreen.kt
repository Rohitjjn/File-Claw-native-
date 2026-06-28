package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SettingEntity
import com.example.ui.component.ClaudeAppBar
import com.example.ui.component.ClaudeCard
import com.example.viewmodel.MainViewModel

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import io.iamjosephmj.flinger.flings.flingBehavior
import io.iamjosephmj.flinger.FlingPresets
import com.example.utils.CacheManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settingsState.collectAsState()

    val themes = remember {
        listOf(
            "Light" to "Default Claude aesthetic",
            "Dark" to "Easy on the eyes",
            "System" to "Follow device theme"
        )
    }
    val fontSizes = remember { listOf("Small", "Medium", "Large") }
    val tabSizes = remember { listOf(2, 4) }
    val encodings = remember { listOf("Auto", "UTF-8", "UTF-16", "ASCII", "ISO-8859-1", "Windows-1252", "MacRoman") }
    val historyLimits = remember { listOf(10, 20, 50, 100) }

    Scaffold(
        topBar = {
            ClaudeAppBar(
                title = "Settings",
                onNavIconClick = onBackClick,
                navIcon = Icons.AutoMirrored.Filled.ArrowBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.testTag("settings_screen")
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(
                    state = scrollState,
                    flingBehavior = flingBehavior(scrollConfiguration = FlingPresets.ultraSmooth())
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. APPEARANCE
            Column {
                Text(
                    text = "APPEARANCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                
                ClaudeCard {
                    // Modern radio-styled row selection
                    themes.forEachIndexed { i, pair ->
                        val key = pair.first
                        val desc = pair.second
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSettings(settings.copy(theme = key))
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = (settings.theme == key),
                                onClick = {
                                    viewModel.updateSettings(settings.copy(theme = key))
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = key,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        if (i < themes.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // 2. FONT SIZE SCALE
            Column {
                Text(
                    text = "FONT SIZE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                
                SegmentedControl(
                    items = fontSizes,
                    selectedItem = settings.fontSize,
                    onItemSelected = { size ->
                        viewModel.updateSettings(settings.copy(fontSize = size))
                    },
                    itemLabel = { it }
                )
            }

            // 3. EDITOR CONFIGURATIONS
            Column {
                Text(
                    text = "EDITOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                
                ClaudeCard {
                    SettingsToggleRow(
                        title = "Show Line Numbers",
                        checked = settings.showLineNumbers,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(showLineNumbers = it)) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    SettingsToggleRow(
                        title = "Word Wrap",
                        checked = settings.wordWrap,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(wordWrap = it)) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    SettingsToggleRow(
                        title = "Auto-save Drafts",
                        description = "Restore unsaved edits later",
                        checked = settings.autoSaveDrafts,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(autoSaveDrafts = it)) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    SettingsToggleRow(
                        title = "Default to Edit on open",
                        checked = settings.defaultToEditOnOpen,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(defaultToEditOnOpen = it)) }
                    )
                }
            }

            // 4. TAB SIZE (SEGMENTED)
            Column {
                Text(
                    text = "TAB SIZE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                SegmentedControl(
                    items = tabSizes,
                    selectedItem = settings.tabSize,
                    onItemSelected = { size ->
                        viewModel.updateSettings(settings.copy(tabSize = size))
                    },
                    itemLabel = { "$it spaces" }
                )
            }

            // 5. DEFAULT ENCODING
            Column {
                Text(
                    text = "DEFAULT ENCODING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(encodings.size) { index ->
                        val encode = encodings[index]
                        val isSelected = (encode == settings.defaultEncoding)
                        val bg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        val border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                        Surface(
                            onClick = { viewModel.updateSettings(settings.copy(defaultEncoding = encode)) },
                            shape = RoundedCornerShape(16.dp),
                            color = bg,
                            border = border,
                            modifier = Modifier.height(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(
                                    text = encode,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }

            // 6. STORAGE & HISTORY
            Column {
                Text(
                    text = "STORAGE & HISTORY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClaudeCard {
                        Column {
                            java.lang.String.valueOf("History Limit") // unused dummy check
                            Text(
                                text = "History Limit",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            SegmentedControl(
                                items = historyLimits,
                                selectedItem = settings.historyLimit,
                                onItemSelected = { limit ->
                                    viewModel.updateSettings(settings.copy(historyLimit = limit))
                                },
                                itemLabel = { "$it" }
                            )
                        }
                    }

                    ClaudeCard {
                        SettingsActionRow(
                            title = "Clear File History",
                            icon = Icons.Outlined.History,
                            iconColor = MaterialTheme.colorScheme.error,
                            onClick = { viewModel.clearRecentFiles() }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        SettingsActionRow(
                            title = "Clear Editor Cache",
                            icon = Icons.Outlined.CleaningServices,
                            iconColor = MaterialTheme.colorScheme.primary,
                            onClick = { viewModel.clearEditorCache() }
                        )
                    }

                    CacheSettingsItem(cacheManager = CacheManager.getInstance(LocalContext.current))
                }
            }

            // ABOUT
            Column {
                Text(
                    text = "ABOUT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                
                ClaudeCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Files Claw v1.0.0",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Preview Everything. Edit Anywhere.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = Color.Transparent
            )
        )
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    icon: ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (iconColor == MaterialTheme.colorScheme.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun <T> SegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { item ->
            val isSelected = (item == selectedItem)
            val bg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
            val border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
            val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            
            Surface(
                onClick = { onItemSelected(item) },
                shape = RoundedCornerShape(8.dp),
                color = bg,
                border = border,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = itemLabel(item),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun CacheSettingsItem(cacheManager: CacheManager) {
    var showClearDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val cacheSize by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(cacheManager.getTotalSize()) }
    val breakdown = cacheManager.getBreakdown()
    
    ClaudeCard {
        androidx.compose.material3.ListItem(
            headlineContent = { Text("Cache Management") },
            supportingContent = { 
                Column {
                    Text("Total: $cacheSize", fontWeight = FontWeight.Medium)
                    breakdown.forEach { (name, size) ->
                        Text("$name: $size", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            trailingContent = {
                androidx.compose.material3.TextButton(onClick = { showClearDialog = true }) {
                    Text("Clear")
                }
            }
        )
    }
    
    if (showClearDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Cache") },
            text = { Text("This will free up $cacheSize. All previews and thumbnails will need to be regenerated.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    CoroutineScope(Dispatchers.IO).launch { cacheManager.nuke() }
                    showClearDialog = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}
