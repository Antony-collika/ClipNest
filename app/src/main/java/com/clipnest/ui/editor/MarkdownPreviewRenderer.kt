package com.clipnest.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import java.util.Locale

/**
 * Converts the editor's Markdown into a self-contained HTML document.
 * The generated preview also indexes Markdown headings so the built-in TOC can jump
 * directly to the corresponding section without needing an external navigation layer.
 */
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

    fun render(
        markdown: String,
        colors: MarkdownPreviewColors,
        viewerTextSizePx: Int = 16
    ): String {
        val renderedBody = renderer.render(parser.parse(markdown))
        var headingIndex = 0
        val tocItems = StringBuilder()
        val headingRegex = Regex("<h([1-6])>(.*?)</h\\1>", setOf(RegexOption.DOT_MATCHES_ALL))
        val body = headingRegex.replace(renderedBody) { match ->
            val level = match.groupValues[1].toInt()
            val title = match.groupValues[2]
            val id = "md-heading-${headingIndex++}"
            tocItems.append("<button class=\"toc-item toc-level-$level\" onclick=\"jumpToHeading('$id')\">$title</button>")
            "<h$level id=\"$id\">$title</h$level>"
        }
        val toc = if (tocItems.isNotEmpty()) tocItems.toString() else "<div class=\"toc-empty\">No headings</div>"

        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=5.0, user-scalable=yes" />
              <style>
                :root { color-scheme: ${colors.colorScheme}; }
                * { box-sizing: border-box; }
                html, body { margin: 0; padding: 0; min-height: 100%; scroll-behavior: smooth; }
                body {
                  background: ${colors.background};
                  color: ${colors.onSurface};
                  font-family: sans-serif;
                  font-size: ${viewerTextSizePx.coerceIn(10, 32)}px;
                  line-height: 1.5;
                  overflow-wrap: anywhere;
                  overflow-x: hidden;
                }
                h1, h2, h3, h4, h5, h6 { color: ${colors.onSurface}; line-height: 1.25; margin: 0.6em 0 0.45em; scroll-margin-top: 56px; }
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
                .preview-toc-bar { position: sticky; top: 0; z-index: 20; display: flex; justify-content: flex-end; height: 42px; padding: 4px 2px; background: ${colors.background}; border-bottom: 1px solid ${colors.outlineVariant}; }
                .toc-toggle { width: 38px; height: 34px; border: 0; border-radius: 8px; background: transparent; color: ${colors.primary}; font-size: 20px; cursor: pointer; }
                .toc-toggle:active { background: ${colors.surfaceVariant}; }
                .toc-panel { position: fixed; top: 46px; right: 8px; z-index: 30; width: min(82vw, 360px); max-height: 70vh; overflow-y: auto; padding: 8px; border: 1px solid ${colors.outlineVariant}; border-radius: 12px; background: ${colors.background}; box-shadow: 0 8px 28px rgba(0,0,0,.24); display: none; }
                .toc-panel.open { display: block; }
                .toc-title { padding: 6px 8px 8px; color: ${colors.onSurface}; font-weight: 700; font-size: .9em; }
                .toc-item { display: block; width: 100%; border: 0; border-radius: 7px; background: transparent; color: ${colors.onSurface}; padding: 7px 8px; text-align: left; font: inherit; cursor: pointer; }
                .toc-item:hover, .toc-item:active { background: ${colors.surfaceVariant}; }
                .toc-level-2 { padding-left: 20px; }
                .toc-level-3 { padding-left: 32px; }
                .toc-level-4 { padding-left: 44px; }
                .toc-level-5 { padding-left: 56px; }
                .toc-level-6 { padding-left: 68px; }
                .toc-empty { padding: 8px; color: ${colors.onSurfaceVariant}; }
              </style>
              <script>
                function toggleToc() { document.getElementById('toc-panel').classList.toggle('open'); }
                function jumpToHeading(id) {
                  var target = document.getElementById(id);
                  if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                  var panel = document.getElementById('toc-panel');
                  if (panel) panel.classList.remove('open');
                }
              </script>
            </head>
            <body>
              <div class="preview-toc-bar">
                <button class="toc-toggle" aria-label="Table of contents" title="Table of contents" onclick="toggleToc()">☰</button>
              </div>
              <div id="toc-panel" class="toc-panel">
                <div class="toc-title">Table of contents</div>
                $toc
              </div>
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
        fun from(
            background: Color,
            onSurface: Color,
            onSurfaceVariant: Color,
            surfaceVariant: Color,
            outline: Color,
            outlineVariant: Color,
            primary: Color,
            codeBackground: Color,
            isDark: Boolean
        ): MarkdownPreviewColors = MarkdownPreviewColors(
            background = background.toPreviewCssHex(),
            onSurface = onSurface.toPreviewCssHex(),
            onSurfaceVariant = onSurfaceVariant.toPreviewCssHex(),
            surfaceVariant = surfaceVariant.toPreviewCssHex(),
            outline = outline.toPreviewCssHex(),
            outlineVariant = outlineVariant.toPreviewCssHex(),
            primary = primary.toPreviewCssHex(),
            codeBackground = codeBackground.toPreviewCssHex(),
            colorScheme = if (isDark) "dark" else "light"
        )
    }
}
