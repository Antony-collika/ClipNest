package com.clipnest.data.repository

/** Stable identifiers stored for capture origins; display labels are localized separately. */
object CaptureSource {
    const val NOTIFICATION = "source_notification"
    const val QUICK_SETTINGS_TILE = "source_quick_settings_tile"
    const val SYSTEM_CLIPBOARD = "source_system_clipboard"
    const val ANDROID_SHARE = "source_android_share"
    const val COMBINED = "source_combined"
    const val MANUAL_CLIPBOARD_BUTTON = "source_manual_clipboard_button"
    const val MANUAL_ENTRY = "source_manual_entry"

    fun isNotification(value: String?): Boolean = value == NOTIFICATION || value == "Notification"
}

/** Maps both new stable identifiers and legacy stored labels to the current app language. */
fun localizedCaptureSourceLabel(context: android.content.Context, value: String?): String? {
    val source = value?.trim().orEmpty()
    if (source.isEmpty()) return null
    val resourceId = when (source) {
        CaptureSource.NOTIFICATION, "Notification" -> com.clipnest.R.string.source_notification
        CaptureSource.QUICK_SETTINGS_TILE, "Quick Settings Tile" -> com.clipnest.R.string.source_quick_settings_tile
        CaptureSource.SYSTEM_CLIPBOARD, "System Clipboard" -> com.clipnest.R.string.source_system_clipboard
        CaptureSource.ANDROID_SHARE, "Android Share" -> com.clipnest.R.string.source_android_share
        CaptureSource.COMBINED, "System Clipboard + Android Share" -> com.clipnest.R.string.source_combined
        CaptureSource.MANUAL_CLIPBOARD_BUTTON, "Manual Clipboard Button" -> com.clipnest.R.string.manual_clipboard_button_source
        CaptureSource.MANUAL_ENTRY, "Manual Entry" -> com.clipnest.R.string.manual_entry_source
        else -> null
    }
    return resourceId?.let(context::getString) ?: source
}
