package com.personal.expensecalendar.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetBalanceCalculatorTest {
    @Test
    fun `cash income increases available budget and remaining amount`() {
        val result = BudgetBalanceCalculator.calculate(
            budgetWon = 2_000_000,
            spentWon = 3_000_000,
            incomeWon = 1_000_000,
        )

        assertEquals(3_000_000L, result.availableWon)
        assertEquals(0L, result.remainingWon)
        assertEquals(1f, result.usageRatio)
    }

    @Test
    fun `remaining amount can still be over budget after income`() {
        val result = BudgetBalanceCalculator.calculate(
            budgetWon = 2_000_000,
            spentWon = 3_500_000,
            incomeWon = 1_000_000,
        )

        assertEquals(-500_000L, result.remainingWon)
    }
}
