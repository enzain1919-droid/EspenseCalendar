package com.personal.expensecalendar.cards

import com.personal.expensecalendar.categories.normalizeMerchantText
import com.personal.expensecalendar.dashboard.ExpenseAggregation
import com.personal.expensecalendar.sms.CardRuleNormalizer
import com.personal.expensecalendar.storage.CardPerformanceTierEntity
import com.personal.expensecalendar.storage.CardProfileWithRules
import com.personal.expensecalendar.storage.PaymentMethod
import com.personal.expensecalendar.storage.PerformanceOverride
import com.personal.expensecalendar.storage.TransactionEntity
import com.personal.expensecalendar.storage.TransactionType

data class CardPerformanceSummary(
    val cardId: Long,
    val cardName: String,
    val performanceWon: Long,
    val totalCardSpendWon: Long,
    val excludedWon: Long,
    val excludedCount: Int,
    val currentBenefitWon: Long,
    val nextThresholdWon: Long?,
    val remainingToNextWon: Long?,
    val progress: Float,
    val tiers: List<CardPerformanceTierEntity>,
)

data class CardPerformanceInclusion(
    val isIncluded: Boolean,
    val exclusionReason: String? = null,
)

object CardPerformanceCalculator {
    fun summarize(
        cards: List<CardProfileWithRules>,
        transactions: List<TransactionEntity>,
    ): List<CardPerformanceSummary> = cards
        .filter { it.card.isActive }
        .map { cardWithRules ->
            summarizeCard(cardWithRules, transactions)
        }

    fun inclusionFor(
        cardWithRules: CardProfileWithRules,
        transaction: TransactionEntity,
    ): CardPerformanceInclusion {
        if (transaction.transactionType == TransactionType.INCOME.name) {
            return CardPerformanceInclusion(
                isIncluded = false,
                exclusionReason = "입금 내역",
            )
        }
        if (transaction.paymentMethod != PaymentMethod.CARD.name) {
            return CardPerformanceInclusion(
                isIncluded = false,
                exclusionReason = "카드 결제가 아님",
            )
        }
        val storedOverride = runCatching {
            PerformanceOverride.valueOf(transaction.performanceOverride)
        }.getOrDefault(PerformanceOverride.AUTO)
        val performanceOverride = if (
            storedOverride == PerformanceOverride.AUTO && !transaction.includedInPerformance
        ) {
            PerformanceOverride.EXCLUDE
        } else {
            storedOverride
        }
        if (performanceOverride == PerformanceOverride.EXCLUDE) {
            return CardPerformanceInclusion(
                isIncluded = false,
                exclusionReason = "사용자가 제외한 거래",
            )
        }
        if (performanceOverride == PerformanceOverride.INCLUDE) {
            return CardPerformanceInclusion(isIncluded = true)
        }
        val normalizedText = normalizedSearchText(transaction)
        val matchedRule = cardWithRules.performanceExclusions
            .asSequence()
            .filter { it.isActive }
            .sortedByDescending { normalizeMerchantText(it.phrase).length }
            .firstOrNull { rule ->
                val phrase = normalizeMerchantText(rule.phrase)
                phrase.isNotBlank() && normalizedText.contains(phrase)
            }
        return if (matchedRule == null) {
            CardPerformanceInclusion(isIncluded = true)
        } else {
            CardPerformanceInclusion(
                isIncluded = false,
                exclusionReason = "제외 문장: ${matchedRule.phrase}",
            )
        }
    }

    private fun summarizeCard(
        cardWithRules: CardProfileWithRules,
        transactions: List<TransactionEntity>,
    ): CardPerformanceSummary {
        val card = cardWithRules.card
        val cardName = CardRuleNormalizer.normalize(card.displayName)
        val cardTransactions = transactions.filter { transaction ->
            transaction.paymentMethod == PaymentMethod.CARD.name &&
                transaction.transactionType != TransactionType.INCOME.name &&
                CardRuleNormalizer.normalize(transaction.cardName) == cardName
        }
        val excludedTransactions = cardTransactions.filter { transaction ->
            !inclusionFor(cardWithRules, transaction).isIncluded
        }
        val includedTransactions = cardTransactions - excludedTransactions.toSet()
        val totalCardSpendWon = cardTransactions
            .sumOf(ExpenseAggregation::signedAmount)
            .coerceAtLeast(0)
        val performanceWon = includedTransactions
            .sumOf(ExpenseAggregation::signedAmount)
            .coerceAtLeast(0)
        val excludedWon = excludedTransactions
            .sumOf(ExpenseAggregation::signedAmount)
            .coerceAtLeast(0)
        val tiers = cardWithRules.performanceTiers.sortedBy { it.minimumSpendWon }
        val currentTier = tiers.lastOrNull { it.minimumSpendWon <= performanceWon }
        val nextTier = tiers.firstOrNull { it.minimumSpendWon > performanceWon }
        val maximumThreshold = tiers.maxOfOrNull { it.minimumSpendWon } ?: 0L

        return CardPerformanceSummary(
            cardId = card.id,
            cardName = card.displayName,
            performanceWon = performanceWon,
            totalCardSpendWon = totalCardSpendWon,
            excludedWon = excludedWon,
            excludedCount = excludedTransactions.size,
            currentBenefitWon = currentTier?.benefitWon ?: 0L,
            nextThresholdWon = nextTier?.minimumSpendWon,
            remainingToNextWon = nextTier?.minimumSpendWon?.minus(performanceWon),
            progress = if (maximumThreshold > 0L) {
                (performanceWon.toFloat() / maximumThreshold).coerceIn(0f, 1f)
            } else {
                0f
            },
            tiers = tiers,
        )
    }

    private fun normalizedSearchText(transaction: TransactionEntity): String =
        normalizeMerchantText(
            listOf(transaction.merchant, transaction.categoryName, transaction.memo)
                .joinToString(" "),
        )
}
