package com.clipnest.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object RelativeTimeFormatter {

    fun format(timestampMillis: Long): String = format(timestampMillis, "Hôm nay", "Hôm qua")

    fun relativeDateLabel(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply { timeInMillis = timestampMillis }

        val today = java.time.LocalDate.of(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        val targetDate = java.time.LocalDate.of(target.get(Calendar.YEAR), target.get(Calendar.MONTH) + 1, target.get(Calendar.DAY_OF_MONTH))

        if (targetDate == today) return "Hôm nay"
        if (targetDate == today.minusDays(1)) return "Hôm qua"

        val weekFields = java.time.temporal.WeekFields.ISO
        val firstDayOfWeek = weekFields.firstDayOfWeek
        val todayWeek = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val targetWeek = targetDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(firstDayOfWeek))
        val weeksAgo = java.time.temporal.ChronoUnit.WEEKS.between(targetWeek, todayWeek)

        if (weeksAgo == 1L) return "Tuần trước"
        if (targetDate.year == today.year && targetDate.month == today.month && weeksAgo >= 2) {
            return "${weeksAgo} tuần trước"
        }

        val monthsAgo = java.time.temporal.ChronoUnit.MONTHS.between(targetDate.withDayOfMonth(1), today.withDayOfMonth(1))
        if (monthsAgo == 1L) return "Tháng trước"
        if (targetDate.year == today.year && monthsAgo >= 2) return "${monthsAgo} tháng trước"

        return "Tháng ${targetDate.monthValue} - ${targetDate.year}"
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
