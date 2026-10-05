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
            "AND sourceFingerprint NOT IN (SELECT sourceFingerprint FROM advertisement_sources) " +
            "ORDER BY occurredAtMillis DESC",
    )
    suspend fun findBetween(
        startInclusiveMillis: Long,
        endExclusiveMillis: Long,
    ): List<TransactionEntity>

    @Query(
        "SELECT COUNT(*) FROM transactions WHERE sourceFingerprint NOT IN " +
            "(SELECT sourceFingerprint FROM advertisement_sources)",
    )
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun rememberAdvertisementSources(sources: List<AdvertisementSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun rememberDeletedSource(deletedSource: DeletedSourceEntity): Long

    @Query("SELECT sourceFingerprint FROM deleted_sources")
    suspend fun findDeletedSourceFingerprints(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun rememberDeletedTransaction(transaction: DeletedTransactionEntity): Long

    @Query(
        "SELECT * FROM deleted_transactions WHERE sourceFingerprint NOT IN " +
            "(SELECT sourceFingerprint FROM advertisement_sources) ORDER BY deletedAtMillis DESC",
    )
    suspend fun findDeletedTransactions(): List<DeletedTransactionEntity>

    @Query("DELETE FROM deleted_transactions WHERE sourceFingerprint = :sourceFingerprint")
    suspend fun forgetDeletedTransaction(sourceFingerprint: String)

    @Query("DELETE FROM deleted_sources WHERE sourceFingerprint = :sourceFingerprint")
    suspend fun forgetDeletedSource(sourceFingerprint: String)

    @Query("SELECT sourceFingerprint FROM transactions")
    suspend fun findAllSourceFingerprints(): List<String>

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Transaction
    suspend fun deleteAndRememberSource(transaction: TransactionEntity) {
        val deletedAtMillis = System.currentTimeMillis()
        rememberDeletedTransaction(
            DeletedTransactionEntity.from(
                transaction = transaction,
                deletedAtMillis = deletedAtMillis,
            ),
        )
        if (transaction.source != "MANUAL") {
            rememberDeletedSource(
                DeletedSourceEntity(
                    sourceFingerprint = transaction.sourceFingerprint,
                    deletedAtMillis = deletedAtMillis,
                ),
            )
        }
        delete(transaction)
    }

    @Transaction
    suspend fun restoreDeletedTransaction(transaction: DeletedTransactionEntity) {
        insert(transaction.restoreAsTransaction())
        forgetDeletedTransaction(transaction.sourceFingerprint)
        forgetDeletedSource(transaction.sourceFingerprint)
    }
}
