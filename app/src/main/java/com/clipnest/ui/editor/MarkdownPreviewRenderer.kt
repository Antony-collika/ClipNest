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
}

private fun Color.toPreviewCssHex(): String = String.format(Locale.US, "#%06X", toArgb() and 0xFFFFFF)

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