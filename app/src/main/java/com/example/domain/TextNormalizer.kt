package com.example.domain

import com.example.data.model.ContentType
import java.util.regex.Pattern

object TextNormalizer {

    private val URL_PATTERN = Pattern.compile(
        "^(https?://)?[a-zA-Z0-9][-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)$"
    )

    fun normalize(rawText: CharSequence?): String? {
        if (rawText == null) return null
        val asString = rawText.toString()
        val normalizedLineEndings = asString.replace("\r\n", "\n").replace("\r", "\n")
        if (normalizedLineEndings.trim().isEmpty()) {
            return null
        }
        return normalizedLineEndings
    }

    private fun normalizeForComposition(rawText: CharSequence?): String? =
        normalize(rawText)?.trim()?.takeIf { it.isNotEmpty() }

    fun generatePreview(text: String, maxLines: Int = 3, maxChars: Int = 180): String {
        val lines = text.lines()
        val takeLines = lines.take(maxLines).joinToString("\n")
        return if (takeLines.length > maxChars) {
            takeLines.substring(0, maxChars).trimEnd() + "…"
        } else if (lines.size > maxLines) {
            takeLines.trimEnd() + "…"
        } else {
            takeLines
        }
    }

    fun detectContentType(text: String): ContentType {
        val trimmed = text.trim()
        if (!trimmed.contains(" ") && !trimmed.contains("\n")) {
            if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                URL_PATTERN.matcher(trimmed).matches()
            ) {
                return ContentType.URL
            }
        }
        return ContentType.TEXT
    }

    /**
     * Combines the copied content and the shared payload without blank spacer lines.
     * The first argument is always placed first; the second is marked as its source.
     */
    fun combine(firstText: String, sourceText: String): String {
        val first = normalizeForComposition(firstText)
        val source = normalizeForComposition(sourceText)
        return when {
            first != null && source != null -> "$first\n---\nsource: $source"
            first != null -> first
            source != null -> source
            else -> ""
        }
    }

    /**
     * Formats cards in their already-visible order as Markdown sections.
     * Headers are opt-in so Share keeps its compact content format while Copy/Open editor can request Markdown sections.
     */
    fun formatSelectedCards(contents: List<String>, includeHeaders: Boolean = false): String {
        val normalized = contents.mapNotNull(::normalizeForComposition)
        if (normalized.isEmpty()) return ""
        return normalized.mapIndexed { index, content ->
            if (includeHeaders && normalized.size > 1) {
                "## Clipboard #${index + 1}\n\n$content"
            } else {
                content
            }
        }.joinToString("\n\n---\n\n")
    }
}
