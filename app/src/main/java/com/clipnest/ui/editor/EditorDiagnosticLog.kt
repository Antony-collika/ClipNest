package com.clipnest.ui.editor

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Temporary in-memory diagnostic log to find why the editor caret/scroll jumps to the
 * end of the document when returning to this screen. Not persisted to disk; cleared
 * when the process dies. View it from Settings > Debug log while reproducing the bug,
 * then copy the text out.
 *
 * Safe to remove entirely once the underlying bug is found and fixed — this file has
 * no other dependents.
 */
object EditorDiagnosticLog {
    private const val MAX_LINES = 300
    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(tag: String, message: String) {
        val stamp = timeFormat.format(Date())
        val line = "$stamp [$tag] $message"
        if (lines.size >= MAX_LINES) lines.removeFirst()
        lines.addLast(line)
    }

    @Synchronized
    fun snapshot(): String = if (lines.isEmpty()) "(empty — reproduce the bug first, then reopen this screen)" else lines.joinToString("\n")

    @Synchronized
    fun clear() = lines.clear()
}
