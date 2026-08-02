package com.personal.expensecalendar.sms

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageReviewCandidateSelectorTest {
    private val parser = PaymentMessageParser(ZoneId.of("Asia/Seoul"))
    private val patterns = CardDetector.defaultPatterns

    @Test
    fun `keeps only candidates that the parser cannot analyze`() {
        val recognized = record(1, "네이버페이 8,000원 승인 로카 X 세라젬")
        val unknownCard = record(2, "[새카드] 12,300원 승인 일시불 테스트가맹점")

        val result = MessageReviewCandidateSelector.select(
            candidates = listOf(recognized, unknownCard),
            detectionPatterns = patterns,
            deletedFingerprints = emptySet(),
            existingFingerprints = emptySet(),
            parser = parser,
        )

        assertEquals(1, result.size)
        assertEquals(unknownCard, result.single().record)
        assertEquals(12_300L, result.single().draft.amountWon)
        assertTrue("카드 미식별" in result.single().draft.missingReasons)
    }

    @Test
    fun `hides candidates that were registered or excluded`() {
        val registered = record(3, "[새카드] 10,000원 승인 일시불 등록됨")
        val excluded = record(4, "[새카드] 20,000원 승인 일시불 제외됨")

        val result = MessageReviewCandidateSelector.select(
            candidates = listOf(registered, excluded),
            detectionPatterns = patterns,
            deletedFingerprints = setOf(MessageFingerprint.create(excluded)),
            existingFingerprints = setOf(MessageFingerprint.create(registered)),
            parser = parser,
        )

        assertTrue(result.isEmpty())
    }

    private fun record(id: Long, body: String) = SmsRecord(
        id = id,
        sender = "15880000",
        receivedAtMillis = Instant.parse("2026-08-02T03:00:00Z").toEpochMilli(),
        body = body,
    )
}
