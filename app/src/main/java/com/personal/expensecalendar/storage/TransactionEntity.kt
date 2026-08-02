package com.personal.expensecalendar.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["sourceFingerprint"], unique = true),
        Index(value = ["occurredAtMillis"]),
        Index(value = ["cardName"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceSmsId: Long,
    val sourceFingerprint: String,
    val cardName: String,
    val merchant: String,
    val amountWon: Long,
    val status: String,
    val occurredAtMillis: Long,
    val categoryName: String = "기타",
    @ColumnInfo(defaultValue = "'LIVING_EXPENSE'")
    val majorCategory: String = MajorCategory.LIVING_EXPENSE.name,
    val includedInPerformance: Boolean = true,
    @ColumnInfo(defaultValue = "'AUTO'")
    val performanceOverride: String = PerformanceOverride.AUTO.name,
    @ColumnInfo(defaultValue = "1")
    val includedInExpense: Boolean = true,
    @ColumnInfo(defaultValue = "'SMS'")
    val source: String = "SMS",
    @ColumnInfo(defaultValue = "''")
    val memo: String = "",
    @ColumnInfo(defaultValue = "'EXPENSE'")
    val transactionType: String = TransactionType.EXPENSE.name,
    @ColumnInfo(defaultValue = "'CARD'")
    val paymentMethod: String = PaymentMethod.CARD.name,
    val importedAtMillis: Long,
)
