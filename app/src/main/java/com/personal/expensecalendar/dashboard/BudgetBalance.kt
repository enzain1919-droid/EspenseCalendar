package com.personal.expensecalendar.dashboard

data class BudgetBalance(
    val availableWon: Long,
    val remainingWon: Long,
    val usageRatio: Float,
)

object BudgetBalanceCalculator {
    fun calculate(
        budgetWon: Long,
        spentWon: Long,
        incomeWon: Long,
    ): BudgetBalance {
        val availableWon = budgetWon + incomeWon
        return BudgetBalance(
            availableWon = availableWon,
            remainingWon = availableWon - spentWon,
            usageRatio = if (availableWon > 0L) {
                spentWon.toFloat() / availableWon
            } else {
                0f
            },
        )
    }
}
