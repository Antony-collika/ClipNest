package com.clipnest.ui.editor

/**
 * Editor-owned document model. It deliberately has no Compose text-input state
 * dependency; the native editor owns the live IME buffer and this model is used
 * by commands, search and explicit snapshots.
 */
class EditorDocument(initialText: String = "") {
    private val buffer = StringBuilder(initialText)
    private var currentSelection = EditorSelection(initialText.length)

    val length: Int get() = buffer.length
    val selection: EditorSelection get() = currentSelection
    val charSequence: CharSequence get() = buffer

    fun text(): String = buffer.toString()
    fun snapshot(): String = buffer.toString()
    fun charAt(index: Int): Char = buffer[index]

    fun substring(start: Int, end: Int): String {
        val safeStart = start.coerceIn(0, length)
        val safeEnd = end.coerceIn(safeStart, length)
        return buffer.substring(safeStart, safeEnd)
    }

    fun selectedText(): String = substring(selection.min, selection.max)

    fun execute(command: EditorCommand) {
        when (command) {
            EditorCommand.SelectAll -> setSelection(0, length)
            EditorCommand.MoveToDocumentStart -> setSelection(0)
            EditorCommand.MoveToDocumentEnd -> setSelection(length)
            EditorCommand.MoveToLineStart -> setSelection(lineStart(selection.min))
            EditorCommand.MoveToLineEnd -> setSelection(lineEnd(selection.max))
            EditorCommand.MoveLeft -> {
                val current = selection
                setSelection(if (current.collapsed) (current.start - 1).coerceAtLeast(0) else current.min)
            }
            EditorCommand.MoveRight -> {
                val current = selection
                setSelection(if (current.collapsed) (current.end + 1).coerceAtMost(length) else current.max)
            }
            EditorCommand.MoveUp -> moveVertical(-1)
            EditorCommand.MoveDown -> moveVertical(1)
            EditorCommand.DeleteSelection -> {
                if (!selection.collapsed) applyReplace(selection.min, selection.max, "", selection.min)
            }
            EditorCommand.DeleteBackward -> {
                if (!selection.collapsed) {
                    applyReplace(selection.min, selection.max, "", selection.min)
                } else if (selection.start > 0) {
                    applyReplace(selection.start - 1, selection.start, "", selection.start - 1)
                }
            }
            EditorCommand.DeleteForward -> {
                if (!selection.collapsed) {
                    applyReplace(selection.min, selection.max, "", selection.min)
                } else if (selection.start < length) {
                    applyReplace(selection.start, selection.start + 1, "", selection.start)
                }
            }
            EditorCommand.Undo, EditorCommand.Redo -> Unit
            is EditorCommand.Insert -> {
                val current = selection
                applyReplace(current.min, current.max, command.text, current.min + command.text.length)
            }
            is EditorCommand.Replace -> applyReplace(command.start, command.end, command.text, null)
        }
    }

    fun replace(
        start: Int,
        end: Int,
        replacement: String,
        selectionStart: Int? = null,
        selectionEnd: Int? = null
    ) {
        applyReplace(
            start,
            end,
            replacement,
            selectionStart ?: selectionEnd ?: start + replacement.length,
            selectionEnd ?: selectionStart ?: start + replacement.length
        )
    }

    fun setSelection(start: Int, end: Int = start) {
        currentSelection = EditorSelection(
            start.coerceIn(0, length),
            end.coerceIn(0, length)
        )
    }

    private fun applyReplace(start: Int, end: Int, replacement: String, selectionStart: Int?, selectionEnd: Int? = selectionStart) {
        val safeStart = start.coerceIn(0, length)
        val safeEnd = end.coerceIn(safeStart, length)
        buffer.replace(safeStart, safeEnd, replacement)
        val newStart = (selectionStart ?: safeStart + replacement.length).coerceIn(0, length)
        val newEnd = (selectionEnd ?: newStart).coerceIn(newStart, length)
        currentSelection = EditorSelection(newStart, newEnd)
    }

    fun lineStart(offset: Int): Int {
        val safeOffset = offset.coerceIn(0, length)
        var index = safeOffset - 1
        while (index >= 0 && buffer[index] != '\n') index--
        return index + 1
    }

    fun lineEnd(offset: Int): Int {
        val safeOffset = offset.coerceIn(0, length)
        var index = safeOffset
        while (index < length && buffer[index] != '\n') index++
        return index
    }

    private fun moveVertical(direction: Int) {
        val current = selection
        val caret = if (current.collapsed) current.start else if (direction < 0) current.min else current.max
        val start = lineStart(caret)
        val column = caret - start
        val targetLineStart = if (direction < 0) {
            if (start == 0) return else lineStart(start - 1)
        } else {
            val currentEnd = lineEnd(caret)
            if (currentEnd >= length) return
            currentEnd + 1
        }
        setSelection((targetLineStart + column).coerceAtMost(lineEnd(targetLineStart)))
    }

    fun findMatches(query: String): List<Int> = EditorSearchEngine.findMatches(this, query)
}
