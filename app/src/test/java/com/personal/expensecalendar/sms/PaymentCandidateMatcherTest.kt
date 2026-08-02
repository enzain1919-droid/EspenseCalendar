package com.personal.expensecalendar.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentCandidateMatcherTest {
    @Test
    fun `lotte approval is a candidate and card is detected`() {
        val body = """
            네이버페이
            46,600원 승인
            김*훈 로카 X 세라젬 7*6*
            일시불 07/20 22:11
        """.trimIndent()

        assertTrue(PaymentCandidateMatcher.isCandidate(body))
        assertEquals(CardDetector.LOTTE_CARD, CardDetector.detect(body))
    }

    @Test
    fun `hyundai cancellation is a candidate and card is detected`() {
        val body = """
            [Web발신]
            LGU+ M Ed3(통할2.0) 취소
            16,900원 일시불
            우아한형제들
        """.trimIndent()

        assertTrue(PaymentCandidateMatcher.isCandidate(body))
        assertEquals(CardDetector.HYUNDAI_CARD, CardDetector.detect(body))
    }

    @Test
    fun `unrelated message is not a candidate`() {
        assertFalse(PaymentCandidateMatcher.isCandidate("택배가 문 앞에 도착했습니다"))
    }

    @Test
    fun `approval word without payment amount is not a candidate`() {
        assertFalse(PaymentCandidateMatcher.isCandidate("이벤트 참여가 승인되었습니다"))
    }

    @Test
    fun `korean cancellation completion is a candidate`() {
        assertTrue(PaymentCandidateMatcher.isCandidate("네이버페이 40,600원 취소완료"))
    }

    @Test
    fun `membership benefit and billing notices are not candidates`() {
        assertFalse(
            PaymentCandidateMatcher.isCandidate(
                "U+멤버십승인 CGV 영화예매 혜택금액:15,000원",
            ),
        )
        assertFalse(
            PaymentCandidateMatcher.isCandidate(
                "[롯데카드] 07월 결제대금 1,242,840원이 출금되었습니다.",
            ),
        )
        assertFalse(
            PaymentCandidateMatcher.isCandidate(
                "[LG U+] 간편결제 매니저 서비스 가입 결과 안내 이용요금 1,100원",
            ),
        )
    }

    @Test
    fun `monthly highway usage approval is a candidate`() {
        assertTrue(
            PaymentCandidateMatcher.isCandidate(
                "[롯데카드] 김*훈님 06월 이용 하이패스 27건 60,290원, 07월 02일 승인",
            ),
        )
    }

    @Test
    fun `custom detection phrase resolves user card`() {
        val patterns = listOf(
            CardDetectionPattern(cardName = "새 카드", phrase = "MY CARD 1234"),
        )

        assertEquals(
            "새 카드",
            CardDetector.detect("MY CARD 1234 승인 10,000원", patterns),
        )
    }

    @Test
    fun `rule normalization ignores case and repeated spaces`() {
        assertEquals(
            "my card 1234",
            CardRuleNormalizer.normalize("  MY   CARD 1234  "),
        )
    }
}
