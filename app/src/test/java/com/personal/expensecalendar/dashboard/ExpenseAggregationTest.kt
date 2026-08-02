package com.personal.expensecalendar.dashboard

import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.storage.MajorCategory
import com.personal.expensecalendar.storage.TransactionEntity
import com.personal.expensecalendar.storage.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ExpenseAggregationTest {
    @Test
    fun `cancellation is subtracted from monthly and daily total`() {
        val transactions = listOf(
            transaction(amount = 50_000, status = PaymentStatus.APPROVED),
            transaction(amount = 20_000, status = PaymentStatus.CANCELED),
        )

        assertEquals(30_000L, ExpenseAggregation.monthlyTotal(transactions))
        val daily = ExpenseAggregation.byDay(transactions, ZoneId.of("Asia/Seoul"))
        assertEquals(30_000L, daily.values.single().netAmountWon)
        assertEquals(2, daily.values.single().transactionCount)
    }

    @Test
    fun `income is tracked separately and does not reduce expense budget`() {
        val transactions = listOf(
            transaction(amount = 50_000, status = PaymentStatus.APPROVED),
            transaction(
                amount = 100_000,
                status = PaymentStatus.APPROVED,
                type = TransactionType.INCOME,
            ),
        )

        assertEquals(50_000L, ExpenseAggregation.monthlyTotal(transactions))
        assertEquals(100_000L, ExpenseAggregation.monthlyIncome(transactions))
        val daily = ExpenseAggregation.byDay(transactions, ZoneId.of("Asia/Seoul"))
        assertEquals(50_000L, daily.values.single().netAmountWon)
        assertEquals(100_000L, daily.values.single().incomeAmountWon)
    }

    @Test
    fun `category totals subtract cancellations ignore income and sort by spending`() {
        val transactions = listOf(
            transaction(80_000, PaymentStatus.APPROVED, category = "외식비"),
            transaction(20_000, PaymentStatus.CANCELED, category = "외식비"),
            transaction(120_000, PaymentStatus.APPROVED, category = "교통비"),
            transaction(30_000, PaymentStatus.APPROVED, TransactionType.INCOME, "기타"),
            transaction(10_000, PaymentStatus.CANCELED, category = "보험비"),
        )

        val categories = ExpenseAggregation.byCategory(transactions)

        assertEquals(listOf("교통비", "외식비", "보험비"), categories.map { it.categoryName })
        assertEquals(listOf(120_000L, 60_000L, -10_000L), categories.map { it.netAmountWon })
        assertEquals(listOf(1, 2, 1), categories.map { it.transactionCount })
    }

    @Test
    fun `major category totals always show three groups and ignore income`() {
        val transactions = listOf(
            transaction(
                amount = 80_000,
                status = PaymentStatus.APPROVED,
                majorCategory = MajorCategory.LIVING_EXPENSE,
            ),
            transaction(
                amount = 20_000,
                status = PaymentStatus.CANCELED,
                majorCategory = MajorCategory.LIVING_EXPENSE,
            ),
            transaction(
                amount = 120_000,
                status = PaymentStatus.APPROVED,
                majorCategory = MajorCategory.FIXED_EXPENSE,
            ),
            transaction(
                amount = 30_000,
                status = PaymentStatus.APPROVED,
                type = TransactionType.INCOME,
                majorCategory = MajorCategory.JINYOUNG_ALLOWANCE,
            ),
        )

        val summaries = ExpenseAggregation.byMajorCategory(transactions)

        assertEquals(MajorCategory.entries, summaries.map { it.majorCategory })
        assertEquals(listOf(60_000L, 120_000L, 0L), summaries.map { it.netAmountWon })
        assertEquals(listOf(2, 1, 0), summaries.map { it.transactionCount })
    }

    @Test
    fun `expense exclusion keeps the transaction but removes it from every spending total`() {
        val transactions = listOf(
            transaction(
                amount = 50_000,
                status = PaymentStatus.APPROVED,
                category = "외식비",
            ),
            transaction(
                amount = 80_000,
                status = PaymentStatus.APPROVED,
                category = "교통비",
                majorCategory = MajorCategory.FIXED_EXPENSE,
                includedInExpense = false,
            ),
            transaction(
                amount = 20_000,
                status = PaymentStatus.CANCELED,
                category = "보험비",
                includedInExpense = false,
            ),
        )

        assertEquals(50_000L, ExpenseAggregation.monthlyTotal(transactions))
        val daily = ExpenseAggregation.byDay(transactions, ZoneId.of("Asia/Seoul")).values.single()
        assertEquals(50_000L, daily.netAmountWon)
        assertEquals(3, daily.transactionCount)
        assertEquals(listOf("외식비"), ExpenseAggregation.byCategory(transactions).map { it.categoryName })
        assertEquals(
            listOf(50_000L, 0L, 0L),
            ExpenseAggregation.byMajorCategory(transactions).map { it.netAmountWon },
        )
    }

    private fun transaction(
        amount: Long,
        status: PaymentStatus,
        type: TransactionType = TransactionType.EXPENSE,
        category: String = "기타",
        majorCategory: MajorCategory = MajorCategory.LIVING_EXPENSE,
        includedInExpense: Boolean = true,
    ) = TransactionEntity(
        sourceSmsId = amount,
        sourceFingerprint = "test-$amount-$status",
        cardName = "테스트카드",
        merchant = "테스트 가맹점",
        amountWon = amount,
        status = status.name,
        categoryName = category,
        majorCategory = majorCategory.name,
        transactionType = type.name,
        includedInExpense = includedInExpense,
        occurredAtMillis = Instant.parse("2026-08-02T03:00:00Z").toEpochMilli(),
        importedAtMillis = 0,
    )
}
