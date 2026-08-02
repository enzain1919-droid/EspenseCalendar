package com.personal.expensecalendar.categories

data class ClassificationPattern(
    val categoryName: String,
    val phrase: String,
    val normalizedPhrase: String = normalizeMerchantText(phrase),
)

object MerchantClassifier {
    fun classify(
        merchant: String,
        patterns: List<ClassificationPattern>,
    ): String? {
        val normalizedMerchant = normalizeMerchantText(merchant)
        return patterns
            .sortedByDescending { it.normalizedPhrase.length }
            .firstOrNull { pattern ->
                pattern.normalizedPhrase.isNotBlank() &&
                    normalizedMerchant.contains(pattern.normalizedPhrase)
            }
            ?.categoryName
    }
}

fun normalizeMerchantText(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]"), "")
