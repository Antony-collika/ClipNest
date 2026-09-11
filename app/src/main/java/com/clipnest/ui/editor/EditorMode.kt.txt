package com.clipnest.ui.editor

/**
 * Controls which optional chrome [EditorScreen] renders around the shared
 * native text surface. The editor itself (document, commands, undo/redo,
 * search) is identical in every mode; only the header/toolbar differ.
 *
 * [PLAIN] is the existing full-document editor: no breadcrumb, no title
 * field, "Editor" stays as the screen title. This is the default and is
 * what every current call site keeps using unchanged.
 *
 * [NOTE] is the not-yet-built note-taking surface: adds a breadcrumb row
 * above the toolbar, a title field above the content, and switches the
 * screen title to "Edit note". Nothing in this branch wires [NOTE] up yet;
 * it exists so the note feature can be plugged in later without touching
 * [EditorScreen]'s internals again.
 */
enum class EditorMode {
    PLAIN,
    NOTE
}

/**
 * Where a note was created from, used to render the breadcrumb label and to
 * decide where the back action should return to. `null` means the note has
 * no particular origin ("Taking note"); a non-null value renders as
 * "<origin>/New Note" and lets the back action return to that origin instead
 * of a generic note list.
 *
 * Deliberately just a label + an opaque return key for now — there is no
 * note database yet to model this more richly against.
 */
data class EditorNoteOrigin(
    val label: String,
    val returnKey: String? = null
)
