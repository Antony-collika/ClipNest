package com.clipnest.ui.editor

/**
 * Search over a live document view. It never requires a whole-document String
 * snapshot, so search work can be moved off the UI thread without copying the
 * document first.
 */
object EditorSearchEngine {
    fun findMatches(document: CharSequence, query: String): List<Int> {
        if (query.isEmpty() || query.length > document.length) return emptyList()

        val normalizedQuery = query.lowercase()
        val matches = ArrayList<Int>()
        var index = 0
        val lastStart = document.length - normalizedQuery.length
        while (index <= lastStart) {
            var matched = true
            for (queryIndex in normalizedQuery.indices) {
                if (document[index + queryIndex].lowercaseChar() != normalizedQuery[queryIndex]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                matches += index
                index += normalizedQuery.length.coerceAtLeast(1)
            } else {
                index++
            }
        }
        return matches
    }

    fun findMatches(document: EditorDocument, query: String): List<Int> =
        findMatches(document.charSequence, query)
}
