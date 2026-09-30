package com.clipnest.domain

/**
 * Converts user search text into a safe FTS4 MATCH expression.
 *
 * Unquoted terms are prefix-matched ("hel" finds "hello") and combined with AND.
 * Quoted text is treated as a phrase; its last word is prefix-matched.
 * Uppercase OR and NOT are supported as FTS operators.
 *
 * IMPORTANT: in FTS4 the prefix star must be INSIDE the quotes ("hel*").
 * Writing it outside the quotes ("hel"*) makes FTS4 ignore the star and
 * only match the exact whole word.
 *
 * The AND between terms is implicit (a space), because an explicit AND is
 * only understood by FTS4 builds with the "enhanced query syntax" enabled.
 */
object FtsSearchQuery {

    fun fromUserQuery(query: String): String {
        val elements = parseElements(query)
        if (elements.isEmpty()) return ""

        val output = StringBuilder()
        var expectOperand = true

        for (element in elements) {
            when (element) {
                is Element.Operand -> {
                    if (!expectOperand && output.isNotEmpty()) output.append(" ")
                    output.append(element.value)
                    expectOperand = false
                }
                Element.Or -> {
                    if (!expectOperand && output.isNotEmpty()) {
                        output.append(" OR ")
                        expectOperand = true
                    }
                }
                Element.Not -> {
                    if (!expectOperand && output.isNotEmpty()) {
                        output.append(" NOT ")
                        expectOperand = true
                    }
                }
            }
        }

        return output.toString()
    }

    private fun parseElements(query: String): List<Element> {
        val result = mutableListOf<Element>()
        var index = 0

        while (index < query.length) {
            while (index < query.length && query[index].isWhitespace()) index++
            if (index >= query.length) break

            if (query[index] == '"') {
                index++
                val start = index
                while (index < query.length && query[index] != '"') index++
                val phrase = query.substring(start, index)
                if (index < query.length) index++

                // Phrase: all words in order, the last word may be unfinished.
                normalizePhrase(phrase)?.let { result += Element.Operand("\"$it*\"") }
                continue
            }

            val start = index
            while (index < query.length && !query[index].isWhitespace() && query[index] != '"') index++
            val raw = query.substring(start, index)

            when (raw) {
                "OR" -> result += Element.Or
                "NOT" -> result += Element.Not
                // Star goes inside the quotes so half-typed words are found.
                else -> normalizeUnquoted(raw).forEach { result += Element.Operand("\"$it*\"") }
            }
        }

        val valid = mutableListOf<Element>()
        var expectOperand = true
        for (element in result) {
            when (element) {
                is Element.Operand -> {
                    valid += element
                    expectOperand = false
                }
                Element.Or, Element.Not -> {
                    if (!expectOperand) {
                        valid += element
                        expectOperand = true
                    }
                }
            }
        }

        if (valid.lastOrNull() is Element.Or || valid.lastOrNull() is Element.Not) {
            valid.removeAt(valid.lastIndex)
        }

        return valid
    }

    private fun normalizeUnquoted(value: String): List<String> =
        SearchTextNormalizer.normalize(value)
            .split(Regex("[^\\p{L}\\p{N}_]+"))
            .filter { it.isNotEmpty() }
            .distinct()

    private fun normalizePhrase(value: String): String? =
        SearchTextNormalizer.normalize(value)
            .replace(Regex("[^\\p{L}\\p{N}_]+"), " ")
            .trim()
            .takeIf { it.isNotEmpty() }

    private sealed interface Element {
        data class Operand(val value: String) : Element
        data object Or : Element
        data object Not : Element
    }
}
