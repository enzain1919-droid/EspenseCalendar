package com.personal.expensecalendar.dashboard

import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.storage.MajorCategory
import com.personal.expensecalendar.storage.TransactionEntity
import com.personal.expensecalendar.storage.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyExpenseSummary(
    val date: LocalDate,
    val netAmountWon: Long,
    val incomeAmountWon: Long,
    val transactionCount: Int,
    val representativeCategory: String,
)

data class CategoryExpenseSummary(
    val categoryName: String,
    val netAmountWon: Long,
    val transactionCount: Int,
)

data class MajorCategoryExpenseSummary(
    val majorCategory: MajorCategory,
    val netAmountWon: Long,
    val transactionCount: Int,
)

object ExpenseAggregation {
    fun signedAmount(transaction: TransactionEntity): Long =
        if (transaction.transactionType == TransactionType.INCOME.name) {
            0
        } else if (transaction.status == PaymentStatus.CANCELED.name) {
            -transaction.amountWon
        } else {
            transaction.amountWon
        }

    fun incomeAmount(transaction: TransactionEntity): Long =
        if (transaction.transactionType == TransactionType.INCOME.name) {
            transaction.amountWon
        } else {
            0
        }

    fun monthlyTotal(transactions: List<TransactionEntity>): Long =
        transactions.sumOf(::signedAmount)

    fun monthlyIncome(transactions: List<TransactionEntity>): Long =
        transactions.sumOf(::incomeAmount)

    fun byCategory(transactions: List<TransactionEntity>): List<CategoryExpenseSummary> =
        transactions
            .filter { it.transactionType != TransactionType.INCOME.name }
            .groupBy(TransactionEntity::categoryName)
            .map { (categoryName, items) ->
                CategoryExpenseSummary(
                    categoryName = categoryName,
                    netAmountWon = items.sumOf(::signedAmount),
                    transactionCount = items.size,
                )
            }
            .filter { it.netAmountWon != 0L }
            .sortedWith(
                compareByDescending<CategoryExpenseSummary> {
                    if (it.netAmountWon > 0L) 1 else 0
                }.thenByDescending { kotlin.math.abs(it.netAmountWon) },
            )

    fun byMajorCategory(
        transactions: List<TransactionEntity>,
    ): List<MajorCategoryExpenseSummary> {
        val expenses = transactions.filter {
            it.transactionType != TransactionType.INCOME.name
        }
        return MajorCategory.entries.map { majorCategory ->
            val items = expenses.filter {
                MajorCategory.fromStored(it.majorCategory) == majorCategory
            }
            MajorCategoryExpenseSummary(
                majorCategory = majorCategory,
                netAmountWon = items.sumOf(::signedAmount),
                transactionCount = items.size,
            )
        }
    }

    fun byDay(
        transactions: List<TransactionEntity>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, DailyExpenseSummary> = transactions
        .groupBy { transaction ->
            Instant.ofEpochMilli(transaction.occurredAtMillis)
                .atZone(zoneId)
                .toLocalDate()
        }
        .mapValues { (date, items) ->
            DailyExpenseSummary(
                date = date,
                netAmountWon = items.sumOf(::signedAmount),
                incomeAmountWon = items.sumOf(::incomeAmount),
                transactionCount = items.size,
                representativeCategory = items
                    .groupingBy(TransactionEntity::categoryName)
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    ?: "기타",
            )
        }
}
