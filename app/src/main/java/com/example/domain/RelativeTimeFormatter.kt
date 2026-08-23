package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object RelativeTimeFormatter {

    fun format(timestampMillis: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestampMillis }

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = timeFormat.format(Date(timestampMillis))

        val isSameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) {
            return "$timeStr • Hôm nay"
        }

        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val isYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) {
            return "Hôm qua • $timeStr"
        }

        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dateStr = dateFormat.format(Date(timestampMillis))
        return "$dateStr • $timeStr"
    }
}
