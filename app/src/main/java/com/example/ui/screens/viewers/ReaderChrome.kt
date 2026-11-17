package com.example.ui.screens.viewers

/** Where the in-document viewer should scroll next (outline tap / go-to-line). */
data class ViewerScrollRequest(
    val lineNumber: Int,
    val headingIndex: Int? = null
)
