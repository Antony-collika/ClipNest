package com.example.domain

import com.example.data.model.ClipboardCard
import com.example.data.model.ContentType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportLabels(
    val title: String,
    val noItems: String,
    val exportedOn: String,
    val date: String,
    val source: String,
    val item: String,
    val captured: String,
    val pinned: String,
    val urlType: String,
    val combinedType: String,
    val sourceLabel: (String?) -> String? = { it }
)

object ExportFormatter {

    fun formatMarkdown(
        cards: List<ClipboardCard>,
        labels: ExportLabels = ExportLabels(
            title = "Clipboard Vault Export",
            noItems = "No items selected.",
            exportedOn = "Exported on: %1\u0024s (%2\u0024d items)",
            date = "Date: %1\u0024s (%2\u0024d items)",
            source = "Source",
            item = "Item",
            captured = "Captured",
            pinned = "Pinned",
            urlType = "URL",
            combinedType = "Combined"
        )
    ): String {
        if (cards.isEmpty()) return "# ${labels.title}\n\n${labels.noItems}\n"

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("# ${labels.title}\n\n")
        sb.append(String.format(Locale.getDefault(), labels.exportedOn, dateFormat.format(Date()), cards.size))
        sb.append("\n\n---\n\n")

        cards.forEachIndexed { index, card ->
            val num = index + 1
            val timestamp = dateFormat.format(Date(card.createdAtMillis))
            val badge = if (card.pinned) " [${labels.pinned}]" else ""
            val typeStr = when (card.contentType) {
                ContentType.URL -> " (${labels.urlType})"
                ContentType.COMBINED -> " (${labels.combinedType})"
                ContentType.TEXT -> ""
            }

            sb.append("### $num. ${labels.item}$badge$typeStr\n")
            sb.append("*${labels.captured}: $timestamp*")
            val sourceLabel = labels.sourceLabel(card.sourceApp)
            if (!sourceLabel.isNullOrBlank()) {
                sb.append(" • *${labels.source}: $sourceLabel*")
            }
            sb.append("\n\n")

            if (card.contentType == ContentType.URL) {
                sb.append("<${card.content.trim()}>\n\n")
            } else {
                sb.append("```\n")
                sb.append(card.content)
                sb.append("\n```\n\n")
            }
            sb.append("---\n\n")
        }

        return sb.toString().trimEnd() + "\n"
    }

    fun formatPlainText(
        cards: List<ClipboardCard>,
        labels: ExportLabels = ExportLabels(
            title = "Clipboard Vault Export",
            noItems = "No items selected.",
            exportedOn = "Exported on: %1\u0024s (%2\u0024d items)",
            date = "Date: %1\u0024s (%2\u0024d items)",
            source = "Source",
            item = "Item",
            captured = "Captured",
            pinned = "Pinned",
            urlType = "URL",
            combinedType = "Combined"
        )
    ): String {
        if (cards.isEmpty()) return "${labels.title}\n\n${labels.noItems}\n"

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.append(labels.title.uppercase(Locale.getDefault())).append("\n")
        sb.append(String.format(Locale.getDefault(), labels.date, dateFormat.format(Date()), cards.size))
        sb.append("\n========================================\n\n")

        cards.forEachIndexed { index, card ->
            val num = index + 1
            val timestamp = dateFormat.format(Date(card.createdAtMillis))
            val pinnedStr = if (card.pinned) " [${labels.pinned.uppercase(Locale.getDefault())}]" else ""

            sb.append("[$num] ${labels.captured}: $timestamp$pinnedStr\n")
            val sourceLabel = labels.sourceLabel(card.sourceApp)
            if (!sourceLabel.isNullOrBlank()) {
                sb.append("${labels.source}: $sourceLabel\n")
            }
            sb.append("----------------------------------------\n")
            sb.append(card.content)
            sb.append("\n\n")
        }

        return sb.toString().trimEnd() + "\n"
    }
}
