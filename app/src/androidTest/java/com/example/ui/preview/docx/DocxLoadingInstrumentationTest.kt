package com.example.ui.preview.docx

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocxLoadingInstrumentationTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun loadingOverlayIsOnlyWhiteSpinnerAndLoadingLabel() {
        composeRule.setContent {
            MaterialTheme {
                DocxLoadingOverlay()
            }
        }

        composeRule.onNodeWithTag(DOCX_LOADING_SPINNER_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Loading document").assertIsDisplayed()
        composeRule.onAllNodesWithText("Loading document").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Loading Word document").assertDoesNotExist()

        val screenshot = composeRule.onRoot().captureToImage().toPixelMap()
        assertEquals("Loading surface must be pure white", Color.White, screenshot[0, 0])
    }
}
