package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.RemoteInput
import com.example.data.local.AppDatabase
import com.example.data.model.ClipboardCard
import com.example.domain.TextNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuickCaptureReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DIRECT_REPLY = "com.example.service.ACTION_DIRECT_REPLY"
        const val ACTION_RESET_NOTIFICATION = "com.example.service.ACTION_RESET_NOTIFICATION"
        const val KEY_TEXT_REPLY = "key_quick_clip_text"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == ACTION_DIRECT_REPLY) {
            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            val rawInput = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString()
            val normalized = TextNormalizer.normalize(rawInput)

            if (!normalized.isNullOrBlank()) {
                val preview = TextNormalizer.generatePreview(normalized)
                val contentType = TextNormalizer.detectContentType(normalized)

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(context)
                        val maxOrder = db.clipboardDao().getMaxSortOrder()
                        val newCard = ClipboardCard(
                            id = System.currentTimeMillis(),
                            content = normalized,
                            preview = preview,
                            createdAtMillis = System.currentTimeMillis(),
                            sortOrder = maxOrder + 1000L,
                            sourceApp = "Quick Notification",
                            contentType = contentType,
                            pinned = false,
                            isSensitive = false
                        )
                        db.clipboardDao().insertCard(newCard)

                        // Show success notification state
                        CaptureNotificationManager.showSavedSuccessNotification(context)

                        // Reset notification back to normal prompt after 3 seconds
                        Handler(Looper.getMainLooper()).postDelayed({
                            CaptureNotificationManager.showCaptureNotification(context)
                        }, 3000)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                CaptureNotificationManager.showCaptureNotification(context)
            }
        } else if (action == ACTION_RESET_NOTIFICATION) {
            CaptureNotificationManager.showCaptureNotification(context)
        }
    }
}
