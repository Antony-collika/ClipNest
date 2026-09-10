package com.clipnest.service

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.clipnest.data.local.AppDatabase
import com.clipnest.data.local.SettingsDataStore
import com.clipnest.data.repository.CapturePayload
import com.clipnest.data.repository.CaptureSource
import com.clipnest.data.repository.ClipboardRepositoryImpl
import com.clipnest.ui.localization.withAppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransparentCaptureActivity : ComponentActivity() {

    private var captured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        setContentView(FrameLayout(this))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !captured) {
            captured = true
            captureClipboard()
        }
    }

    private fun captureClipboard() {
        val clipManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = try {
            clipManager.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?.trim()
        } catch (_: Exception) {
            null
        }

        if (clipText.isNullOrBlank()) {
            Toast.makeText(this, getString(com.clipnest.R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
            finishWithoutAnimation()
            return
        }

        val source = intent.getStringExtra(CaptureNotificationManager.EXTRA_CAPTURE_SOURCE)
            ?: CaptureSource.NOTIFICATION

        lifecycleScope.launch {
            val language = withContext(Dispatchers.IO) {
                SettingsDataStore(applicationContext).userSettingsFlow.first().language
            }
            val localizedContext = applicationContext.withAppLanguage(language)
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
                Toast.makeText(localizedContext, localizedContext.getString(com.clipnest.R.string.clipboard_saved), Toast.LENGTH_SHORT).show()
                if (CaptureSource.isNotification(source)) {
                    CaptureNotificationManager.showCaptureNotification(applicationContext, language)
                }
            } else {
                Toast.makeText(localizedContext, localizedContext.getString(com.clipnest.R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
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
