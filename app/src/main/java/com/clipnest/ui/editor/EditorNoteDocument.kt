package com.clipnest.ui.editor

/**
 * Logical note document hosted by one native Editable.
 *
 * The Editable contains one structural newline between title and content so the
 * two regions can share one IME/input connection. The newline is an implementation
 * boundary, not note data: callers always persist [title] and [content] separately.
 */
data class EditorNoteDocument(
    val title: String,
    val content: String
) {
    val editableText: String
        get() = title + "\n" + content

    val titleBoundary: Int
        get() = title.length

    companion object {
        fun normalize(value: String): String =
            value.replace("\r\n", "\n").replace('\r', '\n')

        fun fromEditable(text: CharSequence?, titleBoundary: Int): EditorNoteDocument {
            val value = normalize(text?.toString().orEmpty())
            val boundary = titleBoundary.coerceIn(0, value.length)
            val contentStart = if (boundary < value.length && value[boundary] == '\n') boundary + 1 else boundary
            return EditorNoteDocument(
                title = value.substring(0, boundary),
                content = value.substring(contentStart)
            )
        }
    }
}
