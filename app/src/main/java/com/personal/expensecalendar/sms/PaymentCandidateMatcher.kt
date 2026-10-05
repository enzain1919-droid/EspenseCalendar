package com.personal.expensecalendar.sms

object PaymentCandidateMatcher {
    private val nonTransactionPhrases = listOf(
        "혜택금액:",
        "결제대금",
        "가입 결과 안내",
        "가입결과 안내",
        "카드 결제 또는 계좌 입금",
    )
    private val defaultKeywords = listOf(
        "승인취소",
        "취소완료",
        "자동납부 승인",
        "승인",
        "취소",
        "결제",
        "일시불",
    )
    private val transactionHints = listOf(
        "원",
        "일시불",
        "할부",
        "누적",
        "카드",
        "로카",
        "자동납부",
    )

    fun isAdvertisement(body: String): Boolean = body.contains("광고")

    fun isCandidate(body: String): Boolean =
        !isAdvertisement(body) &&
            defaultKeywords.any(body::contains) &&
            transactionHints.any(body::contains) &&
            nonTransactionPhrases.none(body::contains)
}

object CardDetector {
    const val LOTTE_CARD = "롯데카드"
    const val HYUNDAI_CARD = "현대카드"
    const val UNKNOWN_CARD = "미등록 카드"

    val defaultPatterns = listOf(
        CardDetectionPattern(LOTTE_CARD, "로카 X 세라젬"),
        CardDetectionPattern(LOTTE_CARD, "[롯데카드]"),
        CardDetectionPattern(HYUNDAI_CARD, "LGU+ M Ed3(통할2.0)"),
        CardDetectionPattern(HYUNDAI_CARD, "[현대카드]"),
        CardDetectionPattern(HYUNDAI_CARD, "현대카드 M"),
    )

    fun detect(
        body: String,
        patterns: List<CardDetectionPattern> = defaultPatterns,
    ): String {
        val normalizedBody = CardRuleNormalizer.normalize(body)
        return patterns
        .sortedByDescending { CardRuleNormalizer.normalize(it.phrase).length }
        .firstOrNull { pattern ->
            normalizedBody.contains(CardRuleNormalizer.normalize(pattern.phrase))
        }
        ?.cardName
        ?: UNKNOWN_CARD
    }
}

data class CardDetectionPattern(
    val cardName: String,
    val phrase: String,
)

object CardRuleNormalizer {
    fun normalize(value: String): String = value
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()
}
