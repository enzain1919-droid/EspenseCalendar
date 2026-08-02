package com.personal.expensecalendar.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Log
import com.personal.expensecalendar.storage.ExpenseDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface RestoreResult {
    data object ExistingDatabase : RestoreResult
    data object NoBackup : RestoreResult
    data class Restored(val backupPath: String) : RestoreResult
}

data class BackupInfo(
    val path: String,
    val sizeBytes: Long,
    val savedAtMillis: Long,
)

/**
 * Stores a consistent SQLite snapshot outside the app-specific directory so it remains after
 * uninstall. The previous valid snapshot is retained as a fallback for a partially written or
 * damaged primary backup.
 */
object LocalBackupManager {
    const val DATABASE_NAME = "expense-calendar.db"
    const val BACKUP_FOLDER_NAME = "ExpenseCalendar"
    const val BACKUP_FILE_NAME = "expense-calendar-backup.db"
    const val PREVIOUS_BACKUP_FILE_NAME = "expense-calendar-backup.previous.db"

    private const val TAG = "ExpenseBackup"
    private val backupMutex = Mutex()

    fun hasRequiredStorageAccess(): Boolean = Environment.isExternalStorageManager()

    @Suppress("DEPRECATION")
    fun backupDirectory(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        BACKUP_FOLDER_NAME,
    )

    /** Must run before Room opens the database. */
    suspend fun restoreIfPresent(context: Context): RestoreResult = withContext(Dispatchers.IO) {
        require(hasRequiredStorageAccess()) { "저장소 접근 권한이 필요합니다." }

        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        if (databaseFile.exists() && databaseFile.length() > 0L) {
            if (!isValidExpenseDatabase(databaseFile)) {
                error("현재 앱 데이터 파일을 확인할 수 없습니다.")
            }
            return@withContext RestoreResult.ExistingDatabase
        }

        val directory = backupDirectory()
        val candidates = listOf(
            File(directory, BACKUP_FILE_NAME),
            File(directory, PREVIOUS_BACKUP_FILE_NAME),
        )
        val source = restoreBackupToDatabase(databaseFile, candidates)
            ?: return@withContext RestoreResult.NoBackup
        Log.i(TAG, "Restored database from ${source.absolutePath}")
        RestoreResult.Restored(source.absolutePath)
    }

    suspend fun createBackup(
        context: Context,
        database: ExpenseDatabase,
    ): BackupInfo = backupMutex.withLock {
        withContext(Dispatchers.IO) {
            require(hasRequiredStorageAccess()) { "저장소 접근 권한이 필요합니다." }

            val directory = backupDirectory()
            check(directory.exists() || directory.mkdirs()) {
                "백업 폴더를 만들 수 없습니다: ${directory.absolutePath}"
            }

            val cacheSnapshot = File(
                context.cacheDir,
                "expense-calendar-snapshot-${UUID.randomUUID()}.db",
            )
            try {
                createConsistentSnapshot(database, cacheSnapshot)
                check(isValidExpenseDatabase(cacheSnapshot)) { "새 백업 데이터 검증에 실패했습니다." }
                installSnapshot(directory, cacheSnapshot)

                val backup = File(directory, BACKUP_FILE_NAME)
                BackupInfo(
                    path = backup.absolutePath,
                    sizeBytes = backup.length(),
                    savedAtMillis = backup.lastModified(),
                ).also {
                    Log.i(TAG, "Local backup updated: ${it.path} (${it.sizeBytes} bytes)")
                }
            } finally {
                cacheSnapshot.delete()
            }
        }
    }

    private fun createConsistentSnapshot(database: ExpenseDatabase, target: File) {
        target.delete()
        val escapedPath = target.absolutePath.replace("'", "''")
        database.openHelper.writableDatabase.execSQL("VACUUM INTO '$escapedPath'")
    }

    internal fun restoreBackupToDatabase(
        databaseFile: File,
        candidates: List<File>,
    ): File? {
        val existingCandidates = candidates.filter { it.isFile && it.length() > 0L }
        if (existingCandidates.isEmpty()) return null

        val source = existingCandidates.firstOrNull(::isValidExpenseDatabase)
            ?: error("백업 파일이 손상되어 자동 복원할 수 없습니다.")

        databaseFile.parentFile?.mkdirs()
        deleteDatabaseSidecars(databaseFile)
        val restoreTemp = File(
            databaseFile.parentFile,
            ".${databaseFile.name}.restore-${UUID.randomUUID()}",
        )
        try {
            copyAndSync(source, restoreTemp)
            check(isValidExpenseDatabase(restoreTemp)) { "복원 파일 검증에 실패했습니다." }
            replaceFile(restoreTemp, databaseFile)
            return source
        } finally {
            restoreTemp.delete()
        }
    }

    private fun installSnapshot(directory: File, snapshot: File) {
        val current = File(directory, BACKUP_FILE_NAME)
        val previous = File(directory, PREVIOUS_BACKUP_FILE_NAME)
        val nextTemp = File(directory, ".$BACKUP_FILE_NAME.next")
        val previousTemp = File(directory, ".$PREVIOUS_BACKUP_FILE_NAME.next")

        nextTemp.delete()
        previousTemp.delete()
        try {
            copyAndSync(snapshot, nextTemp)
            check(isValidExpenseDatabase(nextTemp)) { "저장된 백업 파일 검증에 실패했습니다." }

            if (current.isFile && current.length() > 0L && isValidExpenseDatabase(current)) {
                copyAndSync(current, previousTemp)
                replaceFile(previousTemp, previous)
            }
            replaceFile(nextTemp, current)
        } finally {
            nextTemp.delete()
            previousTemp.delete()
        }
    }

    private fun isValidExpenseDatabase(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() <= 0L) return@runCatching false
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            val integrityOk = database.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            val schemaVersion = database.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            val hasTransactions = database.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'transactions' LIMIT 1",
                null,
            ).use { cursor -> cursor.moveToFirst() }

            integrityOk && hasTransactions && schemaVersion in 1..ExpenseDatabase.SCHEMA_VERSION
        }
    }.getOrElse {
        Log.w(TAG, "Database validation failed for ${file.absolutePath}", it)
        false
    }

    private fun copyAndSync(source: File, target: File) {
        target.parentFile?.mkdirs()
        FileInputStream(source).channel.use { input ->
            FileOutputStream(target).channel.use { output ->
                var position = 0L
                while (position < input.size()) {
                    position += input.transferTo(position, input.size() - position, output)
                }
                output.force(true)
            }
        }
    }

    private fun replaceFile(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun deleteDatabaseSidecars(databaseFile: File) {
        File(databaseFile.absolutePath + "-wal").delete()
        File(databaseFile.absolutePath + "-shm").delete()
        File(databaseFile.absolutePath + "-journal").delete()
        if (databaseFile.exists() && databaseFile.length() == 0L) databaseFile.delete()
    }
}
