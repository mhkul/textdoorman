package com.textdoorman.android

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TimeFormatTest {
    private fun at(day: Int, hour: Int, min: Int = 0) =
        Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, day, hour, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test
    fun daysBetweenUsesCalendarDays() {
        assertEquals(0, TimeFormat.daysBetween(at(8, 0, 5), at(8, 23, 59)))
        assertEquals(1, TimeFormat.daysBetween(at(7, 23, 59), at(8, 0, 1)))   // 2 minutes, but "yesterday"
        assertEquals(1, TimeFormat.daysBetween(at(7, 17, 52), at(8, 13, 16)))  // 19 hr apart
        assertEquals(2, TimeFormat.daysBetween(at(6, 16, 28), at(8, 13, 16)))  // 45 hr apart: "2 days" misleads
        assertEquals(7, TimeFormat.daysBetween(at(1, 12), at(8, 12)))
    }
}
