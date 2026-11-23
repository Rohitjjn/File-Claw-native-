package com.example.ui.preview.docx

import android.content.Context
import android.webkit.WebView
import com.example.utils.FilePrintHelper
import java.io.File

/** Keeps the existing Android PrintManager pipeline behind the DOCX preview boundary. */
object DocxPrintController {
    fun print(context: Context, file: File, title: String, preview: WebView?, previewReady: Boolean) {
        if (!file.isFile || file.length() <= 0L) {
            FilePrintHelper.printDocx(context, file, title)
            return
        }
        if (previewReady && preview != null) {
            FilePrintHelper.printWebView(context, preview, title)
        } else {
            FilePrintHelper.printDocx(context, file, title)
        }
    }
}
