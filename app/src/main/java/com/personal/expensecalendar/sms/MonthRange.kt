package com.personal.expensecalendar.sms

import java.time.YearMonth
import java.time.ZoneId

data class MonthRange(
    val yearMonth: YearMonth,
    val startInclusiveMillis: Long,
    val endExclusiveMillis: Long,
) {
    val displayName: String = "${yearMonth.year}년 ${yearMonth.monthValue}월"
}

object MonthRangeFactory {
    fun current(
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: YearMonth = YearMonth.now(zoneId),
    ): MonthRange = forMonth(now, zoneId)

    fun forMonth(yearMonth: YearMonth, zoneId: ZoneId): MonthRange {
        val start = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return MonthRange(
            yearMonth = yearMonth,
            startInclusiveMillis = start,
            endExclusiveMillis = end,
        )
    }
}
