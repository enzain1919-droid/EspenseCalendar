package com.personal.expensecalendar.storage

import com.personal.expensecalendar.sms.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DeletedTransactionEntityTest {
    @Test
    fun `deleted snapshot restores every editable transaction field`() {
        val original = TransactionEntity(
            id = 42,
            sourceSmsId = 77,
            sourceFingerprint = "sms-fingerprint",
            cardName = "롯데카드",
            merchant = "테스트 가맹점",
            amountWon = 123_456,
            status = PaymentStatus.CANCELED.name,
            occurredAtMillis = 1_754_000_000_000,
            categoryName = "여가/문화",
            majorCategory = MajorCategory.JINYOUNG_ALLOWANCE.name,
            includedInPerformance = false,
            performanceOverride = PerformanceOverride.EXCLUDE.name,
            includedInExpense = false,
            source = "SMS",
            memo = "복구 메모",
            transactionType = TransactionType.EXPENSE.name,
            paymentMethod = PaymentMethod.CARD.name,
            importedAtMillis = 1_754_100_000_000,
        )

        val deleted = DeletedTransactionEntity.from(original, deletedAtMillis = 1_755_000_000_000)
        val restored = deleted.restoreAsTransaction()

        assertEquals(0L, restored.id)
        assertEquals(original.copy(id = 0), restored)
        assertEquals(1_755_000_000_000, deleted.deletedAtMillis)
    }
}
