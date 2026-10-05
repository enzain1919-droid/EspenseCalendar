package com.personal.expensecalendar.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvertisementFilteringTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private lateinit var database: ExpenseDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "advertisement-filter-test-${System.nanoTime()}.db"
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun filteredSmsAndMmsAreHiddenButSavedDataAndManualEntriesRemain() = runBlocking {
        val dao = database.transactionDao()
        val transactions = listOf(
            transaction("sms-ad", merchant = "네이버페이"),
            transaction("mms-ad", source = "MMS", merchant = "롯데마트"),
            transaction("payment"),
            transaction("manual", source = "MANUAL", merchant = "광고 제작비"),
        )
        dao.insertAll(transactions)
        val advertisements = listOf(
            AdvertisementSourceEntity("sms-ad"),
            AdvertisementSourceEntity("mms-ad"),
        )
        dao.rememberAdvertisementSources(advertisements)
        dao.rememberAdvertisementSources(advertisements)

        assertEquals(setOf("payment", "manual"), visibleFingerprints())
        assertEquals(2, dao.countAll())
        assertEquals(4, dao.findAllSourceFingerprints().size)
        assertEquals(4, rowCount("transactions"))
        assertEquals(2, rowCount("advertisement_sources"))
    }

    @Test
    fun deletedAdsAreHiddenWithoutLosingTheirRecoverySnapshots() = runBlocking {
        val dao = database.transactionDao()
        listOf(transaction("deleted-ad"), transaction("deleted-payment")).forEach { item ->
            val id = dao.insert(item)
            dao.deleteAndRememberSource(item.copy(id = id))
        }
        dao.rememberAdvertisementSources(listOf(AdvertisementSourceEntity("deleted-ad")))

        assertEquals(
            listOf("deleted-payment"),
            dao.findDeletedTransactions().map { it.sourceFingerprint },
        )
        assertEquals(2, rowCount("deleted_transactions"))
        assertEquals(2, dao.findDeletedSourceFingerprints().size)
    }

    @Test
    fun version14MigrationFiltersIdentifiableAdsAndPreservesAllSavedRows() = runBlocking {
        val dao = database.transactionDao()
        dao.insertAll(
            listOf(
                transaction("old-sms-ad", merchant = "(광고)[롯데카드] 통큰데이"),
                transaction("old-mms-ad", source = "MMS", merchant = "[광고] 할인 안내"),
                transaction("payment"),
                transaction("manual", source = "MANUAL", merchant = "광고 제작비"),
            ),
        )
        dao.rememberDeletedTransaction(
            DeletedTransactionEntity.from(
                transaction("deleted-ad", merchant = "광고 안내"),
                deletedAtMillis = 6_000L,
            ),
        )
        database.close()

        // Version 14 has the same schema except for the new advertisement_sources table.
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.execSQL("DROP TABLE advertisement_sources")
            db.version = 14
        }
        database = openDatabase()

        assertEquals(setOf("payment", "manual"), visibleFingerprints())
        assertTrue(database.transactionDao().findDeletedTransactions().isEmpty())
        assertEquals(4, rowCount("transactions"))
        assertEquals(1, rowCount("deleted_transactions"))
        assertEquals(3, rowCount("advertisement_sources"))
        assertEquals(15, database.openHelper.readableDatabase.version)
    }

    private fun openDatabase(): ExpenseDatabase = Room.databaseBuilder(
        context,
        ExpenseDatabase::class.java,
        databaseName,
    ).addMigrations(ExpenseDatabase.MIGRATION_14_15).build()

    private suspend fun visibleFingerprints(): Set<String> = database.transactionDao()
        .findBetween(0L, 10_000L)
        .map { it.sourceFingerprint }
        .toSet()

    private fun rowCount(table: String): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun transaction(
        fingerprint: String,
        source: String = "SMS",
        merchant: String = "일반 결제",
    ) = TransactionEntity(
        sourceSmsId = 1L,
        sourceFingerprint = fingerprint,
        cardName = "롯데카드",
        merchant = merchant,
        amountWon = 1_000L,
        status = "APPROVED",
        occurredAtMillis = 5_000L,
        importedAtMillis = 5_000L,
        source = source,
    )
}
