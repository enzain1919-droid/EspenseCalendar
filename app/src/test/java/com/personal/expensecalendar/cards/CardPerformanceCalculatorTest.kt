package com.personal.expensecalendar.cards

import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.storage.CardPerformanceExclusionEntity
import com.personal.expensecalendar.storage.CardPerformanceTierEntity
import com.personal.expensecalendar.storage.CardProfileEntity
import com.personal.expensecalendar.storage.CardProfileWithRules
import com.personal.expensecalendar.storage.PaymentMethod
import com.personal.expensecalendar.storage.PerformanceOverride
import com.personal.expensecalendar.storage.TransactionEntity
import com.personal.expensecalendar.storage.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class CardPerformanceCalculatorTest {
    @Test
    fun `approval and cancellation determine tier and benefit`() {
        val summary = calculate(
            transactions = listOf(
                transaction(amount = 500_000),
                transaction(amount = 50_000, status = PaymentStatus.CANCELED),
            ),
        )

        assertEquals(450_000L, summary.performanceWon)
        assertEquals(10_000L, summary.currentBenefitWon)
        assertEquals(700_000L, summary.nextThresholdWon)
        assertEquals(250_000L, summary.remainingToNextWon)
    }

    @Test
    fun `matching phrase and manual flag exclude transactions only from performance`() {
        val summary = calculate(
            exclusions = listOf("건강보험공단"),
            transactions = listOf(
                transaction(amount = 110_466, merchant = "지역연금자동이체_건강보험공단"),
                transaction(amount = 50_000, merchant = "일반 결제"),
                transaction(amount = 20_000, merchant = "수동 제외", included = false),
            ),
        )

        assertEquals(180_466L, summary.totalCardSpendWon)
        assertEquals(50_000L, summary.performanceWon)
        assertEquals(130_466L, summary.excludedWon)
        assertEquals(2, summary.excludedCount)
        assertEquals(300_000L, summary.nextThresholdWon)
    }

    @Test
    fun `cash income and another card do not affect performance`() {
        val summary = calculate(
            transactions = listOf(
                transaction(amount = 30_000, paymentMethod = PaymentMethod.CASH),
                transaction(amount = 40_000, type = TransactionType.INCOME),
                transaction(amount = 50_000, cardName = "다른카드"),
            ),
        )

        assertEquals(0L, summary.performanceWon)
        assertEquals(0L, summary.totalCardSpendWon)
    }

    @Test
    fun `manual include overrides a matching exclusion phrase`() {
        val summary = calculate(
            exclusions = listOf("건강보험공단"),
            transactions = listOf(
                transaction(
                    amount = 110_466,
                    merchant = "지역연금자동이체_건강보험공단",
                    performanceOverride = PerformanceOverride.INCLUDE,
                ),
            ),
        )

        assertEquals(110_466L, summary.performanceWon)
        assertEquals(0L, summary.excludedWon)
        assertEquals(0, summary.excludedCount)
    }

    private fun calculate(
        transactions: List<TransactionEntity>,
        exclusions: List<String> = emptyList(),
    ): CardPerformanceSummary {
        val card = CardProfileEntity(
            id = 1,
            displayName = "현대카드",
            normalizedName = "현대카드",
        )
        val settings = CardProfileWithRules(
            card = card,
            rules = emptyList(),
            performanceTiers = listOf(
                CardPerformanceTierEntity(1, card.id, 300_000, 10_000),
                CardPerformanceTierEntity(2, card.id, 700_000, 15_000),
            ),
            performanceExclusions = exclusions.mapIndexed { index, phrase ->
                CardPerformanceExclusionEntity(
                    id = index.toLong() + 1,
                    cardProfileId = card.id,
                    phrase = phrase,
                    normalizedPhrase = phrase,
                )
            },
        )
        return CardPerformanceCalculator.summarize(listOf(settings), transactions).single()
    }

    private fun transaction(
        amount: Long,
        merchant: String = "테스트 가맹점",
        status: PaymentStatus = PaymentStatus.APPROVED,
        included: Boolean = true,
        paymentMethod: PaymentMethod = PaymentMethod.CARD,
        type: TransactionType = TransactionType.EXPENSE,
        cardName: String = "현대카드",
        performanceOverride: PerformanceOverride = if (included) {
            PerformanceOverride.AUTO
        } else {
            PerformanceOverride.EXCLUDE
        },
    ) = TransactionEntity(
        sourceSmsId = amount,
        sourceFingerprint = "test-$amount-$merchant-$status-$included",
        cardName = cardName,
        merchant = merchant,
        amountWon = amount,
        status = status.name,
        occurredAtMillis = 0,
        includedInPerformance = included,
        performanceOverride = performanceOverride.name,
        paymentMethod = paymentMethod.name,
        transactionType = type.name,
        importedAtMillis = 0,
    )
}
