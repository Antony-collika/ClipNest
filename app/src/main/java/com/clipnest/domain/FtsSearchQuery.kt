package com.clipnest.domain

import java.text.Normalizer

/**
 * Converts user search text into a safe FTS4 MATCH expression.
 *
 * Unquoted terms are prefix-matched and combined with AND.
 * Quoted text is treated as a phrase.
 * Uppercase OR and NOT are supported as FTS operators.
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
                    if (!expectOperand && output.isNotEmpty()) output.append(" AND ")
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

                normalizePhrase(phrase)?.let { result += Element.Operand("\"$it\"") }
                continue
            }

            val start = index
            while (index < query.length && !query[index].isWhitespace() && query[index] != '"') index++
            val raw = query.substring(start, index)

            when (raw) {
                "OR" -> result += Element.Or
                "NOT" -> result += Element.Not
                else -> normalizeUnquoted(raw).forEach { result += Element.Operand("\"$it\"*") }
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
        normalizeText(value)
            ?.split(Regex("[^\\p{L}\\p{N}_]+"))
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?: emptyList()

    private fun normalizePhrase(value: String): String? =
        normalizeText(value)
            ?.replace(Regex("[^\\p{L}\\p{N}_]+"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun normalizeText(value: String): String? {
        val normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase()
            .trim()
        return normalized.takeIf { it.isNotEmpty() }
    }

    private sealed interface Element {
        data class Operand(val value: String) : Element
        data object Or : Element
        data object Not : Element
    }
}
