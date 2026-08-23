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

    fun combine(sharedText: String, clipboardText: String): String {
        val normShared = normalize(sharedText) ?: ""
        val normClip = normalize(clipboardText) ?: ""
        return when {
            normShared.isNotEmpty() && normClip.isNotEmpty() -> "$normShared\n\n---\n\n$normClip"
            normShared.isNotEmpty() -> normShared
            else -> normClip
        }
    }
}
