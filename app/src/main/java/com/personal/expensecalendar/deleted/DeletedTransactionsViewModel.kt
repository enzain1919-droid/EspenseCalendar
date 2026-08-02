package com.personal.expensecalendar.deleted

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.expensecalendar.storage.DeletedTransactionEntity
import com.personal.expensecalendar.storage.ExpenseDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeletedTransactionsUiState(
    val isLoading: Boolean = true,
    val items: List<DeletedTransactionEntity> = emptyList(),
    val processingFingerprints: Set<String> = emptySet(),
    val actionMessage: String? = null,
    val errorMessage: String? = null,
)

class DeletedTransactionsViewModel(application: Application) : AndroidViewModel(application) {
    private val transactionDao = ExpenseDatabase.getInstance(application).transactionDao()

    private val _uiState = MutableStateFlow(DeletedTransactionsUiState())
    val uiState: StateFlow<DeletedTransactionsUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { transactionDao.findDeletedTransactions() }
            }.onSuccess { items ->
                _uiState.update { it.copy(isLoading = false, items = items) }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "삭제 내역을 불러오지 못했습니다.",
                    )
                }
            }
        }
    }

    fun restore(transaction: DeletedTransactionEntity) {
        val fingerprint = transaction.sourceFingerprint
        if (fingerprint in _uiState.value.processingFingerprints) return
        _uiState.update {
            it.copy(
                processingFingerprints = it.processingFingerprints + fingerprint,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    transactionDao.restoreDeletedTransaction(transaction)
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        items = it.items.filterNot { item ->
                            item.sourceFingerprint == fingerprint
                        },
                        processingFingerprints = it.processingFingerprints - fingerprint,
                        actionMessage = "${transaction.merchant} 내역을 복구했습니다.",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        processingFingerprints = it.processingFingerprints - fingerprint,
                        errorMessage = error.message ?: "내역을 복구하지 못했습니다.",
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
