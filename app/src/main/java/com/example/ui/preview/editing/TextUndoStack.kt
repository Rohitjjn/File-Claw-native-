package com.example.ui.preview.editing

import androidx.compose.ui.text.input.TextFieldValue

class TextUndoStack(private val capacity: Int = 50) {
    private val buffer = ArrayDeque<TextFieldValue>()

    fun push(state: TextFieldValue) {
        if (buffer.size >= capacity) buffer.removeFirst()
        buffer.addLast(state)
    }

    fun pop(): TextFieldValue? =
        if (buffer.isEmpty()) null else buffer.removeLast()

    fun clear() = buffer.clear()

    val isEmpty get() = buffer.isEmpty()
    val isNotEmpty get() = buffer.isNotEmpty()
    val size get() = buffer.size

    fun lastOrNull(): TextFieldValue? = buffer.lastOrNull()

    /** Read-only newest-last copy of the stack for the history (undo-log) viewer. */
    fun snapshot(): List<TextFieldValue> = buffer.toList()
}
