package com.clipnest.ui.editor

/** Command boundary between editor UI/input adapters and the document/native surface. */
class EditorController(private val document: EditorDocument) {
    private var nativeEditor: NativeEditorView? = null

    val length: Int get() = nativeEditor?.length() ?: document.length
    val selection: EditorSelection get() = nativeEditor?.let { EditorSelection(it.selectionStart, it.selectionEnd) } ?: document.selection
    val charSequence: CharSequence get() = nativeEditor?.text ?: document.charSequence

    fun bindNativeEditor(editor: NativeEditorView?) { nativeEditor = editor }

    /** Groups multiple native edits into one change/undo boundary. */
    fun <T> transaction(block: EditorController.() -> T): T {
        val editor = nativeEditor
        if (editor == null) return block()
        return editor.transaction { this@EditorController.block() }
    }

    fun execute(command: EditorCommand) {
        val editor = nativeEditor
        if (editor == null) { document.execute(command); return }
        when (command) {
            EditorCommand.SelectAll -> editor.selectAll()
            EditorCommand.MoveToDocumentStart -> editor.setSelection(0)
            EditorCommand.MoveToDocumentEnd -> editor.setSelection(editor.length())
            EditorCommand.MoveToLineStart -> editor.setSelection(lineStart(editor.text ?: "", editor.selectionStart))
            EditorCommand.MoveToLineEnd -> editor.setSelection(lineEnd(editor.text ?: "", editor.selectionEnd))
            EditorCommand.MoveLeft -> moveHorizontal(editor, -1)
            EditorCommand.MoveRight -> moveHorizontal(editor, 1)
            EditorCommand.MoveUp -> moveVertical(editor, -1)
            EditorCommand.MoveDown -> moveVertical(editor, 1)
            EditorCommand.DeleteBackward -> delete(editor, backward = true)
            EditorCommand.DeleteForward -> delete(editor, backward = false)
            EditorCommand.DeleteSelection -> if (editor.selectionStart != editor.selectionEnd) editor.replaceText(editor.selectionStart, editor.selectionEnd, "")
            EditorCommand.Undo -> editor.undo()
            EditorCommand.Redo -> editor.redo()
            is EditorCommand.Insert -> replaceSelection(command.text)
            is EditorCommand.Replace -> editor.replaceText(command.start, command.end, command.text)
        }
    }

    fun selectAll() = execute(EditorCommand.SelectAll)
    fun moveToDocumentStart() = execute(EditorCommand.MoveToDocumentStart)
    fun moveToDocumentEnd() = execute(EditorCommand.MoveToDocumentEnd)
    fun moveToLineStart() = execute(EditorCommand.MoveToLineStart)
    fun moveToLineEnd() = execute(EditorCommand.MoveToLineEnd)
    fun moveLeft() = execute(EditorCommand.MoveLeft)
    fun moveRight() = execute(EditorCommand.MoveRight)
    fun moveUp() = execute(EditorCommand.MoveUp)
    fun moveDown() = execute(EditorCommand.MoveDown)
    fun deleteBackward() = execute(EditorCommand.DeleteBackward)
    fun deleteForward() = execute(EditorCommand.DeleteForward)
    fun deleteSelection() = execute(EditorCommand.DeleteSelection)
    fun insert(text: String) = execute(EditorCommand.Insert(text))
    fun replace(start: Int, end: Int, text: String) = execute(EditorCommand.Replace(start, end, text))
    fun undo() = execute(EditorCommand.Undo)
    fun redo() = execute(EditorCommand.Redo)

    fun replaceSelection(text: String) {
        val current = selection
        val editor = nativeEditor
        if (editor != null) editor.replaceText(current.min, current.max, text, current.min + text.length)
        else { replace(current.min, current.max, text); setSelection(current.min + text.length) }
    }

    fun setSelection(start: Int, end: Int = start) { nativeEditor?.setSelection(start.coerceIn(0, length), end.coerceIn(0, length)) ?: document.setSelection(start, end) }
    fun selectedText(): String = nativeEditor?.let { it.text?.subSequence(it.selectionStart, it.selectionEnd).toString() } ?: document.selectedText()
    fun substring(start: Int, end: Int): String = charSequence.subSequence(start.coerceIn(0, length), end.coerceIn(start, length)).toString()
    fun findMatches(query: String): List<Int> = EditorSearchEngine.findMatches(charSequence, query)
    fun snapshot(): String = charSequence.toString()
    fun snapshot(revision: Long): EditorSnapshot = EditorSnapshot(revision, snapshot())

    private fun moveHorizontal(editor: NativeEditorView, direction: Int) {
        val start = editor.selectionStart; val end = editor.selectionEnd
        if (start != end) editor.setSelection(if (direction < 0) minOf(start, end) else maxOf(start, end))
        else editor.setSelection((start + direction).coerceIn(0, editor.length()))
    }

    private fun moveVertical(editor: NativeEditorView, direction: Int) {
        val text = editor.text ?: return
        val caret = if (editor.selectionStart == editor.selectionEnd) editor.selectionStart else if (direction < 0) minOf(editor.selectionStart, editor.selectionEnd) else maxOf(editor.selectionStart, editor.selectionEnd)
        val start = lineStart(text, caret); val column = caret - start
        val targetStart = if (direction < 0) { if (start == 0) return; lineStart(text, start - 1) } else { val end = lineEnd(text, caret); if (end >= text.length) return; end + 1 }
        editor.setSelection((targetStart + column).coerceAtMost(lineEnd(text, targetStart)))
    }

    private fun delete(editor: NativeEditorView, backward: Boolean) {
        if (editor.selectionStart != editor.selectionEnd) { editor.replaceText(editor.selectionStart, editor.selectionEnd, ""); return }
        val caret = editor.selectionStart
        if (backward && caret > 0) editor.replaceText(caret - 1, caret, "")
        else if (!backward && caret < editor.length()) editor.replaceText(caret, caret + 1, "")
    }

    private fun lineStart(text: CharSequence, offset: Int): Int { var i = offset.coerceIn(0, text.length) - 1; while (i >= 0 && text[i] != '\n') i--; return i + 1 }
    private fun lineEnd(text: CharSequence, offset: Int): Int { var i = offset.coerceIn(0, text.length); while (i < text.length && text[i] != '\n') i++; return i }
}
