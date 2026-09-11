package com.clipnest.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.clipnest.data.local.AppLanguage
import com.clipnest.data.repository.CaptureSource
import com.clipnest.ui.localization.withAppLanguage

object CaptureNotificationManager {

    const val CHANNEL_ID = "clipboard_capture_channel"
    private const val NOTIFICATION_ID = 1001
    const val EXTRA_OPEN_CAPTURE = "extra_open_capture"
    const val EXTRA_START_TAB = "extra_start_tab"
    const val EXTRA_CAPTURE_SOURCE = "extra_capture_source"
    const val SOURCE_NOTIFICATION = CaptureSource.NOTIFICATION

    fun createNotificationChannel(context: Context, language: AppLanguage = AppLanguage.ENGLISH) {
        val localizedContext = context.withAppLanguage(language)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                localizedContext.getString(com.clipnest.R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = localizedContext.getString(com.clipnest.R.string.notification_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showCaptureNotification(context: Context, language: AppLanguage = AppLanguage.ENGLISH) {
        val localizedContext = context.withAppLanguage(language)
        createNotificationChannel(context, language)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val immutableFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val captureIntent = Intent(context, TransparentCaptureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(EXTRA_CAPTURE_SOURCE, SOURCE_NOTIFICATION)
        }
        val capturePendingIntent = PendingIntent.getActivity(
            context,
            104,
            captureIntent,
            immutableFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.clipnest.R.drawable.ic_content_copy_white_24dp)
            .setContentTitle(localizedContext.getString(com.clipnest.R.string.notification_capture_title))
            .setContentText(localizedContext.getString(com.clipnest.R.string.notification_capture_prompt))
            .setContentIntent(capturePendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun showSavedSuccessNotification(context: Context, language: AppLanguage = AppLanguage.ENGLISH) {
        val localizedContext = context.withAppLanguage(language)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.clipnest.R.drawable.ic_content_copy_white_24dp)
            .setContentTitle(localizedContext.getString(com.clipnest.R.string.notification_saved_title))
            .setContentText(localizedContext.getString(com.clipnest.R.string.notification_saved_body))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun dismissCaptureNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
