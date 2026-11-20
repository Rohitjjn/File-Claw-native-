package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.NavDestination
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.platform.LocalConfiguration
import com.example.ui.component.ClawNavRail
import com.example.ui.screens.AllFilesScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.FilePreviewScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.navigation.ClawDestination
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.FilesClawTheme
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type


/** Type-safe route check. @Serializable objects serialize to their class serial name (the FQCN). */
private inline fun <reified T> NavDestination?.isDestinationOf(): Boolean =
    this?.route == T::class.qualifiedName

class MainActivity : ComponentActivity() {

    private companion object {
        const val STATE_SKIP_GATE = "skip_permission_gate"
    }

    private val _permissionCheckState = mutableStateOf(false)

    /** Same instance Compose gets from viewModel(): both are scoped to this Activity. */
    private val mainViewModel: MainViewModel by viewModels()

    /**
     * Shortcut intents to act on. Deliberately Compose state that starts null on every (re)creation:
     * the old code keyed a LaunchedEffect on Activity.getIntent(), which is not state, so a rotation
     * re-ran it and re-imported the same file / re-opened the last file each time.
     */
    private val pendingShortcutIntent = mutableStateOf<android.content.Intent?>(null)

    /** True once the user opened a file from another app or chose to continue without all-files access. */
    private val skipPermissionGate = mutableStateOf(false)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SKIP_GATE, skipPermissionGate.value)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /** Runs exactly once per delivered intent (never on configuration changes). */
    private fun handleIncomingIntent(actIntent: android.content.Intent) {
        if (actIntent.action == android.content.Intent.ACTION_VIEW) {
            actIntent.data?.let { uri ->
                // Opening a content:// document needs no broad storage permission at all.
                skipPermissionGate.value = true
                mainViewModel.importFileFromUri(uri)
            }
        } else {
            pendingShortcutIntent.value = actIntent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _permissionCheckState.value = hasRequiredPermissions(this)
        skipPermissionGate.value = savedInstanceState?.getBoolean(STATE_SKIP_GATE, false) == true
        if (savedInstanceState == null) handleIncomingIntent(intent)

        val requestPermissionLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            _permissionCheckState.value = hasRequiredPermissions(this)
        }

        val requestStandardPermissions = {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_MEDIA_IMAGES,
                        android.Manifest.permission.READ_MEDIA_VIDEO,
                        android.Manifest.permission.READ_MEDIA_AUDIO
                    )
                )
            } else {
                requestPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }

        val requestManageAllFilesPermission = {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            val settings by mainViewModel.settingsState.collectAsState()
            val fileEvent by mainViewModel.fileEvent.collectAsState()
            val hasPermissions = _permissionCheckState.value || skipPermissionGate.value

            // Display dynamic notifications (e.g. Save completed, Import files complete)
            LaunchedEffect(fileEvent) {
                fileEvent?.let { message ->
                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                    mainViewModel.clearFileEvent()
                }
            }

            FilesClawTheme(themeSetting = settings.theme) {
                if (!hasPermissions) {
                    val isManageAllFilesRequired = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                    PermissionGateScreen(
                        onRequestPermissions = requestStandardPermissions,
                        onRequestAllFilesAccess = requestManageAllFilesPermission,
                        isManageAllFilesRequired = isManageAllFilesRequired,
                        onContinueWithoutAccess = { skipPermissionGate.value = true }
                    )
                } else {
                    val navController = rememberNavController()
                    val recentFilesForPalette by mainViewModel.recentFilesState.collectAsState()
                    var quickOpenVisible by androidx.compose.runtime.mutableStateOf(false)

                    // Phase 3c (docs/UI_FRONTIER.md §5.3): forward navigation. Navigation Compose
                    // has no forward stack, so Alt+Left records the route it leaves and Alt+Right
                    // replays it. Any navigation that is neither of those (rail, palette, file
                    // open, system back) clears the recorded stack — the one deviation from
                    // browser semantics is that the system back gesture clears it too.
                    val forwardStack = androidx.compose.runtime.mutableStateListOf<String>()
                    val navCause = androidx.compose.runtime.mutableStateOf("other")
                    androidx.compose.runtime.DisposableEffect(navController) {
                        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, _, _ ->
                            if (navCause.value == "other") forwardStack.clear()
                            navCause.value = "other"
                        }
                        navController.addOnDestinationChangedListener(listener)
                        onDispose { navController.removeOnDestinationChangedListener(listener) }
                    }

                    // Dynamic Shortcuts Registration
                    LaunchedEffect(Unit) {
                        try {
                            val shortcutLastOpened = androidx.core.content.pm.ShortcutInfoCompat.Builder(this@MainActivity, "shortcut_last_opened")
                                .setShortLabel("Last Opened File")
                                .setLongLabel("Open the last opened file")
                                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this@MainActivity, R.drawable.ic_shortcut_last_opened))
                                .setIntent(
                                    android.content.Intent(this@MainActivity, MainActivity::class.java).apply {
                                        action = "com.example.action.LAST_OPENED"
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                )
                                .build()

                            val shortcutHistory = androidx.core.content.pm.ShortcutInfoCompat.Builder(this@MainActivity, "shortcut_history")
                                .setShortLabel("History")
                                .setLongLabel("View recent files history")
                                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this@MainActivity, R.drawable.ic_shortcut_history))
                                .setIntent(
                                    android.content.Intent(this@MainActivity, MainActivity::class.java).apply {
                                        action = "com.example.action.HISTORY"
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                )
                                .build()

                            val shortcutSettings = androidx.core.content.pm.ShortcutInfoCompat.Builder(this@MainActivity, "shortcut_settings")
                                .setShortLabel("Settings")
                                .setLongLabel("Open app settings")
                                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this@MainActivity, R.drawable.ic_shortcut_settings))
                                .setIntent(
                                    android.content.Intent(this@MainActivity, MainActivity::class.java).apply {
                                        action = "com.example.action.SETTINGS"
                                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                )
                                .build()

                            androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(
                                this@MainActivity,
                                listOf(shortcutLastOpened, shortcutHistory, shortcutSettings)
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Handle App Shortcuts Intents
                    LaunchedEffect(pendingShortcutIntent.value) {
                        pendingShortcutIntent.value?.let { actIntent ->
                            val action = actIntent.action
                            if (action == "com.example.action.LAST_OPENED") {
                                try {
                                    val lastFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val db = com.example.data.AppDatabase.getDatabase(applicationContext)
                                        db.recentFileDao().getAllRecentFiles().first().firstOrNull()
                                    }
                                    if (lastFile != null) {
                                        mainViewModel.openFile(lastFile)
                                        if (lastFile.extension.lowercase() == "txt" && settings.defaultToEditOnOpen) {
                                            navController.navigate(ClawDestination.Editor) {
                                                launchSingleTop = true
                                            }
                                        } else {
                                            navController.navigate(ClawDestination.Preview) {
                                                launchSingleTop = true
                                            }
                                        }
                                    } else {
                                        Toast.makeText(applicationContext, "No history found", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else if (action == "com.example.action.HISTORY") {
                                navController.navigate(ClawDestination.Home) {
                                    popUpTo(ClawDestination.Splash) { inclusive = true }
                                }
                            } else if (action == "com.example.action.SETTINGS") {
                                navController.navigate(ClawDestination.Settings) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                    
                    val navigateToPreviewWithEditOnOpen = {
                        val fileState = mainViewModel.currentFileState.value
                        val dest = navController.currentBackStackEntry?.destination
                        val onPreviewOrEditor = dest.isDestinationOf<ClawDestination.Preview>() == true ||
                            dest.isDestinationOf<ClawDestination.Editor>() == true
                        if (!onPreviewOrEditor) {
                            if (fileState.opensInEditor() && settings.defaultToEditOnOpen) {
                                navController.navigate(ClawDestination.Editor) {
                                    launchSingleTop = true
                                }
                            } else {
                                navController.navigate(ClawDestination.Preview) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }

                    // Collect navigation events via one-shot channel flow
                    LaunchedEffect(mainViewModel) {
                        mainViewModel.navigationEvent.collect { event ->
                            when (event) {
                                is MainViewModel.NavigationEvent.NavigateToPreview -> {
                                    val dest = navController.currentBackStackEntry?.destination
                                    val onPreviewOrEditor = dest.isDestinationOf<ClawDestination.Preview>() == true ||
                                        dest.isDestinationOf<ClawDestination.Editor>() == true
                                    if (!onPreviewOrEditor) {
                                        if (event.fileState.opensInEditor() && settings.defaultToEditOnOpen) {
                                            navController.navigate(ClawDestination.Editor) {
                                                launchSingleTop = true
                                            }
                                        } else {
                                            navController.navigate(ClawDestination.Preview) {
                                                launchSingleTop = true
                                            }
                                        }
                                    } else if (dest.isDestinationOf<ClawDestination.Preview>() == true && event.fileState.opensInEditor() && settings.defaultToEditOnOpen) {
                                        navController.navigate(ClawDestination.Editor) {
                                            popUpTo(ClawDestination.Preview) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                }
                                is MainViewModel.NavigationEvent.ShowError -> {
                                    Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }

                    // Phase 3a: glass-at-root — ambient glow floats behind every destination.
                    // Reading surfaces stay opaque by contract (docs/UI_FRONTIER.md §4.5/§4.6).
                    // Phase 3b: Quick Open (Ctrl/Cmd+K) — files + actions palette.
                    val isExpandedLayout = LocalConfiguration.current.screenWidthDp >= 840
                    val railBackStackEntry by navController.currentBackStackEntryAsState()
                    val railDestination = railBackStackEntry?.destination
                    val digitKeys = mapOf(
                        Key.One to 0, Key.Two to 1, Key.Three to 2, Key.Four to 3, Key.Five to 4,
                        Key.Six to 5, Key.Seven to 6, Key.Eight to 7, Key.Nine to 8
                    )
                    val onShortcutKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = shortcut@{ event ->
                        if (event.type != KeyEventType.KeyDown) return@shortcut false
                        val isMod = event.isCtrlPressed || event.isMetaPressed
                        when {
                            // Quick Open — Ctrl/Cmd+K or Ctrl/Cmd+P
                            isMod && (event.key == Key.K || event.key == Key.P) -> {
                                quickOpenVisible = !quickOpenVisible
                                true
                            }
                            // Settings — Ctrl/Cmd+,
                            isMod && event.key == Key.Comma -> {
                                quickOpenVisible = false
                                navController.navigate(ClawDestination.Settings) { launchSingleTop = true }
                                true
                            }
                            // Back — Alt+Left
                            event.isAltPressed && event.key == Key.DirectionLeft -> {
                                if (quickOpenVisible) {
                                    quickOpenVisible = false
                                } else {
                                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                                    if (currentRoute != null && navController.previousBackStackEntry != null) {
                                        forwardStack.add(currentRoute)
                                        navCause.value = "back"
                                    }
                                    navController.popBackStack()
                                }
                                true
                            }
                            // Forward — Alt+Right (replays the route Alt+Left recorded)
                            event.isAltPressed && event.key == Key.DirectionRight -> {
                                val route = forwardStack.lastOrNull()
                                if (route != null) {
                                    forwardStack.removeAt(forwardStack.size - 1)
                                    navCause.value = "forward"
                                    navController.navigate(route) { launchSingleTop = true }
                                    true
                                } else {
                                    false
                                }
                            }
                            // Copy current file path — Ctrl/Cmd+Shift+C
                            isMod && event.isShiftPressed && event.key == Key.C -> {
                                val path = mainViewModel.currentFileState.value.fileOrNull?.path
                                if (path == null) {
                                    false
                                } else {
                                    val clip = android.content.ClipData.newPlainText("path", path)
                                    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(clip)
                                    Toast.makeText(applicationContext, R.string.path_copied, Toast.LENGTH_SHORT).show()
                                    true
                                }
                            }
                            // Open 1st…9th recent file — Ctrl/Cmd+1…9
                            isMod && !event.isShiftPressed && digitKeys.containsKey(event.key) -> {
                                val idx = digitKeys.getValue(event.key)
                                if (idx < recentFilesForPalette.size) {
                                    quickOpenVisible = false
                                    mainViewModel.openFile(recentFilesForPalette[idx])
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxSize()
                    ) {
                    if (isExpandedLayout) {
                        ClawNavRail(
                            isHome = railDestination.isDestinationOf<ClawDestination.Home>() == true,
                            isAllFiles = railDestination.isDestinationOf<ClawDestination.AllFiles>() == true,
                            isSearch = railDestination.isDestinationOf<ClawDestination.Search>() == true,
                            isSettings = railDestination.isDestinationOf<ClawDestination.Settings>() == true,
                            onHome = {
                                navController.navigate(ClawDestination.Home) {
                                    popUpTo(ClawDestination.Splash) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            onAllFiles = { navController.navigate(ClawDestination.AllFiles) { launchSingleTop = true } },
                            onSearch = { navController.navigate(ClawDestination.Search) { launchSingleTop = true } },
                            onSettings = { navController.navigate(ClawDestination.Settings) { launchSingleTop = true } }
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f).onKeyEvent { event -> onShortcutKey(event) }
                    ) {
                    com.example.ui.theme.GlassBackdrop {
                    NavHost(
                        navController = navController,
                        startDestination = ClawDestination.Splash,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = {
                            scaleIn(
                                initialScale = 0.97f,
                                animationSpec = tween(80, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(80))
                        },
                        exitTransition = {
                            scaleOut(
                                targetScale = 1.03f,
                                animationSpec = tween(60, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(60))
                        },
                        popEnterTransition = {
                            scaleIn(
                                initialScale = 1.03f,
                                animationSpec = tween(80, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(80))
                        },
                        popExitTransition = {
                            scaleOut(
                                targetScale = 0.97f,
                                animationSpec = tween(60, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(60))
                        }
                    ) {
                        composable<ClawDestination.Splash> {
                            SplashScreen(
                                onNavigateToHome = {
                                    navController.navigate(ClawDestination.Home) {
                                        popUpTo(ClawDestination.Splash) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable<ClawDestination.Home> {
                            HomeScreen(
                                viewModel = mainViewModel,
                                onNavigateToPreview = navigateToPreviewWithEditOnOpen,
                                onNavigateToSettings = {
                                    navController.navigate(ClawDestination.Settings)
                                },
                                onNavigateToAllFiles = {
                                    navController.navigate(ClawDestination.AllFiles)
                                },
                                onNavigateToSearch = {
                                    navController.navigate(ClawDestination.Search)
                                }
                            )
                        }

                        composable<ClawDestination.AllFiles> {
                            AllFilesScreen(
                                viewModel = mainViewModel,
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onNavigateToPreview = navigateToPreviewWithEditOnOpen
                            )
                        }

                        composable<ClawDestination.Search> {
                            SearchScreen(
                                viewModel = mainViewModel,
                                onBackClick = {
                                    mainViewModel.updateSearchQuery("")
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable<ClawDestination.Preview> {
                            FilePreviewScreen(
                                viewModel = mainViewModel,
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onNavigateToEditor = {
                                    navController.navigate(ClawDestination.Editor)
                                }
                            )
                        }

                        composable<ClawDestination.Editor> {
                            EditorScreen(
                                viewModel = mainViewModel,
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable<ClawDestination.Settings> {
                            SettingsScreen(
                                viewModel = mainViewModel,
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                    }
                    if (quickOpenVisible) {
                        com.example.ui.component.CommandPalette(
                            recentFiles = recentFilesForPalette,
                            onOpenFile = { entity ->
                                quickOpenVisible = false
                                mainViewModel.openFile(entity)
                            },
                            onAction = { action ->
                                quickOpenVisible = false
                                when (action) {
                                    com.example.ui.component.CommandPaletteAction.TOGGLE_THEME ->
                                        mainViewModel.updateSettings(settings.copy(theme = if (settings.theme == "Dark") "Light" else "Dark"))
                                    com.example.ui.component.CommandPaletteAction.OPEN_SETTINGS ->
                                        navController.navigate(ClawDestination.Settings)
                                    com.example.ui.component.CommandPaletteAction.OPEN_SEARCH ->
                                        navController.navigate(ClawDestination.Search)
                                    com.example.ui.component.CommandPaletteAction.OPEN_ALL_FILES ->
                                        navController.navigate(ClawDestination.AllFiles)
                                    com.example.ui.component.CommandPaletteAction.CLEAR_RECENTS ->
                                        mainViewModel.clearRecentFiles()
                                }
                            },
                            onDismiss = { quickOpenVisible = false }
                        )
                    }
                    }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _permissionCheckState.value = hasRequiredPermissions(this)
    }

    private fun hasRequiredPermissions(context: android.content.Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}

@Composable
fun PermissionGateScreen(
    onRequestPermissions: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    isManageAllFilesRequired: Boolean,
    onContinueWithoutAccess: () -> Unit = {}
) {
    com.example.ui.theme.GlassBackdrop {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(
                    state = rememberScrollState(),
                )
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderCopy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Storage Access Required",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "To search, preview, and edit files on your device safely, File Claw requires storage access permissions.",
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onContinueWithoutAccess) {
                Text("Continue without full access")
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (!isManageAllFilesRequired) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Standard Storage Access", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Required to read and scan documents, images, and audio across standard directory indices.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRequestPermissions,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_permission_btn")
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Manage All Files Access", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Allowing 'All Files Access' ensures File Claw can scan search indices, parse subfolders, and edit documents safely.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRequestAllFilesAccess,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_manage_storage_btn")
                        ) {
                            Text("Enable in Settings")
                        }
                    }
                }
            }
        }
    }
    }
}

/** Only fully loaded, writable text files go straight into the editor; truncated or converted views stay read-only. */
private fun MainViewModel.FileContentState.opensInEditor(): Boolean =
    this is MainViewModel.FileContentState.TextSuccess && this.editable
