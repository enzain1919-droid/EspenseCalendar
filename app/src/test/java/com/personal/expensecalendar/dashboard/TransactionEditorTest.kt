package com.personal.expensecalendar.dashboard

import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.storage.PaymentMethod
import com.personal.expensecalendar.storage.PerformanceOverride
import com.personal.expensecalendar.storage.MajorCategory
import com.personal.expensecalendar.storage.TransactionEntity
import com.personal.expensecalendar.storage.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionEditorTest {
    private val zoneId = ZoneId.of("Asia/Seoul")

    @Test
    fun `editing date preserves original transaction time and fields`() {
        val original = transactionAt(LocalDateTime.of(2026, 7, 31, 22, 47))

        val edited = TransactionEditor.apply(
            original = original,
            input = TransactionEditInput(
                merchant = "  수정 가맹점  ",
                amountWon = 1_000_000,
                date = LocalDate.of(2026, 8, 2),
                memo = "  메모  ",
                transactionType = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.CARD,
                cardName = "롯데카드",
                majorCategory = MajorCategory.JINYOUNG_ALLOWANCE,
                categoryName = "쇼핑",
                status = PaymentStatus.CANCELED,
                performanceOverride = PerformanceOverride.EXCLUDE,
            ),
            zoneId = zoneId,
        )

        val editedDateTime = java.time.Instant.ofEpochMilli(edited.occurredAtMillis)
            .atZone(zoneId)
            .toLocalDateTime()
        assertEquals(LocalDateTime.of(2026, 8, 2, 22, 47), editedDateTime)
        assertEquals("수정 가맹점", edited.merchant)
        assertEquals(1_000_000L, edited.amountWon)
        assertEquals("메모", edited.memo)
        assertEquals("롯데카드", edited.cardName)
        assertEquals("쇼핑", edited.categoryName)
        assertEquals(MajorCategory.JINYOUNG_ALLOWANCE.name, edited.majorCategory)
        assertEquals(PaymentStatus.CANCELED.name, edited.status)
        assertEquals(PerformanceOverride.EXCLUDE.name, edited.performanceOverride)
    }

    @Test
    fun `cash income clears card-only status and performance override`() {
        val edited = TransactionEditor.apply(
            original = transactionAt(LocalDateTime.of(2026, 8, 1, 12, 0)),
            input = TransactionEditInput(
                merchant = "현금 입금",
                amountWon = 500_000,
                date = LocalDate.of(2026, 8, 1),
                memo = "",
                transactionType = TransactionType.INCOME,
                paymentMethod = PaymentMethod.CASH,
                cardName = "현대카드",
                majorCategory = MajorCategory.FIXED_EXPENSE,
                categoryName = "기타",
                status = PaymentStatus.CANCELED,
                performanceOverride = PerformanceOverride.INCLUDE,
            ),
            zoneId = zoneId,
        )

        assertEquals("현금", edited.cardName)
        assertEquals(PaymentStatus.APPROVED.name, edited.status)
        assertEquals(PerformanceOverride.AUTO.name, edited.performanceOverride)
        assertEquals(true, edited.includedInPerformance)
    }

    private fun transactionAt(dateTime: LocalDateTime) = TransactionEntity(
        id = 7,
        sourceSmsId = 100,
        sourceFingerprint = "sms:100",
        cardName = "현대카드",
        merchant = "원래 가맹점",
        amountWon = 10_000,
        status = PaymentStatus.APPROVED.name,
        occurredAtMillis = dateTime.atZone(zoneId).toInstant().toEpochMilli(),
        importedAtMillis = 0,
    )
}
