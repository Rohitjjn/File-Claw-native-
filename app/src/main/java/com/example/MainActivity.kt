package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AllFilesScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.FilePreviewScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.FilesClawTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Dynamic permission requesting for local file indexing
        val permissions = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }

        // Trigger All Files Access settings for Android 11+ if not already granted to enable scanning files on virtual device SDCard
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Please allow All Files Access to search and index files on your device!", Toast.LENGTH_LONG).show()
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
            val mainViewModel: MainViewModel = viewModel()
            val settings by mainViewModel.settingsState.collectAsState()
            val fileEvent by mainViewModel.fileEvent.collectAsState()

            // Handle Open-With Intent launches and file streams
            LaunchedEffect(intent) {
                intent?.let { actIntent ->
                    if (actIntent.action == android.content.Intent.ACTION_VIEW) {
                        actIntent.data?.let { uri ->
                            mainViewModel.importFileFromUri(uri)
                        }
                    }
                }
            }

            // Display dynamic notifications (e.g. Save completed, Import files complete)
            LaunchedEffect(fileEvent) {
                fileEvent?.let { message ->
                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                    mainViewModel.clearFileEvent()
                }
            }

            FilesClawTheme(themeSetting = settings.theme) {
                val navController = rememberNavController()
                
                val navigateToPreviewWithEditOnOpen = {
                    val fileState = mainViewModel.currentFileState.value
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute != "preview" && currentRoute != "editor") {
                        if (fileState is MainViewModel.FileContentState.TextSuccess && settings.defaultToEditOnOpen) {
                            navController.navigate("editor") {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate("preview") {
                                launchSingleTop = true
                            }
                        }
                    }
                }
                
                // Global observer to handle transitioning to the file preview when a file is imported from an intent
                val navigateToPreviewState by mainViewModel.navigateToPreview.collectAsState()
                LaunchedEffect(navigateToPreviewState) {
                    if (navigateToPreviewState) {
                        navigateToPreviewWithEditOnOpen()
                        mainViewModel.resetNavigateToPreview()
                    }
                }
                
                NavHost(
                    navController = navController,
                    startDestination = "splash",
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        scaleIn(
                            initialScale = 0.96f,
                            animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(200))
                    },
                    exitTransition = {
                        scaleOut(
                            targetScale = 1.04f,
                            animationSpec = tween(160, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(160))
                    },
                    popEnterTransition = {
                        scaleIn(
                            initialScale = 1.04f,
                            animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + fadeIn(animationSpec = tween(200))
                    },
                    popExitTransition = {
                        scaleOut(
                            targetScale = 0.96f,
                            animationSpec = tween(160, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(160))
                    }
                ) {
                    composable("splash") {
                        SplashScreen(
                            onNavigateToHome = {
                                navController.navigate("home") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("home") {
                        HomeScreen(
                            viewModel = mainViewModel,
                            onNavigateToPreview = navigateToPreviewWithEditOnOpen,
                            onNavigateToSettings = {
                                navController.navigate("settings")
                            },
                            onNavigateToAllFiles = {
                                navController.navigate("all_files")
                            },
                            onNavigateToSearch = {
                                navController.navigate("search")
                            }
                        )
                    }

                    composable("all_files") {
                        AllFilesScreen(
                            viewModel = mainViewModel,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onNavigateToPreview = navigateToPreviewWithEditOnOpen
                        )
                    }

                    composable("search") {
                        SearchScreen(
                            viewModel = mainViewModel,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onNavigateToPreview = navigateToPreviewWithEditOnOpen
                        )
                    }

                    composable("preview") {
                        FilePreviewScreen(
                            viewModel = mainViewModel,
                            onBackClick = {
                                navController.popBackStack()
                            },
                            onNavigateToEditor = {
                                navController.navigate("editor")
                            }
                        )
                    }

                    composable("editor") {
                        EditorScreen(
                            viewModel = mainViewModel,
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            viewModel = mainViewModel,
                            onBackClick = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
