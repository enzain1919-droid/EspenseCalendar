package com.personal.expensecalendar.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monthly_budgets")
data class MonthlyBudgetEntity(
    @PrimaryKey
    val yearMonth: String,
    val amountWon: Long,
)
