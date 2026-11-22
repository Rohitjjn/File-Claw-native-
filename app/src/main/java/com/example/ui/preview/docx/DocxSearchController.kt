package com.example.ui.preview.docx

/** Keeps query changes separate from next/previous deltas so navigation does not reset match zero. */
object DocxSearchController {
    data class Cursor(val query: String? = null, val next: Int = 0, val previous: Int = 0)
    data class Plan(val commands: List<Command>, val cursor: Cursor)
    sealed class Command {
        data class Search(val query: String) : Command()
        data object Next : Command()
        data object Previous : Command()
        data object Clear : Command()
    }

    fun plan(cursor: Cursor, query: String, nextTrigger: Int, previousTrigger: Int): Plan {
        val normalized = query.trim()
        val commands = mutableListOf<Command>()
        when {
            normalized.isEmpty() -> if (!cursor.query.isNullOrEmpty()) commands += Command.Clear
            normalized != cursor.query -> commands += Command.Search(normalized)
        }
        val nextDelta = (nextTrigger - cursor.next).coerceAtLeast(0).coerceAtMost(100)
        val previousDelta = (previousTrigger - cursor.previous).coerceAtLeast(0).coerceAtMost(100)
        repeat(nextDelta) { commands += Command.Next }
        repeat(previousDelta) { commands += Command.Previous }
        return Plan(commands, Cursor(normalized.takeIf { it.isNotEmpty() }, nextTrigger, previousTrigger))
    }
}
