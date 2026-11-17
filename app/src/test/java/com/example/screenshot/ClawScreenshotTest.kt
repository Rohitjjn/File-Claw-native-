package com.example.screenshot

import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.AllFilesScreen
import com.example.ui.screens.NoticeBanner
import com.example.ui.screens.PropertyItem
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FilesClawTheme
import com.example.viewmodel.MainViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 2 golden-master baselines. Recorded once via `./gradlew recordRoborazziDebug`,
 * enforced by `verifyRoborazziDebug` in CI (failing screenshot = blocked run).
 *
 * Light + dark for every case. HomeScreen is deliberately excluded: its greeting is derived
 * from the wall-clock hour and would flake across record/verify times (see docs/TODO.md).
 * VM-backed screens render against a fresh, empty database, which is deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ClawScreenshotTest {

    private fun viewModel(): MainViewModel =
        MainViewModel(ApplicationProvider.getApplicationContext())

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/snapshots/images/$name.png", content = content)
    }

    // ---- stateless components ----

    @Test
    fun noticeBannerLight() = capture("notice_banner_light") {
        FilesClawTheme(themeSetting = "Light") { NoticeBanner("This file type is read-only.") }
    }

    @Test
    fun noticeBannerDark() = capture("notice_banner_dark") {
        FilesClawTheme(themeSetting = "Dark") { NoticeBanner("This file type is read-only.") }
    }

    @Test
    fun propertyItemLight() = capture("property_item_light") {
        FilesClawTheme(themeSetting = "Light") { PropertyItem("Total size", "12.4 MB") }
    }

    @Test
    fun propertyItemDark() = capture("property_item_dark") {
        FilesClawTheme(themeSetting = "Dark") { PropertyItem("Total size", "12.4 MB") }
    }

    // ---- VM-backed screens (fresh, empty database — deterministic empty states) ----

    @Test
    fun allFilesScreenLight() = capture("all_files_screen_light") {
        FilesClawTheme(themeSetting = "Light") {
            AllFilesScreen(viewModel = viewModel(), onBackClick = {}, onNavigateToPreview = {})
        }
    }

    @Test
    fun allFilesScreenDark() = capture("all_files_screen_dark") {
        FilesClawTheme(themeSetting = "Dark") {
            AllFilesScreen(viewModel = viewModel(), onBackClick = {}, onNavigateToPreview = {})
        }
    }

    @Test
    fun searchScreenLight() = capture("search_screen_light") {
        FilesClawTheme(themeSetting = "Light") {
            SearchScreen(viewModel = viewModel(), onBackClick = {})
        }
    }

    @Test
    fun searchScreenDark() = capture("search_screen_dark") {
        FilesClawTheme(themeSetting = "Dark") {
            SearchScreen(viewModel = viewModel(), onBackClick = {})
        }
    }

    @Test
    fun settingsScreenLight() = capture("settings_screen_light") {
        FilesClawTheme(themeSetting = "Light") {
            SettingsScreen(viewModel = viewModel(), onBackClick = {})
        }
    }

    @Test
    fun settingsScreenDark() = capture("settings_screen_dark") {
        FilesClawTheme(themeSetting = "Dark") {
            SettingsScreen(viewModel = viewModel(), onBackClick = {})
        }
    }
}
