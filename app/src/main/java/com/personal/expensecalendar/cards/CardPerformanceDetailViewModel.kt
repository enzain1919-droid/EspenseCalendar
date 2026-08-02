package com.personal.expensecalendar.cards

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.expensecalendar.dashboard.TransactionEditInput
import com.personal.expensecalendar.dashboard.TransactionEditor
import com.personal.expensecalendar.sms.CardRuleNormalizer
import com.personal.expensecalendar.sms.MonthRangeFactory
import com.personal.expensecalendar.storage.ExpenseDatabase
import com.personal.expensecalendar.storage.CategoryEntity
import com.personal.expensecalendar.storage.PaymentMethod
import com.personal.expensecalendar.storage.TransactionEntity
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CardPerformanceTransactionItem(
    val transaction: TransactionEntity,
    val inclusion: CardPerformanceInclusion,
)

data class CardPerformanceDetailUiState(
    val cardName: String = "",
    val yearMonth: YearMonth = YearMonth.now(),
    val summary: CardPerformanceSummary? = null,
    val transactions: List<CardPerformanceTransactionItem> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val cardNames: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class CardPerformanceDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ExpenseDatabase.getInstance(application)
    private val transactionDao = database.transactionDao()
    private val cardProfileDao = database.cardProfileDao()
    private val categoryDao = database.categoryDao()
    private val zoneId = ZoneId.systemDefault()
    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow(CardPerformanceDetailUiState())
    val uiState: StateFlow<CardPerformanceDetailUiState> = _uiState.asStateFlow()

    fun load(
        cardName: String,
        yearMonth: YearMonth,
    ) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                cardName = cardName,
                yearMonth = yearMonth,
                isLoading = true,
                errorMessage = null,
            )
        }
        loadJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val range = MonthRangeFactory.forMonth(yearMonth, zoneId)
                    val transactions = transactionDao.findBetween(
                        range.startInclusiveMillis,
                        range.endExclusiveMillis,
                    )
                    val normalizedCardName = CardRuleNormalizer.normalize(cardName)
                    val cards = cardProfileDao.findAllWithRules()
                    val card = cards.firstOrNull {
                        CardRuleNormalizer.normalize(it.card.displayName) == normalizedCardName
                    } ?: error("카드 설정을 찾을 수 없습니다.")
                    val cardTransactions = transactions
                        .filter {
                            it.paymentMethod == PaymentMethod.CARD.name &&
                                CardRuleNormalizer.normalize(it.cardName) == normalizedCardName
                        }
                        .sortedByDescending(TransactionEntity::occurredAtMillis)
                    val summary = CardPerformanceCalculator
                        .summarize(listOf(card), transactions)
                        .single()
                    CardPerformanceDetailData(
                        summary = summary,
                        transactions = cardTransactions.map { transaction ->
                            CardPerformanceTransactionItem(
                                transaction = transaction,
                                inclusion = CardPerformanceCalculator.inclusionFor(card, transaction),
                            )
                        },
                        categories = categoryDao.findAll(),
                        cardNames = cards
                            .filter { it.card.isActive }
                            .map { it.card.displayName },
                    )
                }
            }.onSuccess { data ->
                _uiState.update {
                    it.copy(
                        summary = data.summary,
                        transactions = data.transactions,
                        categories = data.categories,
                        cardNames = data.cardNames,
                        isLoading = false,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "카드 이용내역을 불러오지 못했습니다.",
                    )
                }
            }
        }
    }

    fun updateTransaction(
        transaction: TransactionEntity,
        input: TransactionEditInput,
    ) {
        TransactionEditor.validationError(input)?.let { message ->
            _uiState.update { it.copy(errorMessage = message) }
            return
        }
        val cardName = _uiState.value.cardName
        val yearMonth = _uiState.value.yearMonth
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    transactionDao.update(
                        TransactionEditor.apply(
                            original = transaction,
                            input = input,
                            zoneId = zoneId,
                        ),
                    )
                }
            }.onSuccess {
                load(cardName, yearMonth)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "이용내역을 수정하지 못했습니다.")
                }
            }
        }
    }
}

private data class CardPerformanceDetailData(
    val summary: CardPerformanceSummary,
    val transactions: List<CardPerformanceTransactionItem>,
    val categories: List<CategoryEntity>,
    val cardNames: List<String>,
)
