package com.example.domain

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
    fun format_yesterday_returnsHomQua() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val result = RelativeTimeFormatter.format(cal.timeInMillis)
        assertTrue(result.contains("Hôm qua"))
    }
}
