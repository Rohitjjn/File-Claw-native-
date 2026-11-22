package com.example.ui.preview.docx

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.RecentFileEntity
import com.example.ui.theme.FilesClawTheme
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocxFallbackInstrumentationTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun corruptDocxRecoveryIsOnlyShownAfterTheUserRequestsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "corrupt-recoverable-${System.nanoTime()}.docx")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write("<w:document xmlns:w=\"urn:test\"><w:body><w:p><w:r><w:t>RECOVERABLE DOCX TEXT</w:t></w:r></w:p></w:body></w:document>".toByteArray())
            zip.closeEntry()
        }
        val entity = RecentFileEntity(
            path = file.absolutePath,
            name = file.name,
            size = file.length(),
            extension = "docx",
            lastOpened = System.currentTimeMillis()
        )
        composeRule.setContent {
            FilesClawTheme {
                DocxPreviewWebView(
                    fileEntity = entity,
                    viewerState = rememberDocxViewerState(file.absolutePath),
                    isNightMode = false
                )
            }
        }

        composeRule.waitUntil(30_000) {
            composeRule.onAllNodesWithText("Show recovered text").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("RECOVERABLE DOCX TEXT").assertDoesNotExist()
        composeRule.onNodeWithText("Show recovered text").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("RECOVERABLE DOCX TEXT").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(file.delete() || !file.exists())
    }
}
