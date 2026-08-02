package com.personal.expensecalendar.backup

import android.content.Context
import android.util.Log
import androidx.room.InvalidationTracker
import com.personal.expensecalendar.storage.ExpenseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Watches every persisted app table and coalesces bursts (such as SMS import) into one backup. */
object LocalBackupCoordinator {
    private const val TAG = "ExpenseBackup"
    private const val DEBOUNCE_MILLIS = 800L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var applicationContext: Context? = null
    private var database: ExpenseDatabase? = null
    private var observer: InvalidationTracker.Observer? = null
    private var worker: Job? = null

    @Synchronized
    fun start(context: Context, database: ExpenseDatabase) {
        if (this.database === database && worker?.isActive == true) {
            requestBackup()
            return
        }

        observer?.let { previousObserver ->
            this.database?.invalidationTracker?.removeObserver(previousObserver)
        }
        worker?.cancel()

        applicationContext = context.applicationContext
        this.database = database
        observer = object : InvalidationTracker.Observer(ExpenseDatabase.BACKUP_TABLES) {
            override fun onInvalidated(tables: Set<String>) {
                requestBackup()
            }
        }.also(database.invalidationTracker::addObserver)

        worker = scope.launch {
            for (ignored in requests) {
                delay(DEBOUNCE_MILLIS)
                while (requests.tryReceive().isSuccess) {
                    // Drain changes accumulated during a bulk import.
                }
                performBackup()
            }
        }
        requestBackup()
    }

    fun requestBackup() {
        requests.trySend(Unit)
    }

    fun requestImmediateBackup() {
        scope.launch { performBackup() }
    }

    private suspend fun performBackup() {
        val context = applicationContext ?: return
        val currentDatabase = database ?: return
        if (!LocalBackupManager.hasRequiredStorageAccess()) return
        runCatching {
            LocalBackupManager.createBackup(context, currentDatabase)
        }.onFailure { error ->
            Log.e(TAG, "Automatic local backup failed", error)
        }
    }
}
