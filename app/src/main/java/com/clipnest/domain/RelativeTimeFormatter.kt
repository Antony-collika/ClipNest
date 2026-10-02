package com.clipnest.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

object RelativeTimeFormatter {

    fun format(timestampMillis: Long): String = format(timestampMillis, "Hôm nay", "Hôm qua")

    fun relativeDateLabel(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply { timeInMillis = timestampMillis }

        val today = LocalDate.of(
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        )
        val targetDate = LocalDate.of(
            target.get(Calendar.YEAR),
            target.get(Calendar.MONTH) + 1,
            target.get(Calendar.DAY_OF_MONTH)
        )

        if (targetDate == today) return "Hôm nay"
        if (targetDate == today.minusDays(1)) return "Hôm qua"

        val daysAgo = ChronoUnit.DAYS.between(targetDate, today)
        if (daysAgo > 0) {
            val firstDayOfWeek = WeekFields.ISO.firstDayOfWeek
            val todayWeek = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val targetWeek = targetDate.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val weeksAgo = ChronoUnit.WEEKS.between(targetWeek, todayWeek)

            // Month boundaries take precedence over week labels.
            if (YearMonth.from(targetDate) == YearMonth.from(today)) {
                if (weeksAgo == 0L || daysAgo <= 3L) return "${daysAgo} ngày trước"
                if (weeksAgo == 1L) return "Tuần trước"
                if (weeksAgo >= 2L) return "${weeksAgo} tuần trước"
            } else if (YearMonth.from(targetDate) == YearMonth.from(today).minusMonths(1)) {
                return "Tháng trước"
            }
        }

        return "Tháng ${targetDate.monthValue}, ${targetDate.year}"
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
