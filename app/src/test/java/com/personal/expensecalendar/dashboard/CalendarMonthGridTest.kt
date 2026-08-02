package com.personal.expensecalendar.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarMonthGridTest {
    @Test
    fun `august 2026 uses six rows and starts on previous sunday`() {
        val days = CalendarMonthGrid.build(YearMonth.of(2026, 8))

        assertEquals(42, days.size)
        assertEquals(LocalDate.of(2026, 7, 26), days.first().date)
        assertEquals(LocalDate.of(2026, 9, 5), days.last().date)
        assertFalse(days.first().isInDisplayedMonth)
        assertTrue(days[6].isInDisplayedMonth)
    }

    @Test
    fun `february 2026 uses five rows`() {
        val days = CalendarMonthGrid.build(YearMonth.of(2026, 2))

        assertEquals(35, days.size)
        assertEquals(LocalDate.of(2026, 2, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 3, 7), days.last().date)
    }
}
