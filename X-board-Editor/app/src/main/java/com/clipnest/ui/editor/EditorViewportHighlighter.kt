package com.clipnest.ui.editor

/**
 * Computes lightweight Markdown token ranges for only the requested viewport.
 * The scanner is intentionally pure so callers can run it on Dispatchers.Default
 * and apply the returned ranges on the editor thread.
 */
object EditorViewportHighlighter {
    data class HighlightRange(val start: Int, val end: Int, val kind: Kind)
    enum class Kind { HEADING, CODE, QUOTE, LIST, EMPHASIS, LINK }

    fun highlightViewport(document: CharSequence, viewportStart: Int, viewportEnd: Int): List<HighlightRange> {
        val start = viewportStart.coerceIn(0, document.length)
        val end = viewportEnd.coerceIn(start, document.length)
        if (start == end) return emptyList()
        val firstLine = lineStart(document, start)
        val scanEnd = lineEnd(document, end)
        return highlightSlice(document.subSequence(firstLine, scanEnd), firstLine)
    }

    /** Scans an already bounded viewport slice and restores absolute document offsets. */
    fun highlightSlice(slice: CharSequence, baseOffset: Int): List<HighlightRange> {
        if (slice.isEmpty()) return emptyList()
        require(baseOffset >= 0) { "baseOffset must be non-negative" }
        val ranges = ArrayList<HighlightRange>()
        var line = 0
        while (line < slice.length) {
            val next = lineEnd(slice, line)
            scanLine(slice, line, next, ranges)
            line = if (next < slice.length) next + 1 else slice.length
        }
        if (baseOffset == 0) return ranges
        return ranges.map { it.copy(start = it.start + baseOffset, end = it.end + baseOffset) }
    }

    private fun scanLine(document: CharSequence, start: Int, end: Int, out: MutableList<HighlightRange>) {
        var cursor = start
        while (cursor < end && (document[cursor] == ' ' || document[cursor] == '\t')) cursor++
        if (cursor < end && document[cursor] == '#') {
            var i = cursor
            while (i < end && document[i] == '#') i++
            if (i < end && document[i] == ' ') out += HighlightRange(cursor, end, Kind.HEADING)
        }
        if (cursor < end && document[cursor] == '>') out += HighlightRange(cursor, end, Kind.QUOTE)
        if (cursor + 1 < end && (document[cursor] == '-' || document[cursor] == '*' || document[cursor] == '+') && document[cursor + 1] == ' ') {
            out += HighlightRange(cursor, end, Kind.LIST)
        }
        if (cursor < end && document[cursor].isDigit()) {
            var i = cursor
            while (i < end && document[i].isDigit()) i++
            if (i + 1 < end && document[i] == '.' && document[i + 1] == ' ') out += HighlightRange(cursor, end, Kind.LIST)
        }
        if (start + 2 < end && document[start] == '`' && document[start + 1] == '`' && document[start + 2] == '`') out += HighlightRange(start, end, Kind.CODE)
        scanDelimited(document, start, end, "**", Kind.EMPHASIS, out)
        scanDelimited(document, start, end, "*", Kind.EMPHASIS, out, ignoreNestedDouble = true)
        scanDelimited(document, start, end, "[", Kind.LINK, out, closingPrefix = "](")
    }

    private fun scanDelimited(
        document: CharSequence,
        start: Int,
        end: Int,
        delimiter: String,
        kind: Kind,
        out: MutableList<HighlightRange>,
        closingPrefix: String? = null,
        ignoreNestedDouble: Boolean = false,
    ) {
        var cursor = start
        while (cursor <= end - delimiter.length) {
            val open = indexOf(document, delimiter, cursor, end, ignoreNestedDouble)
            if (open < 0) return
            val closeToken = closingPrefix ?: delimiter
            val close = indexOf(document, closeToken, open + delimiter.length, end, ignoreNestedDouble)
            if (close < 0) return
            out += HighlightRange(open, close + closeToken.length, kind)
            cursor = close + closeToken.length
        }
    }

    private fun indexOf(document: CharSequence, token: String, from: Int, end: Int, ignoreNestedDouble: Boolean = false): Int {
        if (token.isEmpty()) return from
        var i = from
        while (i + token.length <= end) {
            if (ignoreNestedDouble && token == "*" && ((i > 0 && document[i - 1] == '*') || (i + 1 < end && document[i + 1] == '*'))) {
                i++
                continue
            }
            var matched = true
            for (j in token.indices) if (document[i + j] != token[j]) { matched = false; break }
            if (matched) return i
            i++
        }
        return -1
    }

    private fun lineStart(document: CharSequence, offset: Int): Int {
        var i = offset - 1
        while (i >= 0 && document[i] != '\n') i--
        return i + 1
    }

    private fun lineEnd(document: CharSequence, offset: Int): Int {
        var i = offset
        while (i < document.length && document[i] != '\n') i++
        return i
    }
}
