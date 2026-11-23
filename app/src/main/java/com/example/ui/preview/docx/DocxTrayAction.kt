package com.example.ui.preview.docx

/** Stable DOCX-only reading tray order; keep the tray to these five actions. */
enum class DocxTrayAction(val label: String) {
    PAGE("Page"),
    ROTATE("Rotate"),
    SEARCH("Search"),
    PRINT("Print"),
    EDIT("Edit")
}
