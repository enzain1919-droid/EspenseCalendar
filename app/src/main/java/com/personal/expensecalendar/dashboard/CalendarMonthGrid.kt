package com.personal.expensecalendar.dashboard

import java.time.LocalDate
import java.time.YearMonth

data class CalendarDay(
    val date: LocalDate,
    val isInDisplayedMonth: Boolean,
)

object CalendarMonthGrid {
    fun build(yearMonth: YearMonth): List<CalendarDay> {
        val firstDay = yearMonth.atDay(1)
        val sundayBasedOffset = firstDay.dayOfWeek.value % 7
        val usedCells = sundayBasedOffset + yearMonth.lengthOfMonth()
        val cellCount = if (usedCells <= 35) 35 else 42
        val gridStart = firstDay.minusDays(sundayBasedOffset.toLong())

        return List(cellCount) { index ->
            val date = gridStart.plusDays(index.toLong())
            CalendarDay(
                date = date,
                isInDisplayedMonth = YearMonth.from(date) == yearMonth,
            )
        }
    }
}
