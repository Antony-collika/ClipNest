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
    jumpToHeadingIndex: Int?,
    onReady: () -> Unit
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                setBackgroundColor(previewSurfaceColor)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                isNestedScrollingEnabled = true
                overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
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
                    if (webView.tag == html) webView.scrollTo(0, previousScrollY)
                }
            }
            jumpToHeadingIndex?.let { index ->
                webView.post {
                    webView.evaluateJavascript("jumpToHeading('md-heading-$index')", null)
                }
            }
        },
        modifier = Modifier.fillMaxSize().testTag("markdown_preview_content")
    )
}