package com.example.app

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that "Open with → Files Claw" actually resolves for the MIME types the manifest
 * advertises. Deterministic package-level check — no UI automation involved.
 */
@RunWith(AndroidJUnit4::class)
class OpenWithIntentFilterTest {

    private val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private fun resolves(mimeType: String, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
        val matches = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
            .queryIntentActivities(intent, 0)
        return matches.any { it.activityInfo?.packageName == packageName }
    }

    @Test
    fun openWith_textPlain_resolves() =
        assertTrue(resolves("text/plain", Uri.parse("content://authority/document/1")))

    @Test
    fun openWith_markdown_resolves() =
        assertTrue(resolves("text/markdown", Uri.parse("content://authority/document/2")))

    @Test
    fun openWith_pdf_resolves() =
        assertTrue(resolves("application/pdf", Uri.parse("content://authority/document/3")))

    @Test
    fun openWith_wordprocessingml_resolves() =
        assertTrue(resolves(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            Uri.parse("content://authority/document/4")
        ))

    @Test
    fun openWith_image_resolves() =
        assertTrue(resolves("image/png", Uri.parse("content://authority/document/5")))

    @Test
    fun openWith_fileScheme_pdf_resolves() =
        assertTrue(resolves("application/pdf", Uri.parse("file:///sdcard/document.pdf")))
}
