package com.example.ui.preview.docx

import android.content.Context
import com.example.utils.FilePrintHelper
import java.io.File

/** Print DOCX in an isolated 100% print-mode WebView, never in the mobile fitted preview. */
object DocxPrintController {
    fun print(context: Context, file: File, title: String) {
        // The preview owns a phone-width CSS fit transform and native zoom state. Reusing it for
        // PrintManager leaked both into the A4 output; the dedicated renderer starts at 1x.
        FilePrintHelper.printDocx(context, file, title)
    }
}
