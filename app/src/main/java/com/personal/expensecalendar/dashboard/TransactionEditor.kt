package com.personal.expensecalendar.dashboard

import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.storage.PaymentMethod
import com.personal.expensecalendar.storage.PerformanceOverride
import com.personal.expensecalendar.storage.MajorCategory
import com.personal.expensecalendar.storage.TransactionEntity
import com.personal.expensecalendar.storage.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TransactionEditInput(
    val merchant: String,
    val amountWon: Long,
    val date: LocalDate,
    val memo: String,
    val transactionType: TransactionType,
    val paymentMethod: PaymentMethod,
    val cardName: String?,
    val majorCategory: MajorCategory,
    val categoryName: String,
    val status: PaymentStatus,
    val performanceOverride: PerformanceOverride,
    val includedInExpense: Boolean = true,
)

object TransactionEditor {
    fun validationError(input: TransactionEditInput): String? = when {
        input.amountWon <= 0L -> "금액은 0원보다 커야 합니다."
        input.merchant.isBlank() -> "가맹점 또는 내용을 입력해 주세요."
        input.categoryName.isBlank() -> "카테고리를 선택해 주세요."
        input.paymentMethod == PaymentMethod.CARD && input.cardName.isNullOrBlank() ->
            "카드를 선택해 주세요."
        else -> null
    }

    fun apply(
        original: TransactionEntity,
        input: TransactionEditInput,
        zoneId: ZoneId,
    ): TransactionEntity {
        val originalTime = Instant.ofEpochMilli(original.occurredAtMillis)
            .atZone(zoneId)
            .toLocalTime()
        val isCardExpense = input.transactionType == TransactionType.EXPENSE &&
            input.paymentMethod == PaymentMethod.CARD
        val effectiveOverride = if (isCardExpense) {
            input.performanceOverride
        } else {
            PerformanceOverride.AUTO
        }
        return original.copy(
            merchant = input.merchant.trim().ifBlank { "직접 입력" },
            amountWon = input.amountWon,
            occurredAtMillis = input.date.atTime(originalTime)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli(),
            memo = input.memo.trim(),
            transactionType = input.transactionType.name,
            paymentMethod = input.paymentMethod.name,
            cardName = if (input.paymentMethod == PaymentMethod.CASH) {
                "현금"
            } else {
                input.cardName?.trim().takeUnless { it.isNullOrBlank() } ?: original.cardName
            },
            majorCategory = input.majorCategory.name,
            categoryName = input.categoryName,
            status = if (isCardExpense) input.status.name else PaymentStatus.APPROVED.name,
            includedInPerformance = effectiveOverride != PerformanceOverride.EXCLUDE,
            performanceOverride = effectiveOverride.name,
            includedInExpense = if (input.transactionType == TransactionType.EXPENSE) {
                input.includedInExpense
            } else {
                true
            },
        )
    }
}
