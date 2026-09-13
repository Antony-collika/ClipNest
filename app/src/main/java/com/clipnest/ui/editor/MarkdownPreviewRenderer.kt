package com.clipnest.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import java.util.Locale

object MarkdownPreviewRenderer {
    private val options: MutableDataSet by lazy {
        MutableDataSet().apply {
            set(Parser.EXTENSIONS, listOf(TablesExtension.create()))
            set(TablesExtension.COLUMN_SPANS, false)
            set(TablesExtension.APPEND_MISSING_COLUMNS, true)
            set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
            set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
            set(HtmlRenderer.SUPPRESS_HTML, true)
        }
    }

    private val parser: Parser by lazy { Parser.builder(options).build() }
    private val renderer: HtmlRenderer by lazy { HtmlRenderer.builder(options).build() }

    fun render(markdown: String, colors: MarkdownPreviewColors, viewerTextSizePx: Int = 16): String {
        val renderedBody = renderer.render(parser.parse(markdown))
        var headingIndex = 0
        val headingRegex = Regex("<h([1-6])>(.*?)</h\\1>", setOf(RegexOption.DOT_MATCHES_ALL))
        val body = headingRegex.replace(renderedBody) { match ->
            val level = match.groupValues[1].toInt()
            val title = match.groupValues[2]
            val id = "md-heading-${headingIndex++}"
            "<h$level id=\"$id\">$title</h$level>"
        }

        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=5.0, user-scalable=yes" />
              <style>
                :root { color-scheme: ${colors.colorScheme}; }
                * { box-sizing: border-box; }
                html, body { margin: 0; padding: 0; min-height: 100%; scroll-behavior: smooth; }
                body { background: ${colors.background}; color: ${colors.onSurface}; font-family: sans-serif; font-size: ${viewerTextSizePx.coerceIn(10, 32)}px; line-height: 1.5; overflow-wrap: anywhere; overflow-x: hidden; }
                h1, h2, h3, h4, h5, h6 { color: ${colors.onSurface}; line-height: 1.25; margin: 0.6em 0 0.45em; scroll-margin-top: 12px; }
                h1, h2 { border-bottom: 1px solid ${colors.outlineVariant}; padding-bottom: 0.2em; }
                p { margin: 0.7em 0; }
                a { color: ${colors.primary}; }
                strong { color: ${colors.onSurface}; }
                blockquote { border-inline-start: 4px solid ${colors.primary}; background: ${colors.surfaceVariant}; color: ${colors.onSurfaceVariant}; margin: 12px 0; padding: 8px 12px; }
                ul, ol { padding-inline-start: 28px; margin: 0.7em 0; }
                li { margin: 0.25em 0; }
                hr { border: 0; border-top: 1px solid ${colors.outline}; margin: 18px 0; }
                code { background: ${colors.surfaceVariant}; border-radius: 4px; color: ${colors.onSurface}; font-family: monospace; padding: 1px 4px; }
                pre { background: ${colors.codeBackground}; border: 1px solid ${colors.outlineVariant}; border-radius: 8px; color: ${colors.onSurface}; margin: 12px 0; overflow-x: auto; padding: 12px; white-space: pre-wrap; }
                pre code { background: transparent; padding: 0; }
                table { width: 100%; max-width: 100%; table-layout: fixed; border-collapse: collapse; margin: 14px 0; overflow-wrap: anywhere; word-break: break-word; }
                thead { background: ${colors.surfaceVariant}; }
                th, td { border: 1px solid ${colors.outlineVariant}; padding: 8px 6px; vertical-align: top; text-align: left; white-space: normal; overflow-wrap: anywhere; word-break: break-word; min-width: 0; max-width: 0; }
                th { color: ${colors.onSurface}; font-weight: 700; }
                td { color: ${colors.onSurface}; }
              </style>
              <script>
                function jumpToHeading(id) {
                  var target = document.getElementById(id);
                  if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }
              </script>
            </head>
            <body>
              $body
            </body>
            </html>
        """.trimIndent()
    }

    fun extractHeadings(markdown: String): List<MarkdownHeading> {
        val headings = mutableListOf<MarkdownHeading>()
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
        var index = 0
        var inFence = false
        var line = 0
        while (line < lines.size) {
            val current = lines[line]
            val trimmed = current.trim()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence
                line++
                continue
            }
            if (!inFence) {
                val atx = Regex("^#{1,6}\\s+(.+?)\\s*#*\\s*$").find(trimmed)
                if (atx != null) {
                    val level = trimmed.takeWhile { it == '#' }.length
                    headings += MarkdownHeading(level, cleanHeadingTitle(atx.groupValues[1]), index++)
                    line++
                    continue
                }
                if (line + 1 < lines.size && trimmed.isNotEmpty()) {
                    val next = lines[line + 1].trim()
                    val level = when {
                        next.matches(Regex("^=+\\s*$")) -> 1
                        next.matches(Regex("^-+\\s*$")) -> 2
                        else -> 0
                    }
                    if (level != 0) {
                        headings += MarkdownHeading(level, cleanHeadingTitle(trimmed), index++)
                        line += 2
                        continue
                    }
                }
            }
            line++
        }
        return headings
    }

    private fun cleanHeadingTitle(title: String): String = title
        .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("[*_~`]"), "")
        .trim()
}

object CsvPreviewRenderer {

    /**
     * Parses CSV/TSV text into rows of cells, following RFC 4180 conventions:
     * - Cells may be wrapped in double quotes.
     * - A quoted cell may contain the delimiter, newlines, and escaped quotes ("").
     * - The delimiter is auto-detected between comma, semicolon, and tab by
     *   checking which occurs most often outside of quotes on the header line.
     */
    fun parse(text: String): List<List<String>> {
        if (text.isEmpty()) return emptyList()
        val delimiter = detectDelimiter(text)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        fun endCell() { row.add(cell.toString()); cell.clear() }
        fun endRow() { endCell(); rows.add(row); row = mutableListOf() }
        while (i < normalized.length) {
            val c = normalized[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < normalized.length && normalized[i + 1] == '"' -> { cell.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> cell.append(c)
                }
                c == '"' -> inQuotes = true
                c == delimiter -> endCell()
                c == '\n' -> endRow()
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows.filterNot { r -> r.size == 1 && r[0].isBlank() }
    }

    private fun detectDelimiter(text: String): Char {
        val headerLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        val counts = listOf(',', ';', '\t').associateWith { d -> headerLine.count { it == d } }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    fun render(csv: String, colors: MarkdownPreviewColors, viewerTextSizePx: Int = 16): String {
        val rows = parse(csv)
        val body = if (rows.isEmpty()) {
            "<p>Empty CSV file.</p>"
        } else {
            val columnCount = rows.maxOf { it.size }
            val header = rows.first()
            val dataRows = rows.drop(1)
            buildString {
                append("<table><thead><tr>")
                for (col in 0 until columnCount) {
                    append("<th>").append(escapeHtml(header.getOrElse(col) { "" })).append("</th>")
                }
                append("</tr></thead><tbody>")
                for (dataRow in dataRows) {
                    append("<tr>")
                    for (col in 0 until columnCount) {
                        append("<td>").append(escapeHtml(dataRow.getOrElse(col) { "" })).append("</td>")
                    }
                    append("</tr>")
                }
                append("</tbody></table>")
            }
        }

        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=5.0, user-scalable=yes" />
              <style>
                :root { color-scheme: ${colors.colorScheme}; }
                * { box-sizing: border-box; }
                html, body { margin: 0; padding: 0; min-height: 100%; scroll-behavior: smooth; }
                body { background: ${colors.background}; color: ${colors.onSurface}; font-family: sans-serif; font-size: ${viewerTextSizePx.coerceIn(10, 32)}px; line-height: 1.5; overflow-wrap: anywhere; overflow-x: auto; padding: 8px; }
                table { border-collapse: collapse; margin: 4px 0; }
                thead { background: ${colors.surfaceVariant}; position: sticky; top: 0; }
                th, td { border: 1px solid ${colors.outlineVariant}; padding: 8px 10px; vertical-align: top; text-align: left; white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
                th { color: ${colors.onSurface}; font-weight: 700; }
                td { color: ${colors.onSurface}; }
                tbody tr:nth-child(even) { background: ${colors.surfaceVariant}; }
              </style>
            </head>
            <body>
              $body
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

private fun Color.toPreviewCssHex(): String = String.format(Locale.US, "#%06X", toArgb() and 0xFFFFFF)

data class MarkdownHeading(val level: Int, val title: String, val index: Int)

data class MarkdownPreviewColors(
    val background: String,
    val onSurface: String,
    val onSurfaceVariant: String,
    val surfaceVariant: String,
    val outline: String,
    val outlineVariant: String,
    val primary: String,
    val codeBackground: String,
    val colorScheme: String
) {
    companion object {
        fun from(background: Color, onSurface: Color, onSurfaceVariant: Color, surfaceVariant: Color, outline: Color, outlineVariant: Color, primary: Color, codeBackground: Color, isDark: Boolean) = MarkdownPreviewColors(
            background.toPreviewCssHex(), onSurface.toPreviewCssHex(), onSurfaceVariant.toPreviewCssHex(), surfaceVariant.toPreviewCssHex(), outline.toPreviewCssHex(), outlineVariant.toPreviewCssHex(), primary.toPreviewCssHex(), codeBackground.toPreviewCssHex(), if (isDark) "dark" else "light"
        )
    }
}