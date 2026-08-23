package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.MainActivity

object CaptureNotificationManager {

    const val CHANNEL_ID = "clipboard_capture_channel"
    private const val NOTIFICATION_ID = 1001
    const val EXTRA_OPEN_CAPTURE = "extra_open_capture"
    const val EXTRA_START_TAB = "extra_start_tab"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clipboard capture",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Quick foreground entry point to capture clipboard content"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showCaptureNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to open Main App (Kho)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_START_TAB, "vault")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val openAppPendingIntent = PendingIntent.getActivity(context, 101, openAppIntent, flags)

        // RemoteInput for direct inline typing / pasting
        val remoteInput = RemoteInput.Builder(QuickCaptureReceiver.KEY_TEXT_REPLY)
            .setLabel("Dán nội dung clipboard vào đây...")
            .build()

        val replyIntent = Intent(context, QuickCaptureReceiver::class.java).apply {
            action = QuickCaptureReceiver.ACTION_DIRECT_REPLY
        }

        val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val replyPendingIntent = PendingIntent.getBroadcast(context, 102, replyIntent, replyFlags)

        // Action 1: Nhập / Dán (RemoteInput)
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit,
            "Nhập / Dán",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        // Action 2: Mở kho (Open App)
        val openAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_agenda,
            "Mở kho",
            openAppPendingIntent
        ).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Lưu nội dung vào Clipboard")
            .setContentText("Nhập hoặc dán nội dung bạn muốn lưu")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Nhập hoặc dán nội dung bạn muốn lưu")
                    .setSummaryText("Clipboard Manager")
            )
            .setContentIntent(openAppPendingIntent)
            .addAction(replyAction)
            .addAction(openAction)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun showSavedSuccessNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_START_TAB, "vault")
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val openAppPendingIntent = PendingIntent.getActivity(context, 103, openAppIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.checkbox_on_background)
            .setContentTitle("Đã lưu vào clipboard")
            .setContentText("Nội dung đã được lưu vào kho")
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun dismissCaptureNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
