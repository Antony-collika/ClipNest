package com.clipnest.ui.editor

/** Keeps a selected search match inside the editor viewport without changing the editor's scrolling model. */
fun NativeEditorView.scrollSelectionIntoView(start: Int, end: Int = start) {
    val textLayout = layout ?: return
    val safeStart = start.coerceIn(0, length())
    val safeEnd = end.coerceIn(safeStart, length())
    val startLine = textLayout.getLineForOffset(safeStart)
    val endLine = textLayout.getLineForOffset(safeEnd)
    val selectionTop = textLayout.getLineTop(startLine)
    val selectionBottom = textLayout.getLineBottom(endLine)
    val viewportTop = scrollY + paddingTop
    val viewportBottom = scrollY + height - paddingBottom
    val maxScrollY = (computeVerticalScrollRange() - computeVerticalScrollExtent()).coerceAtLeast(0)
    val target = when {
        selectionTop < viewportTop -> selectionTop - paddingTop
        selectionBottom > viewportBottom -> selectionBottom - height + paddingBottom
        else -> return
    }
    scrollTo(scrollX, target.coerceIn(0, maxScrollY))
}
