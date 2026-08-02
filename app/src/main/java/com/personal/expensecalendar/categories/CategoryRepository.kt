package com.personal.expensecalendar.categories

import com.personal.expensecalendar.storage.CategoryDao
import com.personal.expensecalendar.storage.CategoryEntity
import com.personal.expensecalendar.storage.ClassificationRuleEntity
import com.personal.expensecalendar.storage.TransactionEntity

class CategoryRepository(
    private val dao: CategoryDao,
) {
    suspend fun activePatterns(): List<ClassificationPattern> = dao.findActiveRules().map { rule ->
        ClassificationPattern(
            categoryName = rule.categoryName,
            phrase = rule.phrase,
        )
    }

    suspend fun addCategory(name: String) {
        val cleanName = name.trim()
        dao.insertCategory(
            CategoryEntity(
                name = cleanName,
                normalizedName = normalizeMerchantText(cleanName),
            ),
        )
    }

    suspend fun addRule(
        categoryId: Long,
        phrase: String,
    ): Int {
        val cleanPhrase = phrase.trim()
        dao.insertRule(
            ClassificationRuleEntity(
                categoryId = categoryId,
                phrase = cleanPhrase,
                normalizedPhrase = normalizeMerchantText(cleanPhrase),
            ),
        )
        return reclassifyOther()
    }

    suspend fun deleteRule(rule: ClassificationRuleEntity) {
        dao.deleteRule(rule)
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        require(!category.isSystem) { "기본 기타 카테고리는 삭제할 수 없습니다." }
        dao.deleteCategoryAndResetTransactions(category)
    }

    suspend fun assignCategory(
        transaction: TransactionEntity,
        category: CategoryEntity,
        saveRulePhrase: String?,
    ): Int {
        if (!saveRulePhrase.isNullOrBlank()) {
            dao.insertRule(
                ClassificationRuleEntity(
                    categoryId = category.id,
                    phrase = saveRulePhrase.trim(),
                    normalizedPhrase = normalizeMerchantText(saveRulePhrase),
                ),
            )
        }
        dao.updateTransactionCategory(transaction.id, category.name)
        return if (saveRulePhrase.isNullOrBlank()) 0 else reclassifyOther()
    }

    suspend fun reclassifyOther(): Int {
        val patterns = activePatterns()
        if (patterns.isEmpty()) return 0

        val assignments = dao.findOtherTransactions()
            .mapNotNull { transaction ->
                MerchantClassifier.classify(transaction.merchant, patterns)
                    ?.let { categoryName -> transaction.id to categoryName }
            }
        assignments
            .groupBy(keySelector = { it.second }, valueTransform = { it.first })
            .forEach { (categoryName, transactionIds) ->
                if (transactionIds.isNotEmpty()) {
                    dao.updateTransactionCategories(transactionIds, categoryName)
                }
            }
        return assignments.size
    }
}
