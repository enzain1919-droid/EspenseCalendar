package com.personal.expensecalendar.categories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantClassifierTest {
    private val patterns = listOf(
        ClassificationPattern("보험비", "건강보험공단"),
        ClassificationPattern("외식비", "우아한형제들"),
    )

    @Test
    fun `classifies merchant when phrase is contained`() {
        assertEquals(
            "보험비",
            MerchantClassifier.classify("지역연금자동이체_건강보험공단", patterns),
        )
    }

    @Test
    fun `normalization ignores case and repeated spaces`() {
        val custom = listOf(ClassificationPattern("쇼핑", "NAVER PAY"))
        assertEquals("쇼핑", MerchantClassifier.classify("naver   pay 정기결제", custom))
    }

    @Test
    fun `returns null when no rule matches`() {
        assertNull(MerchantClassifier.classify("알 수 없는 가맹점", patterns))
    }

    @Test
    fun `normalization ignores punctuation and symbols`() {
        val custom = listOf(ClassificationPattern("교통비", "S-OIL"))

        assertEquals("교통비", MerchantClassifier.classify("S OIL-강남주유소", custom))
    }

    @Test
    fun `longer and more specific phrase wins`() {
        val custom = listOf(
            ClassificationPattern("식료품", "이마트"),
            ClassificationPattern("생활비", "이마트24"),
        )

        assertEquals("생활비", MerchantClassifier.classify("이마트24 서울역점", custom))
    }

    @Test
    fun `expanded common merchants are classified`() {
        val expanded = listOf(
            ClassificationPattern("외식비", "스타벅스"),
            ClassificationPattern("쇼핑", "쿠팡"),
            ClassificationPattern("의료비", "약국"),
            ClassificationPattern("여가/문화", "노래연습장"),
        )

        assertEquals("외식비", MerchantClassifier.classify("스타벅스 강남점", expanded))
        assertEquals("쇼핑", MerchantClassifier.classify("쿠팡 주식회사", expanded))
        assertEquals("의료비", MerchantClassifier.classify("행복한약국", expanded))
        assertEquals("여가/문화", MerchantClassifier.classify("악쓰는하마코인노래연습장", expanded))
    }
}
