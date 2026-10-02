package com.clipnest.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RelativeTimeFormatterTest {

    @Test
    fun format_today_returnsHomNay() {
        val now = System.currentTimeMillis()
        val result = RelativeTimeFormatter.format(now)
        assertTrue(result.contains("Hôm nay"))
    }

    @Test
    fun relativeDateLabel_usesMemoryAndCalendarBuckets() {
        fun calendar(year: Int, month: Int, day: Int): Calendar = Calendar.getInstance().apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        fun label(today: Calendar, year: Int, month: Int, day: Int): String =
            RelativeTimeFormatter.relativeDateLabel(
                calendar(year, month, day).timeInMillis,
                today.timeInMillis
            )

        // Same week: day labels are not limited by the 3-day memory window.
        val saturday = calendar(2026, 10, 10)
        assertEquals("Hôm nay", label(saturday, 2026, 10, 10))
        assertEquals("Hôm qua", label(saturday, 2026, 10, 9))
        assertEquals("2 ngày trước", label(saturday, 2026, 10, 8))
        assertEquals("3 ngày trước", label(saturday, 2026, 10, 7))
        assertEquals("5 ngày trước", label(saturday, 2026, 10, 5))

        // Cross-week: only the first 3 days remain in the day-memory window.
        val tuesday = calendar(2026, 10, 6)
        assertEquals("3 ngày trước", label(tuesday, 2026, 10, 3))
        assertEquals("Tuần trước", label(tuesday, 2026, 10, 2))

        // Older weeks in the same month use calendar-week distance.
        val laterTuesday = calendar(2026, 10, 20)
        assertEquals("2 tuần trước", label(laterTuesday, 2026, 10, 6))

        // Month boundaries do not override recent calendar-day/week memory.
        assertEquals("2 ngày trước", label(tuesday, 2026, 10, 4))
        assertEquals("Tháng trước", label(tuesday, 2026, 9, 30))

        // Previous-month dates still use week memory while they are in the previous calendar week.
        assertEquals("Tuần trước", label(tuesday, 2026, 9, 26))
        assertEquals("Tuần trước", label(tuesday, 2026, 9, 21))

        // Once the date falls into the week before last, the previous-month bucket wins.
        assertEquals("Tháng trước", label(tuesday, 2026, 9, 20))

        // Older months always use an explicit month/year label.
        // Older months always use an explicit month/year label.
        assertEquals("Tháng 8, 2026", label(tuesday, 2026, 8, 15))
        assertEquals("Tháng 9, 2025", label(tuesday, 2025, 9, 15))
    }

    @Test
    fun format_yesterday_returnsHomQua() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val result = RelativeTimeFormatter.format(cal.timeInMillis)
        assertTrue(result.contains("Hôm qua"))
    }
}
    @Test
    fun relativeDateLabel_usesLocalizedLabels() {
        val today = calendar(2026, 10, 6)
        val labels = RelativeDateLabels(
            today = "Today",
            yesterday = "Yesterday",
            daysAgo = { count -> "$count days ago" },
            lastWeek = "Last week",
            weeksAgo = { count -> "$count weeks ago" },
            lastMonth = "Last month",
            monthYear = { month, year -> "Month $month, $year" }
        )

        assertEquals(
            "Last week",
            RelativeTimeFormatter.relativeDateLabel(
                calendar(2026, 9, 26).timeInMillis,
                today.timeInMillis,
                labels
            )
        )
        assertEquals(
            "Last month",
            RelativeTimeFormatter.relativeDateLabel(
                calendar(2026, 9, 20).timeInMillis,
                today.timeInMillis,
                labels
            )
        )
    }

