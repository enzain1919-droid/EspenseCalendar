package com.personal.expensecalendar.sms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.expensecalendar.storage.ExpenseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SmsVerificationUiState {
    data object Idle : SmsVerificationUiState
    data class Loading(val monthName: String) : SmsVerificationUiState
    data class Loaded(val result: SmsImportResult) : SmsVerificationUiState
    data class Error(val message: String) : SmsVerificationUiState
}

class SmsVerificationViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ExpenseDatabase.getInstance(application)
    private val repository = SmsImportRepository(
        smsRepository = SmsRepository(application.contentResolver),
        transactionDao = database.transactionDao(),
        cardProfileDao = database.cardProfileDao(),
        categoryDao = database.categoryDao(),
    )

    private val _uiState = MutableStateFlow<SmsVerificationUiState>(SmsVerificationUiState.Idle)
    val uiState: StateFlow<SmsVerificationUiState> = _uiState.asStateFlow()

    fun scanCurrentMonth() {
        if (_uiState.value is SmsVerificationUiState.Loading) return

        val range = MonthRangeFactory.current()
        _uiState.value = SmsVerificationUiState.Loading(range.displayName)
        viewModelScope.launch {
            _uiState.value = runCatching {
                withContext(Dispatchers.IO) {
                    repository.importCurrentMonth(range)
                }
            }.fold(
                onSuccess = SmsVerificationUiState::Loaded,
                onFailure = { error ->
                    SmsVerificationUiState.Error(
                        message = error.message ?: "문자를 가져오는 중 알 수 없는 오류가 발생했습니다.",
                    )
                },
            )
        }
    }
}
