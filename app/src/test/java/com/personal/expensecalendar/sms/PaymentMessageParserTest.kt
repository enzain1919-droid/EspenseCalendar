package com.personal.expensecalendar.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PaymentMessageParserTest {
    private val parser = PaymentMessageParser(ZoneId.of("Asia/Seoul"))
    private val receivedAt = Instant.parse("2026-08-02T03:00:00Z").toEpochMilli()

    @Test
    fun `parses lotte approval`() {
        val payment = parse(
            """
                네이버페이
                46,600원 승인
                김*훈 로카 X 세라젬 7*6*
                일시불 07/20 22:11
                누적 1,614,857원
            """.trimIndent(),
        )

        assertEquals(CardDetector.LOTTE_CARD, payment.cardName)
        assertEquals("네이버페이", payment.merchant)
        assertEquals(46_600L, payment.amountWon)
        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertEquals(Instant.parse("2026-07-20T13:11:00Z").toEpochMilli(), payment.occurredAtMillis)
    }

    @Test
    fun `forwarded lotte message ignores sender metadata and web prefix`() {
        val payment = parse(
            """
                보낸사람 : 15888100
                [Web발신] CJ CGV (씨제이 씨지브이) ㈜
                15,000원 승인
                김*훈 로카 X 세라젬(7*6*)
                일시불, 07/31 22:47
                누적 2,753,087원
            """.trimIndent(),
        )

        assertEquals("CJ CGV (씨제이 씨지브이) ㈜", payment.merchant)
        assertEquals(15_000L, payment.amountWon)
        assertEquals(CardDetector.LOTTE_CARD, payment.cardName)
    }

    @Test
    fun `parses mms lotte auto repair approval`() {
        val payment = parse(
            """
                광교종합서비스기아오토큐주식
                165,220원 승인
                김*훈 로카 X 세라젬 7*6*
                일시불 07/30 15:35
                누적 2,594,587원
            """.trimIndent(),
        )

        assertEquals("광교종합서비스기아오토큐주식", payment.merchant)
        assertEquals(165_220L, payment.amountWon)
    }

    @Test
    fun `parses lotte cancellation`() {
        val payment = parse(
            """
                네이버페이
                35,900원 승인취소
                김*훈 로카 X 세라젬 7*6*
                일시불 07/20 21:22
                누적 1,560,757원
            """.trimIndent(),
        )

        assertEquals(35_900L, payment.amountWon)
        assertEquals(PaymentStatus.CANCELED, payment.status)
    }

    @Test
    fun `parses hyundai LGU approval`() {
        val payment = parse(
            """
                [Web발신]
                LGU+ M Ed3(통할2.0) 승인
                김*훈
                21,500원 일시불
                07/20 20:49
                네이버파이낸
                누적769,413원
            """.trimIndent(),
        )

        assertEquals(CardDetector.HYUNDAI_CARD, payment.cardName)
        assertEquals("네이버파이낸", payment.merchant)
        assertEquals(21_500L, payment.amountWon)
        assertEquals(PaymentStatus.APPROVED, payment.status)
    }

    @Test
    fun `parses hyundai M approval`() {
        val payment = parse(
            """
                [Web발신] 현대카드 M 승인
                김*훈
                48,000원 일시불
                07/20 15:18
                CJCGV
                누적 1,271,081원
            """.trimIndent(),
        )

        assertEquals(CardDetector.HYUNDAI_CARD, payment.cardName)
        assertEquals("CJCGV", payment.merchant)
        assertEquals(48_000L, payment.amountWon)
    }

    @Test
    fun `parses korean am pm date`() {
        val payment = parse(
            """
                네이버페이
                40,600원 취소완료
                김*훈 로카 X 세라젬(7*6*)
                07/20 오후 10:01
            """.trimIndent(),
        )

        assertEquals(PaymentStatus.CANCELED, payment.status)
        assertEquals(Instant.parse("2026-07-20T13:01:00Z").toEpochMilli(), payment.occurredAtMillis)
    }

    @Test
    fun `parses hyundai LGU cancellation`() {
        val payment = parse(
            """
                [Web발신]
                LGU+ M Ed3(통할2.0) 취소
                김*훈
                16,900원 일시불
                07/09 18:17
                우아한형제들
                누적966,124원
            """.trimIndent(),
        )

        assertEquals("우아한형제들", payment.merchant)
        assertEquals(PaymentStatus.CANCELED, payment.status)
    }

    @Test
    fun `parses single line hyundai automatic payment`() {
        val payment = parse(
            "[현대카드] 자동납부 승인 김*훈님 지역연금자동이체_건강보험공단 110,466원",
        )

        assertEquals(CardDetector.HYUNDAI_CARD, payment.cardName)
        assertEquals("지역연금자동이체_건강보험공단", payment.merchant)
        assertEquals(110_466L, payment.amountWon)
        assertEquals(receivedAt, payment.occurredAtMillis)
    }

    @Test
    fun `parses lotte monthly highway usage approval`() {
        val payment = parse(
            """
                보낸사람 : 15888100()
                [롯데카드]
                김*훈님 06월 이용
                하이패스 27건 60,290원,
                07월 02일 승인
            """.trimIndent(),
        )

        assertEquals(CardDetector.LOTTE_CARD, payment.cardName)
        assertEquals("하이패스", payment.merchant)
        assertEquals(60_290L, payment.amountWon)
        assertEquals(PaymentStatus.APPROVED, payment.status)
        assertEquals(
            Instant.parse("2026-07-02T03:00:00Z").toEpochMilli(),
            payment.occurredAtMillis,
        )
    }

    @Test
    fun `ignores payment message from unregistered card`() {
        val result = parser.parse(record("다른 카드 승인 12,000원"))
        assertEquals(null, result)
    }

    @Test
    fun `creates review draft when card is not registered`() {
        val draft = parser.createReviewDraft(
            record(
                """
                    새로운카드 승인
                    12,300원 일시불
                    08/01 19:25
                    테스트가맹점
                """.trimIndent(),
            ),
        )

        assertNull(draft.cardName)
        assertEquals(12_300L, draft.amountWon)
        assertEquals("테스트가맹점", draft.merchant)
        assertEquals(Instant.parse("2026-08-01T10:25:00Z").toEpochMilli(), draft.occurredAtMillis)
        assertTrue("카드 미식별" in draft.missingReasons)
    }

    @Test
    fun `review draft identifies missing amount`() {
        val draft = parser.createReviewDraft(record("롯데카드 로카 X 세라젬 승인 금액 확인 필요"))

        assertEquals(CardDetector.LOTTE_CARD, draft.cardName)
        assertNull(draft.amountWon)
        assertTrue("금액 미식별" in draft.missingReasons)
    }

    @Test
    fun `fingerprint is stable and changes with source message`() {
        val first = record("네이버페이 8,000원 승인 로카 X 세라젬")
        val same = first.copy()
        val changed = first.copy(id = 2, body = "네이버페이 9,000원 승인 로카 X 세라젬")

        assertEquals(MessageFingerprint.create(first), MessageFingerprint.create(same))
        assertNotEquals(MessageFingerprint.create(first), MessageFingerprint.create(changed))
        assertNotEquals(
            MessageFingerprint.create(first),
            MessageFingerprint.create(first.copy(transport = MessageTransport.MMS)),
        )
    }

    @Test
    fun `advertisement is not parsed as a payment even with a recognized card and amount`() {
        val body = "(광고)[롯데카드] 통큰데이 25,000원 결제 시 할인 일시불"
        assertNull(parser.parse(record(body)))
        assertNull(parser.parse(record(body).copy(transport = MessageTransport.MMS)))
    }

    private fun parse(body: String): ParsedPayment {
        val result = parser.parse(record(body))
        assertNotNull(result)
        return requireNotNull(result)
    }

    private fun record(body: String) = SmsRecord(
        id = 1,
        sender = "15776200",
        receivedAtMillis = receivedAt,
        body = body,
    )
}
