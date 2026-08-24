package com.example.service

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.data.local.AppDatabase
import com.example.data.repository.CapturePayload
import com.example.data.repository.ClipboardRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransparentCaptureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        val clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = try {
            clipManager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        } catch (_: Exception) {
            null
        }

        if (clipText.isNullOrBlank()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            finishWithoutAnimation()
            return
        }

        val source = intent.getStringExtra(CaptureNotificationManager.EXTRA_CAPTURE_SOURCE)
            ?: "Clipboard capture"

        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val repository = ClipboardRepositoryImpl(
                    AppDatabase.getInstance(applicationContext).clipboardDao()
                )
                repository.saveCards(
                    listOf(
                        CapturePayload(
                            content = clipText,
                            sourceApp = source
                        )
                    )
                ).firstOrNull()
            }

            if (saved != null) {
                Toast.makeText(applicationContext, "Clipboard saved", Toast.LENGTH_SHORT).show()
                if (source == CaptureNotificationManager.SOURCE_NOTIFICATION) {
                    CaptureNotificationManager.showCaptureNotification(applicationContext)
                }
            } else {
                Toast.makeText(applicationContext, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
            finishWithoutAnimation()
        }
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
