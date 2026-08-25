package com.example.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.options.MutableDataSet
import java.util.Locale

/**
 * Converts the editor's Markdown into a self-contained, JavaScript-free HTML document.
 * Flexmark does the parsing; the CSS is intentionally small so the preview stays fast
 * and visually consistent with the Compose surface around it.
 */
object MarkdownPreviewRenderer {
    private val parser: Parser by lazy {
        Parser.builder().build()
    }

    private val renderer: HtmlRenderer by lazy {
        val options = MutableDataSet()
        options.set(HtmlRenderer.SUPPRESS_HTML, true)
        HtmlRenderer.builder(options).build()
    }

    fun render(markdown: String, colors: MarkdownPreviewColors): String {
        val body = renderer.render(parser.parse(markdown))
        return """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <style>
                :root { color-scheme: ${colors.colorScheme}; }
                * { box-sizing: border-box; }
                html, body { margin: 0; padding: 0; min-height: 100%; }
                body {
                  background: ${colors.background};
                  color: ${colors.onSurface};
                  font-family: sans-serif;
                  font-size: 16px;
                  line-height: 1.5;
                  padding: 16px;
                  overflow-wrap: anywhere;
                }
                h1, h2, h3, h4, h5, h6 {
                  color: ${colors.onSurface};
                  line-height: 1.25;
                  margin: 0.6em 0 0.45em;
                }
                h1, h2 { border-bottom: 1px solid ${colors.outlineVariant}; padding-bottom: 0.2em; }
                p { margin: 0.7em 0; }
                a { color: ${colors.primary}; }
                strong { color: ${colors.onSurface}; }
                blockquote {
                  border-inline-start: 4px solid ${colors.primary};
                  background: ${colors.surfaceVariant};
                  color: ${colors.onSurfaceVariant};
                  margin: 12px 0;
                  padding: 8px 12px;
                }
                ul, ol { padding-inline-start: 28px; margin: 0.7em 0; }
                li { margin: 0.25em 0; }
                hr { border: 0; border-top: 1px solid ${colors.outline}; margin: 18px 0; }
                code {
                  background: ${colors.surfaceVariant};
                  border-radius: 4px;
                  color: ${colors.onSurface};
                  font-family: monospace;
                  padding: 1px 4px;
                }
                pre {
                  background: ${colors.codeBackground};
                  border: 1px solid ${colors.outlineVariant};
                  border-radius: 8px;
                  color: ${colors.onSurface};
                  margin: 12px 0;
                  overflow-x: auto;
                  padding: 12px;
                  white-space: pre-wrap;
                }
                pre code { background: transparent; padding: 0; }
              </style>
            </head>
            <body>$body</body>
            </html>
        """.trimIndent()
    }

}

private fun Color.toPreviewCssHex(): String =
    String.format(Locale.US, "#%06X", toArgb() and 0xFFFFFF)

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
