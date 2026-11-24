package com.example.ui.preview.chrome

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Owns the temporary edge-to-edge window styling and restores the host window on disposal. */
@Composable
fun PreviewWindowEffects(activity: Activity?, extension: String?, isDarkTheme: Boolean, barsVisible: Boolean) {
    val originalWindowBars = remember(activity) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            Triple(window.statusBarColor, window.navigationBarColor, controller.isAppearanceLightStatusBars to controller.isAppearanceLightNavigationBars)
        }
    }
    val originalSystemBarsBehavior = remember(activity) {
        activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView).systemBarsBehavior }
    }
    val originalDecorBackground = remember(activity) { activity?.window?.decorView?.background }
    val originalCutoutMode = remember(activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) activity?.window?.attributes?.layoutInDisplayCutoutMode else null
    }
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val normalizedExtension = extension?.lowercase()
        val isDarkCanvas = normalizedExtension != "docx" && isDarkTheme
        val canvasColor = when (normalizedExtension) {
            "pdf" -> android.graphics.Color.rgb(240, 240, 240)
            "docx" -> android.graphics.Color.WHITE
            "doc" -> if (isDarkTheme) android.graphics.Color.rgb(21, 21, 20) else android.graphics.Color.rgb(250, 249, 245)
            else -> if (isDarkTheme) android.graphics.Color.rgb(24, 23, 21) else android.graphics.Color.rgb(250, 249, 245)
        }
        window.decorView.setBackgroundColor(canvasColor)
        insetsController.isAppearanceLightStatusBars = !isDarkCanvas
        insetsController.isAppearanceLightNavigationBars = !isDarkCanvas
        if (!barsVisible) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = originalCutoutMode }
            }
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(activity) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            originalWindowBars?.let { (status, navigation, appearance) ->
                window.statusBarColor = status
                window.navigationBarColor = navigation
                insetsController.isAppearanceLightStatusBars = appearance.first
                insetsController.isAppearanceLightNavigationBars = appearance.second
            }
            originalSystemBarsBehavior?.let { insetsController.systemBarsBehavior = it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = originalCutoutMode }
            }
            window.decorView.background = originalDecorBackground
        }
    }
}
