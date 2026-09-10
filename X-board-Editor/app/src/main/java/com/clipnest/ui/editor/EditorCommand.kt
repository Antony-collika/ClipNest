package com.clipnest.ui.editor

/**
 * User/editor intent independent from Android key or IME events.
 *
 * UI integrations map keyboard, IME and toolbar events into these commands
 * without teaching the document layer about Android input details.
 */
sealed interface EditorCommand {
    data object SelectAll : EditorCommand
    data object MoveToDocumentStart : EditorCommand
    data object MoveToDocumentEnd : EditorCommand
    data object MoveToLineStart : EditorCommand
    data object MoveToLineEnd : EditorCommand
    data object MoveLeft : EditorCommand
    data object MoveRight : EditorCommand
    data object MoveUp : EditorCommand
    data object MoveDown : EditorCommand
    data object DeleteBackward : EditorCommand
    data object DeleteForward : EditorCommand
    data object DeleteSelection : EditorCommand
    data object Undo : EditorCommand
    data object Redo : EditorCommand
    data class Insert(val text: String) : EditorCommand
    data class Replace(val start: Int, val end: Int, val text: String) : EditorCommand
}
