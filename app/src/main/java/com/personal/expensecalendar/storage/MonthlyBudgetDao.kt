package com.personal.expensecalendar.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MonthlyBudgetDao {
    @Query(
        "SELECT * FROM monthly_budgets " +
            "WHERE yearMonth <= :yearMonth " +
            "ORDER BY yearMonth DESC LIMIT 1",
    )
    suspend fun findEffective(yearMonth: String): MonthlyBudgetEntity?

    @Upsert
    suspend fun upsert(budget: MonthlyBudgetEntity)
}
