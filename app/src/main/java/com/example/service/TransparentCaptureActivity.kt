package com.example.service

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.example.data.local.AppDatabase
import com.example.data.model.ClipboardCard
import com.example.domain.OrderHelper
import com.example.domain.TextNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class TransparentCaptureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        val clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = try {
            clipManager.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (e: Exception) {
            null
        }

        val normalized = TextNormalizer.normalize(clipText)

        if (!normalized.isNullOrBlank()) {
            val preview = TextNormalizer.generatePreview(normalized)
            val contentType = TextNormalizer.detectContentType(normalized)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(applicationContext)
                    val maxOrder = db.clipboardDao().getMaxSortOrder()
                    val now = System.currentTimeMillis()
                    val nextOrder = max(maxOrder + OrderHelper.ORDER_STEP, now)

                    val card = ClipboardCard(
                        id = now + (0..999).random(),
                        content = normalized,
                        preview = preview,
                        createdAtMillis = now,
                        sortOrder = nextOrder,
                        sourceApp = "Quick Notification",
                        contentType = contentType,
                        pinned = false,
                        isSensitive = false
                    )
                    db.clipboardDao().insertCard(card)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Đã lưu vào clipboard!", Toast.LENGTH_SHORT).show()
                        CaptureNotificationManager.showSavedSuccessNotification(applicationContext)
                        finish()
                        overridePendingTransition(0, 0)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        finish()
                        overridePendingTransition(0, 0)
                    }
                }
            }
        } else {
            Toast.makeText(this, "Clipboard hiện đang trống", Toast.LENGTH_SHORT).show()
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
