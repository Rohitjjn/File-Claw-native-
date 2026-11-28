package com.example.ui.preview.chrome

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewOverflowMenuInstrumentationTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun docxOverflowMenuShowsFiveCompactActions() {
        composeRule.setContent {
            MaterialTheme {
                PreviewOverflowMenu(
                    expanded = true,
                    onDismissRequest = {},
                    isPdf = false,
                    isDocx = true,
                    isEditMode = false,
                    hasOutline = false,
                    isTextFile = false,
                    actions = PreviewOverflowMenuActions(
                        share = {},
                        saveChanges = {},
                        exportCopy = {},
                        jumpToPage = {},
                        openSystemViewer = {},
                        openOutline = {},
                        openAppearance = {},
                        goToLine = {},
                        printOrExport = {},
                        properties = {},
                        removeFromHistory = {}
                    )
                )
            }
        }

        val expectedLabels = listOf(
            "Share file",
            "Go to Page",
            "Print / Export as PDF",
            "Properties",
            "Remove from history"
        )
        expectedLabels.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        assertEquals(5, composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)

        val density = composeRule.activity.resources.displayMetrics.density
        val menuBounds = composeRule.onNodeWithTag(PREVIEW_OVERFLOW_MENU_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val widthDp = menuBounds.width / density
        assertTrue("Popup width should be 180–215dp, was $widthDp", widthDp in 180f..215f)
        expectedLabels.forEach { label ->
            val heightDp = composeRule.onNodeWithText(label)
                .fetchSemanticsNode().boundsInRoot.height / density
            assertTrue("Menu row '$label' should be 40–44dp, was $heightDp", heightDp in 40f..44f)
        }
    }
}
