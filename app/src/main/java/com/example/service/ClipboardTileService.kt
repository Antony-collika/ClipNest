package com.example.service

import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.data.model.ClipboardCard
import com.example.domain.TextNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.N)
class ClipboardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Lưu Clipboard"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()

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
                    val card = ClipboardCard(
                        id = System.currentTimeMillis(),
                        content = normalized,
                        preview = preview,
                        createdAtMillis = System.currentTimeMillis(),
                        sortOrder = maxOrder + 1000L,
                        sourceApp = "Quick Settings Tile",
                        contentType = contentType,
                        pinned = false,
                        isSensitive = false
                    )
                    db.clipboardDao().insertCard(card)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Đã lưu nội dung clipboard vào kho!", Toast.LENGTH_SHORT).show()
                    }
                    CaptureNotificationManager.showSavedSuccessNotification(applicationContext)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            // Launch app to capture explicitly if clipboard is empty or restricted
            val intent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(CaptureNotificationManager.EXTRA_OPEN_CAPTURE, true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }
}
