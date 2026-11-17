package com.example.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.AllFilesScreen
import com.example.ui.screens.NoticeBanner
import com.example.ui.screens.PropertyItem
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FilesClawTheme
import com.example.viewmodel.MainViewModel
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 2 golden-master baselines. Recorded once via `./gradlew recordRoborazziDebug`,
 * enforced forever by `verifyRoborazziDebug` in CI (failing screenshot = blocked PR).
 *
 * Light + dark for every case. HomeScreen is deliberately excluded: its greeting is derived
 * from the wall-clock hour and would flake across record/verify times (see docs/TODO.md).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ClawScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun newViewModel(): MainViewModel =
        MainViewModel(ApplicationProvider.getApplicationContext())

    private fun shot(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { content() }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/images/$name.png")
    }

    private fun vmShot(name: String, screen: @Composable (MainViewModel) -> Unit) {
        val vm = newViewModel()
        composeRule.setContent { screen(vm) }
        composeRule.waitUntil(timeoutMillis = 10_000) { vm.isInitialized.value }
        composeRule.onRoot().captureRoboImage("src/test/snapshots/images/$name.png")
    }

    // ---- stateless components ----

    @Test fun noticeBannerLight() = shot("notice_banner_light") {
        FilesClawTheme(themeSetting = "Light") { NoticeBanner("This file type is read-only.") }
    }

    @Test fun noticeBannerDark() = shot("notice_banner_dark") {
        FilesClawTheme(themeSetting = "Dark") { NoticeBanner("This file type is read-only.") }
    }

    @Test fun propertyItemLight() = shot("property_item_light") {
        FilesClawTheme(themeSetting = "Light") { PropertyItem("Total size", "12.4 MB") }
    }

    @Test fun propertyItemDark() = shot("property_item_dark") {
        FilesClawTheme(themeSetting = "Dark") { PropertyItem("Total size", "12.4 MB") }
    }

    // ---- VM-backed screens (fresh, empty database — deterministic empty states) ----

    @Test fun allFilesScreenLight() = vmShot("all_files_screen_light") { vm ->
        FilesClawTheme(themeSetting = "Light") {
            AllFilesScreen(viewModel = vm, onBackClick = {}, onNavigateToPreview = {})
        }
    }

    @Test fun allFilesScreenDark() = vmShot("all_files_screen_dark") { vm ->
        FilesClawTheme(themeSetting = "Dark") {
            AllFilesScreen(viewModel = vm, onBackClick = {}, onNavigateToPreview = {})
        }
    }

    @Test fun searchScreenLight() = vmShot("search_screen_light") { vm ->
        FilesClawTheme(themeSetting = "Light") {
            SearchScreen(viewModel = vm, onBackClick = {})
        }
    }

    @Test fun searchScreenDark() = vmShot("search_screen_dark") { vm ->
        FilesClawTheme(themeSetting = "Dark") {
            SearchScreen(viewModel = vm, onBackClick = {})
        }
    }

    @Test fun settingsScreenLight() = vmShot("settings_screen_light") { vm ->
        FilesClawTheme(themeSetting = "Light") {
            SettingsScreen(viewModel = vm, onBackClick = {})
        }
    }

    @Test fun settingsScreenDark() = vmShot("settings_screen_dark") { vm ->
        FilesClawTheme(themeSetting = "Dark") {
            SettingsScreen(viewModel = vm, onBackClick = {})
        }
    }
}
