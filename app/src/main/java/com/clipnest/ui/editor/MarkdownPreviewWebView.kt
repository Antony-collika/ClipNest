package com.clipnest.ui.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

private class FastScrollWebView(context: android.content.Context) : WebView(context) {
    private var draggingFastScroll = false
    private val thumbWidthPx = dp(4)
    private val hitWidthPx = dp(24)
    private val minThumbHeightPx = dp(32)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rect = RectF()

    init {
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawFastScrollThumb(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                draggingFastScroll = isFastScrollHit(event.x, event.y)
                if (draggingFastScroll) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    scrollToFastScroll(event.y)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingFastScroll) {
                    scrollToFastScroll(event.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingFastScroll) {
                    draggingFastScroll = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun drawFastScrollThumb(canvas: Canvas) {
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        val maxScroll = (range - extent).coerceAtLeast(0)
        if (maxScroll <= 0 || height <= 0 || extent <= 0) return
        val thumbHeight = (height.toFloat() * extent / range).toInt().coerceAtLeast(minThumbHeightPx).coerceAtMost(height)
        val travel = (height - thumbHeight).coerceAtLeast(0)
        val top = if (travel == 0) 0f else travel.toFloat() * scrollY.coerceIn(0, maxScroll) / maxScroll
        paint.color = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) context.resources.getColor(android.R.color.darker_gray, context.theme) else context.resources.getColor(android.R.color.darker_gray)
        paint.alpha = if (draggingFastScroll) 210 else 125
        val left = width - thumbWidthPx.toFloat()
        rect.set(left, top, width.toFloat(), top + thumbHeight)
        canvas.drawRoundRect(rect, thumbWidthPx.toFloat(), thumbWidthPx.toFloat(), paint)
    }

    private fun isFastScrollHit(x: Float, y: Float): Boolean {
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        if (range <= extent || width <= 0 || height <= 0 || x < width - hitWidthPx) return false
        val maxScroll = (range - extent).coerceAtLeast(0)
        val thumbHeight = (height.toFloat() * extent / range).toInt().coerceAtLeast(minThumbHeightPx).coerceAtMost(height)
        val travel = (height - thumbHeight).coerceAtLeast(0)
        val top = if (travel == 0) 0f else travel.toFloat() * scrollY.coerceIn(0, maxScroll) / maxScroll
        return y >= top - hitWidthPx / 2f && y <= top + thumbHeight + hitWidthPx / 2f
    }

    private fun scrollToFastScroll(touchY: Float) {
        val range = computeVerticalScrollRange()
        val extent = computeVerticalScrollExtent()
        val maxScroll = (range - extent).coerceAtLeast(0)
        if (maxScroll <= 0) return
        val thumbHeight = (height.toFloat() * extent / range).toInt().coerceAtLeast(minThumbHeightPx).coerceAtMost(height)
        val travel = (height - thumbHeight).coerceAtLeast(0)
        val target = if (travel == 0) 0 else ((touchY - thumbHeight / 2f).coerceIn(0f, travel.toFloat()) * maxScroll / travel).toInt()
        scrollTo(scrollX, target)
        invalidate()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

@Composable
internal fun MarkdownPreviewWebView(
    html: String,
    previewSurfaceColor: Int,
    jumpToHeadingIndex: Int?,
    onReady: () -> Unit
) {
    AndroidView(
        factory = { context ->
            FastScrollWebView(context).apply {
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                setBackgroundColor(previewSurfaceColor)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                isNestedScrollingEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        onReady()
                        jumpToHeadingIndex?.let { index ->
                            view?.evaluateJavascript("jumpToHeading('md-heading-$index')", null)
                        }
                        view?.invalidate()
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
            } else {
                jumpToHeadingIndex?.let { index ->
                    webView.post {
                        webView.evaluateJavascript("jumpToHeading('md-heading-$index')", null)
                    }
                }
            }
            webView.invalidate()
        },
        modifier = Modifier.fillMaxSize().testTag("markdown_preview_content")
    )
}