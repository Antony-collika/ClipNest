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

data class RelativeDateLabels(
    val today: String = "Hôm nay",
    val yesterday: String = "Hôm qua",
    val daysAgo: (Long) -> String = { "${it} ngày trước" },
    val lastWeek: String = "Tuần trước",
    val weeksAgo: (Long) -> String = { "${it} tuần trước" },
    val lastMonth: String = "Tháng trước",
    val monthYear: (Int, Int) -> String = { month, year -> "Tháng $month, $year" }
)

object RelativeTimeFormatter {

    fun format(timestampMillis: Long): String = format(timestampMillis, "Hôm nay", "Hôm qua")

    fun relativeDateLabel(
        timestampMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        labels: RelativeDateLabels = RelativeDateLabels()
    ): String {
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

        if (targetDate == today) return labels.today
        if (targetDate == today.minusDays(1)) return labels.yesterday

        val daysAgo = ChronoUnit.DAYS.between(targetDate, today)
        if (daysAgo > 0) {
            val firstDayOfWeek = WeekFields.ISO.firstDayOfWeek
            val todayWeek = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val targetWeek = targetDate.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            val weeksAgo = ChronoUnit.WEEKS.between(targetWeek, todayWeek)

            // Recency takes precedence over month boundaries.
            if (weeksAgo == 0L || daysAgo <= 3L) return labels.daysAgo(daysAgo)
            if (weeksAgo == 1L) return labels.lastWeek
            if (weeksAgo >= 2L) {
                val targetMonth = YearMonth.from(targetDate)
                val currentMonth = YearMonth.from(today)
                if (targetMonth == currentMonth) {
                    return labels.weeksAgo(weeksAgo)
                }
                if (targetMonth == currentMonth.minusMonths(1)) {
                    return labels.lastMonth
                }
                return labels.monthYear(targetDate.monthValue, targetDate.year)
            }
        }

        return labels.monthYear(targetDate.monthValue, targetDate.year)
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
