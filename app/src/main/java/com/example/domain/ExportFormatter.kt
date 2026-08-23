package com.example.domain

import com.example.data.model.ClipboardCard
import com.example.data.model.ContentType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportFormatter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun formatMarkdown(cards: List<ClipboardCard>): String {
        if (cards.isEmpty()) return "# Clipboard Export\n\nNo items selected.\n"

        val sb = StringBuilder()
        sb.append("# Clipboard Vault Export\n\n")
        sb.append("*Exported on: ${dateFormat.format(Date())} (${cards.size} items)*\n\n")
        sb.append("---\n\n")

        cards.forEachIndexed { index, card ->
            val num = index + 1
            val timestamp = dateFormat.format(Date(card.createdAtMillis))
            val badge = if (card.pinned) " [Pinned]" else ""
            val typeStr = when (card.contentType) {
                ContentType.URL -> " (URL)"
                ContentType.COMBINED -> " (Combined)"
                ContentType.TEXT -> ""
            }

            sb.append("### $num. Item$badge$typeStr\n")
            sb.append("*Captured: $timestamp*")
            if (!card.sourceApp.isNullOrBlank()) {
                sb.append(" • *Source: ${card.sourceApp}*")
            }
            sb.append("\n\n")

            if (card.contentType == ContentType.URL) {
                val url = card.content.trim()
                sb.append("<$url>\n\n")
            } else {
                sb.append("```\n")
                sb.append(card.content)
                sb.append("\n```\n\n")
            }
            sb.append("---\n\n")
        }

        return sb.toString().trimEnd() + "\n"
    }

    fun formatPlainText(cards: List<ClipboardCard>): String {
        if (cards.isEmpty()) return "Clipboard Export\n\nNo items selected.\n"

        val sb = StringBuilder()
        sb.append("CLIPBOARD VAULT EXPORT\n")
        sb.append("Date: ${dateFormat.format(Date())} (${cards.size} items)\n")
        sb.append("========================================\n\n")

        cards.forEachIndexed { index, card ->
            val num = index + 1
            val timestamp = dateFormat.format(Date(card.createdAtMillis))
            val pinnedStr = if (card.pinned) " [PINNED]" else ""

            sb.append("[$num] Captured: $timestamp$pinnedStr\n")
            if (!card.sourceApp.isNullOrBlank()) {
                sb.append("Source: ${card.sourceApp}\n")
            }
            sb.append("----------------------------------------\n")
            sb.append(card.content)
            sb.append("\n\n")
        }

        return sb.toString().trimEnd() + "\n"
    }
}
