package com.personal.expensecalendar.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction

data class CategoryWithRules(
    @Embedded
    val category: CategoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "categoryId",
    )
    val rules: List<ClassificationRuleEntity>,
)

data class ActiveClassificationRule(
    val categoryName: String,
    val phrase: String,
    val normalizedPhrase: String,
)

@Dao
interface CategoryDao {
    @Transaction
    @Query("SELECT * FROM categories ORDER BY isSystem DESC, id")
    suspend fun findAllWithRules(): List<CategoryWithRules>

    @Query("SELECT * FROM categories ORDER BY isSystem DESC, id")
    suspend fun findAll(): List<CategoryEntity>

    @Query(
        "SELECT categories.name AS categoryName, " +
            "classification_rules.phrase AS phrase, " +
            "classification_rules.normalizedPhrase AS normalizedPhrase " +
            "FROM classification_rules " +
            "INNER JOIN categories ON categories.id = classification_rules.categoryId " +
            "WHERE classification_rules.isActive = 1 " +
            "ORDER BY classification_rules.id",
    )
    suspend fun findActiveRules(): List<ActiveClassificationRule>

    @Query("SELECT * FROM transactions WHERE categoryName = '기타'")
    suspend fun findOtherTransactions(): List<TransactionEntity>

    @Query("UPDATE transactions SET categoryName = :categoryName WHERE id IN (:transactionIds)")
    suspend fun updateTransactionCategories(
        transactionIds: List<Long>,
        categoryName: String,
    )

    @Query("UPDATE transactions SET categoryName = :categoryName WHERE id = :transactionId")
    suspend fun updateTransactionCategory(
        transactionId: Long,
        categoryName: String,
    )

    @Query("UPDATE transactions SET categoryName = '기타' WHERE categoryName = :categoryName")
    suspend fun resetTransactionsForDeletedCategory(categoryName: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRule(rule: ClassificationRuleEntity): Long

    @Delete
    suspend fun deleteRule(rule: ClassificationRuleEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Transaction
    suspend fun deleteCategoryAndResetTransactions(category: CategoryEntity) {
        resetTransactionsForDeletedCategory(category.name)
        deleteCategory(category)
    }
}
