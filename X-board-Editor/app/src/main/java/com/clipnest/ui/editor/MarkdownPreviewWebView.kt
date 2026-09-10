package com.clipnest.ui.editor

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun MarkdownPreviewWebView(
    html: String,
    previewSurfaceColor: Int,
    onReady: () -> Unit
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                setBackgroundColor(previewSurfaceColor)
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                isNestedScrollingEnabled = true
                overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Do not gate readiness on postVisualStateCallback: on a cold WebView
                        // that callback can arrive late or not at all, leaving the loading layer
                        // permanently over an otherwise rendered document.
                        onReady()
                    }
                }
            }
        },
        update = { webView ->
            webView.setBackgroundColor(previewSurfaceColor)
            if (webView.tag != html) {
                val previousScrollY = webView.scrollY
                webView.tag = html
                webView.loadDataWithBaseURL("https://x-board.local/", html, "text/html", "UTF-8", null)
                webView.post {
                    if (webView.tag == html) {
                        webView.scrollTo(0, previousScrollY)
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize().testTag("markdown_preview_content")
    )
}
