package com.example.ui.preview.viewers

/** A request to move within text/Markdown preview content. */
data class ViewerScrollRequest(
    val lineNumber: Int,
    val headingIndex: Int? = null
)
