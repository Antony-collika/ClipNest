package com.clipnest.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object RelativeTimeFormatter {

    fun format(timestampMillis: Long): String = format(timestampMillis, "Hôm nay", "Hôm qua")

    fun daysAgo(timestampMillis: Long): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val todayDate = java.time.LocalDate.of(
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        )
        val targetDate = java.time.LocalDate.of(
            target.get(Calendar.YEAR),
            target.get(Calendar.MONTH) + 1,
            target.get(Calendar.DAY_OF_MONTH)
        )
        return java.time.temporal.ChronoUnit.DAYS.between(targetDate, todayDate).coerceAtLeast(0L)
    }

    fun format(timestampMillis: Long, todayLabel: String, yesterdayLabel: String): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestampMillis }

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = timeFormat.format(Date(timestampMillis))

        val isSameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) {
            return "$timeStr • $todayLabel"
        }

        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val isYesterday = yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        if (isYesterday) {
            return "$yesterdayLabel • $timeStr"
        }

        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dateStr = dateFormat.format(Date(timestampMillis))
        return "$dateStr • $timeStr"
    }
}
