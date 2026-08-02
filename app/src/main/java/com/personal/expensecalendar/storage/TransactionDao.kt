package com.personal.expensecalendar.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query(
        "SELECT * FROM transactions " +
            "WHERE occurredAtMillis >= :startInclusiveMillis " +
            "AND occurredAtMillis < :endExclusiveMillis " +
            "ORDER BY occurredAtMillis DESC",
    )
    suspend fun findBetween(
        startInclusiveMillis: Long,
        endExclusiveMillis: Long,
    ): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun rememberDeletedSource(deletedSource: DeletedSourceEntity): Long

    @Query("SELECT sourceFingerprint FROM deleted_sources")
    suspend fun findDeletedSourceFingerprints(): List<String>

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Transaction
    suspend fun deleteAndRememberSource(transaction: TransactionEntity) {
        if (transaction.source != "MANUAL") {
            rememberDeletedSource(
                DeletedSourceEntity(
                    sourceFingerprint = transaction.sourceFingerprint,
                    deletedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
        delete(transaction)
    }
}
