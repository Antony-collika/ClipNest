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
    fun relativeDateLabel_usesCalendarBuckets() {
        val today = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 2, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        fun date(daysAgo: Int): Long = Calendar.getInstance().apply {
            timeInMillis = today.timeInMillis
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }.timeInMillis

        assertEquals("Hôm nay", RelativeTimeFormatter.relativeDateLabel(date(0), today.timeInMillis))
        assertEquals("Hôm qua", RelativeTimeFormatter.relativeDateLabel(date(1), today.timeInMillis))
        assertEquals("1 tuần trước", RelativeTimeFormatter.relativeDateLabel(date(7), today.timeInMillis))
        assertEquals("2 tuần trước", RelativeTimeFormatter.relativeDateLabel(date(10), today.timeInMillis))
        assertEquals("Tháng trước", RelativeTimeFormatter.relativeDateLabel(
            Calendar.getInstance().apply {
                timeInMillis = today.timeInMillis
                add(Calendar.MONTH, -1)
            }.timeInMillis, today.timeInMillis
        ))
        assertEquals("Tháng 8 - 2026", RelativeTimeFormatter.relativeDateLabel(
            Calendar.getInstance().apply {
                timeInMillis = today.timeInMillis
                add(Calendar.MONTH, -2)
            }.timeInMillis, today.timeInMillis
        ))
        assertEquals("Tháng 9 - 2025", RelativeTimeFormatter.relativeDateLabel(
            Calendar.getInstance().apply {
                set(2025, Calendar.SEPTEMBER, 15, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis, today.timeInMillis
        ))
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
