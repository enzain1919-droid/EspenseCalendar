package com.personal.expensecalendar.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class MonthRangeTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `range starts at first day and ends at next month`() {
        val range = MonthRangeFactory.forMonth(YearMonth.of(2026, 8), seoul)

        assertEquals(
            Instant.parse("2026-07-31T15:00:00Z").toEpochMilli(),
            range.startInclusiveMillis,
        )
        assertEquals(
            Instant.parse("2026-08-31T15:00:00Z").toEpochMilli(),
            range.endExclusiveMillis,
        )
        assertEquals("2026년 8월", range.displayName)
    }

    @Test
    fun `end is strictly after start`() {
        val range = MonthRangeFactory.forMonth(YearMonth.of(2026, 12), seoul)
        assertTrue(range.endExclusiveMillis > range.startInclusiveMillis)
    }
}
