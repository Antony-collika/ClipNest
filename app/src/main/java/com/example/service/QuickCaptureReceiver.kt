package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.RemoteInput
import com.example.data.local.AppDatabase
import com.example.data.repository.CapturePayload
import com.example.data.repository.ClipboardRepositoryImpl
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
        when (intent.action) {
            ACTION_DIRECT_REPLY -> {
                val rawInput = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_TEXT_REPLY)
                    ?.toString()
                val pendingResult = goAsync()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = ClipboardRepositoryImpl(
                            AppDatabase.getInstance(context.applicationContext).clipboardDao()
                        )
                        val saved = if (!rawInput.isNullOrBlank()) {
                            repository.saveCards(
                                listOf(
                                    CapturePayload(
                                        content = rawInput,
                                        sourceApp = "Notification RemoteInput"
                                    )
                                )
                            )
                        } else {
                            emptyList()
                        }

                        if (saved.isNotEmpty()) {
                            CaptureNotificationManager.showSavedSuccessNotification(context)
                            Handler(Looper.getMainLooper()).postDelayed({
                                CaptureNotificationManager.showCaptureNotification(context)
                            }, 3000)
                        } else {
                            CaptureNotificationManager.showCaptureNotification(context)
                        }
                    } catch (error: Exception) {
                        error.printStackTrace()
                        CaptureNotificationManager.showCaptureNotification(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_RESET_NOTIFICATION -> {
                CaptureNotificationManager.showCaptureNotification(context)
            }
        }
    }
}
