package com.personal.expensecalendar.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deleted_transactions",
    indices = [
        Index(value = ["deletedAtMillis"]),
        Index(value = ["occurredAtMillis"]),
    ],
)
data class DeletedTransactionEntity(
    @PrimaryKey
    val sourceFingerprint: String,
    val sourceSmsId: Long,
    val cardName: String,
    val merchant: String,
    val amountWon: Long,
    val status: String,
    val occurredAtMillis: Long,
    val categoryName: String,
    val majorCategory: String,
    val includedInPerformance: Boolean,
    val performanceOverride: String,
    val includedInExpense: Boolean,
    val source: String,
    val memo: String,
    val transactionType: String,
    val paymentMethod: String,
    val importedAtMillis: Long,
    val deletedAtMillis: Long,
) {
    fun restoreAsTransaction(): TransactionEntity = TransactionEntity(
        sourceSmsId = sourceSmsId,
        sourceFingerprint = sourceFingerprint,
        cardName = cardName,
        merchant = merchant,
        amountWon = amountWon,
        status = status,
        occurredAtMillis = occurredAtMillis,
        categoryName = categoryName,
        majorCategory = majorCategory,
        includedInPerformance = includedInPerformance,
        performanceOverride = performanceOverride,
        includedInExpense = includedInExpense,
        source = source,
        memo = memo,
        transactionType = transactionType,
        paymentMethod = paymentMethod,
        importedAtMillis = importedAtMillis,
    )

    companion object {
        fun from(
            transaction: TransactionEntity,
            deletedAtMillis: Long,
        ): DeletedTransactionEntity = DeletedTransactionEntity(
            sourceFingerprint = transaction.sourceFingerprint,
            sourceSmsId = transaction.sourceSmsId,
            cardName = transaction.cardName,
            merchant = transaction.merchant,
            amountWon = transaction.amountWon,
            status = transaction.status,
            occurredAtMillis = transaction.occurredAtMillis,
            categoryName = transaction.categoryName,
            majorCategory = transaction.majorCategory,
            includedInPerformance = transaction.includedInPerformance,
            performanceOverride = transaction.performanceOverride,
            includedInExpense = transaction.includedInExpense,
            source = transaction.source,
            memo = transaction.memo,
            transactionType = transaction.transactionType,
            paymentMethod = transaction.paymentMethod,
            importedAtMillis = transaction.importedAtMillis,
            deletedAtMillis = deletedAtMillis,
        )
    }
}
