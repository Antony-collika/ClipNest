package com.clipnest.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.clipnest.data.local.SettingsDataStore
import com.clipnest.data.repository.CaptureSource
import com.clipnest.ui.localization.withAppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.N)
class ClipboardTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = getString(com.clipnest.R.string.quick_settings_copy_clipboard)
            updateTile()
        }
        serviceScope.launch {
            val language = withContext(Dispatchers.IO) {
                SettingsDataStore(applicationContext).userSettingsFlow.first().language
            }
            val localizedContext = applicationContext.withAppLanguage(language)
            qsTile?.apply {
                label = localizedContext.getString(com.clipnest.R.string.quick_settings_copy_clipboard)
                updateTile()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()

        val intent = Intent(this, TransparentCaptureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(CaptureNotificationManager.EXTRA_CAPTURE_SOURCE, SOURCE_TILE)
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

    private companion object {
        const val SOURCE_TILE = CaptureSource.QUICK_SETTINGS_TILE
    }
}
