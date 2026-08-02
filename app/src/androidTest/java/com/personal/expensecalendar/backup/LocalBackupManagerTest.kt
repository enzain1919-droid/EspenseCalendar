package com.personal.expensecalendar.backup

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.expensecalendar.storage.ExpenseDatabase
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBackupManagerTest {
    private lateinit var testDirectory: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDirectory = File(context.cacheDir, "backup-restore-test-${System.nanoTime()}")
        assertTrue(testDirectory.mkdirs())
    }

    @After
    fun tearDown() {
        testDirectory.deleteRecursively()
    }

    @Test
    fun restoresValidBackupIntoANewDatabaseFile() {
        val backup = File(testDirectory, "backup.db")
        createExpenseDatabase(backup, merchant = "복원 확인", amountWon = 12_345L)
        val target = File(testDirectory, "restored.db")

        val usedSource = LocalBackupManager.restoreBackupToDatabase(target, listOf(backup))

        assertSame(backup, usedSource)
        SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT merchant, amountWon FROM transactions", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("복원 확인", cursor.getString(0))
                assertEquals(12_345L, cursor.getLong(1))
            }
        }
    }

    @Test
    fun fallsBackToPreviousBackupWhenPrimaryIsDamaged() {
        val damagedPrimary = File(testDirectory, "backup.db").apply {
            writeText("not a sqlite database")
        }
        val previous = File(testDirectory, "backup.previous.db")
        createExpenseDatabase(previous, merchant = "이전 백업", amountWon = 54_321L)
        val target = File(testDirectory, "restored.db")

        val usedSource = LocalBackupManager.restoreBackupToDatabase(
            target,
            listOf(damagedPrimary, previous),
        )

        assertSame(previous, usedSource)
        SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT merchant, amountWon FROM transactions", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("이전 백업", cursor.getString(0))
                assertEquals(54_321L, cursor.getLong(1))
            }
        }
    }

    private fun createExpenseDatabase(
        file: File,
        merchant: String,
        amountWon: Long,
    ) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("PRAGMA user_version = ${ExpenseDatabase.SCHEMA_VERSION}")
            db.execSQL(
                "CREATE TABLE transactions (" +
                    "id INTEGER PRIMARY KEY, merchant TEXT NOT NULL, amountWon INTEGER NOT NULL)",
            )
            db.execSQL(
                "INSERT INTO transactions (merchant, amountWon) VALUES (?, ?)",
                arrayOf<Any>(merchant, amountWon),
            )
        }
    }
}
