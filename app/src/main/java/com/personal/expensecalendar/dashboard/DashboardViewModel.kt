package com.personal.expensecalendar.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.expensecalendar.sms.MonthRangeFactory
import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.sms.SmsImportRepository
import com.personal.expensecalendar.sms.SmsImportResult
import com.personal.expensecalendar.sms.SmsRepository
import com.personal.expensecalendar.categories.CategoryRepository
import com.personal.expensecalendar.categories.MerchantClassifier
import com.personal.expensecalendar.cards.CardPerformanceCalculator
import com.personal.expensecalendar.cards.CardPerformanceSummary
import com.personal.expensecalendar.storage.CategoryEntity
import com.personal.expensecalendar.storage.ExpenseDatabase
import com.personal.expensecalendar.storage.MonthlyBudgetEntity
import com.personal.expensecalendar.storage.MajorCategory
import com.personal.expensecalendar.storage.PaymentMethod
import com.personal.expensecalendar.storage.TransactionEntity
import com.personal.expensecalendar.storage.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardUiState(
    val displayedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val monthTransactions: List<TransactionEntity> = emptyList(),
    val daySummaries: Map<LocalDate, DailyExpenseSummary> = emptyMap(),
    val monthlySpentWon: Long = 0,
    val monthlyIncomeWon: Long = 0,
    val budgetAmountWon: Long? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val cardPerformance: List<CardPerformanceSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val lastImportResult: SmsImportResult? = null,
    val errorMessage: String? = null,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val zoneId = ZoneId.systemDefault()
    private val database = ExpenseDatabase.getInstance(application)
    private val transactionDao = database.transactionDao()
    private val budgetDao = database.monthlyBudgetDao()
    private val categoryDao = database.categoryDao()
    private val cardProfileDao = database.cardProfileDao()
    private val categoryRepository = CategoryRepository(categoryDao)
    private val smsImporter = SmsImportRepository(
        smsRepository = SmsRepository(application.contentResolver),
        transactionDao = transactionDao,
        cardProfileDao = cardProfileDao,
        categoryDao = database.categoryDao(),
    )
    private var loadJob: Job? = null
    private val automaticallySyncedMonths = mutableSetOf<YearMonth>()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadMonth(
            yearMonth = YearMonth.now(zoneId),
            selectedDate = LocalDate.now(zoneId),
        )
    }

    fun showPreviousMonth() = changeMonth(_uiState.value.displayedMonth.minusMonths(1))

    fun showNextMonth() = changeMonth(_uiState.value.displayedMonth.plusMonths(1))

    fun showCurrentMonth() {
        val today = LocalDate.now(zoneId)
        loadMonth(YearMonth.from(today), today)
    }

    fun selectDate(date: LocalDate) {
        val targetMonth = YearMonth.from(date)
        if (targetMonth != _uiState.value.displayedMonth) {
            loadMonth(targetMonth, date)
        } else {
            _uiState.update { it.copy(selectedDate = date) }
        }
    }

    fun setBudget(amountWon: Long) {
        val targetMonth = _uiState.value.displayedMonth
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    budgetDao.upsert(
                        MonthlyBudgetEntity(
                            yearMonth = targetMonth.toString(),
                            amountWon = amountWon.coerceAtLeast(0),
                        ),
                    )
                }
            }.onSuccess {
                if (_uiState.value.displayedMonth == targetMonth) {
                    _uiState.update { it.copy(budgetAmountWon = amountWon.coerceAtLeast(0)) }
                }
            }.onFailure(::showError)
        }
    }

    fun addManualTransaction(
        merchant: String,
        amountWon: Long,
        memo: String,
        transactionType: TransactionType,
        paymentMethod: PaymentMethod,
        cardName: String?,
    ) {
        val selectedDate = _uiState.value.selectedDate
        val targetMonth = YearMonth.from(selectedDate)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val occurredAt = selectedDate.atTime(12, 0)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli()
                    val categoryName = MerchantClassifier.classify(
                        merchant = merchant,
                        patterns = categoryRepository.activePatterns(),
                    ) ?: "기타"
                    transactionDao.insert(
                        TransactionEntity(
                            sourceSmsId = -1,
                            sourceFingerprint = "manual:${UUID.randomUUID()}",
                            cardName = if (paymentMethod == PaymentMethod.CASH) {
                                "현금"
                            } else {
                                cardName ?: "수동 카드"
                            },
                            merchant = merchant.ifBlank { "직접 입력" },
                            amountWon = amountWon,
                            status = PaymentStatus.APPROVED.name,
                            occurredAtMillis = occurredAt,
                            categoryName = categoryName,
                            majorCategory = MajorCategory.LIVING_EXPENSE.name,
                            source = "MANUAL",
                            memo = memo,
                            transactionType = transactionType.name,
                            paymentMethod = paymentMethod.name,
                            importedAtMillis = Instant.now().toEpochMilli(),
                        ),
                    )
                }
            }.onSuccess {
                loadMonth(targetMonth, selectedDate)
            }.onFailure(::showError)
        }
    }

    fun importDisplayedMonth() {
        if (_uiState.value.isImporting) return

        val targetMonth = _uiState.value.displayedMonth
        val selectedDate = _uiState.value.selectedDate
        loadJob?.cancel()
        _uiState.update { it.copy(isImporting = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val range = MonthRangeFactory.forMonth(targetMonth, zoneId)
                    val result = smsImporter.importCurrentMonth(range)
                    categoryRepository.reclassifyOther()
                    val transactions = transactionDao.findBetween(
                        range.startInclusiveMillis,
                        range.endExclusiveMillis,
                    )
                    ImportedMonthDashboardData(
                        result = result,
                        transactions = transactions,
                        budget = budgetDao.findEffective(targetMonth.toString()),
                        categories = categoryDao.findAll(),
                        cards = cardProfileDao.findAllWithRules(),
                    )
                }
            }.onSuccess { data ->
                if (_uiState.value.displayedMonth == targetMonth) {
                    _uiState.update {
                        it.copy(
                            selectedDate = selectedDate,
                            monthTransactions = data.transactions,
                            daySummaries = ExpenseAggregation.byDay(data.transactions, zoneId),
                            monthlySpentWon = ExpenseAggregation.monthlyTotal(data.transactions),
                            monthlyIncomeWon = ExpenseAggregation.monthlyIncome(data.transactions),
                            budgetAmountWon = data.budget?.amountWon,
                            categories = data.categories,
                            cardPerformance = CardPerformanceCalculator.summarize(
                                cards = data.cards,
                                transactions = data.transactions,
                            ),
                            isLoading = false,
                            isImporting = false,
                            lastImportResult = data.result,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isImporting = false) }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isImporting = false) }
                showError(error)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun refreshDisplayedMonth() {
        val state = _uiState.value
        loadMonth(state.displayedMonth, state.selectedDate)
    }

    fun claimAutomaticSync(yearMonth: YearMonth): Boolean {
        if (_uiState.value.isImporting) return false
        return automaticallySyncedMonths.add(yearMonth)
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        val targetMonth = _uiState.value.displayedMonth
        val selectedDate = _uiState.value.selectedDate
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    transactionDao.deleteAndRememberSource(transaction)
                }
            }.onSuccess {
                loadMonth(targetMonth, selectedDate)
            }.onFailure(::showError)
        }
    }

    fun updateTransaction(
        transaction: TransactionEntity,
        input: TransactionEditInput,
    ) {
        TransactionEditor.validationError(input)?.let { message ->
            showError(IllegalArgumentException(message))
            return
        }

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
                loadMonth(YearMonth.from(input.date), input.date)
            }.onFailure(::showError)
        }
    }

    fun assignCategory(
        transaction: TransactionEntity,
        category: CategoryEntity,
        saveRule: Boolean,
        rulePhrase: String,
    ) {
        val targetMonth = _uiState.value.displayedMonth
        val selectedDate = _uiState.value.selectedDate
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    categoryRepository.assignCategory(
                        transaction = transaction,
                        category = category,
                        saveRulePhrase = rulePhrase.takeIf { saveRule },
                    )
                }
            }.onSuccess {
                loadMonth(targetMonth, selectedDate)
            }.onFailure {
                showError(IllegalStateException("이미 등록된 분류 문구인지 확인해 주세요."))
            }
        }
    }

    private fun changeMonth(yearMonth: YearMonth) {
        val today = LocalDate.now(zoneId)
        val selectedDate = if (YearMonth.from(today) == yearMonth) {
            today
        } else {
            yearMonth.atDay(1)
        }
        loadMonth(yearMonth, selectedDate)
    }

    private fun loadMonth(
        yearMonth: YearMonth,
        selectedDate: LocalDate,
    ) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                displayedMonth = yearMonth,
                selectedDate = selectedDate,
                isLoading = true,
                lastImportResult = null,
                errorMessage = null,
            )
        }
        loadJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    categoryRepository.reclassifyOther()
                    val range = MonthRangeFactory.forMonth(yearMonth, zoneId)
                    val transactions = transactionDao.findBetween(
                        range.startInclusiveMillis,
                        range.endExclusiveMillis,
                    )
                    val budget = budgetDao.findEffective(yearMonth.toString())
                    val categories = categoryDao.findAll()
                    val cards = cardProfileDao.findAllWithRules()
                    MonthDashboardData(transactions, budget, categories, cards)
                }
            }.onSuccess { data ->
                if (_uiState.value.displayedMonth == yearMonth) {
                    _uiState.update {
                        it.copy(
                            monthTransactions = data.transactions,
                            daySummaries = ExpenseAggregation.byDay(data.transactions, zoneId),
                            monthlySpentWon = ExpenseAggregation.monthlyTotal(data.transactions),
                            monthlyIncomeWon = ExpenseAggregation.monthlyIncome(data.transactions),
                            budgetAmountWon = data.budget?.amountWon,
                            categories = data.categories,
                            cardPerformance = CardPerformanceCalculator.summarize(
                                cards = data.cards,
                                transactions = data.transactions,
                            ),
                            isLoading = false,
                        )
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _uiState.update { it.copy(isLoading = false) }
                showError(error)
            }
        }
    }

    private fun showError(error: Throwable) {
        if (error is CancellationException) return
        _uiState.update {
            it.copy(errorMessage = error.message ?: "처리 중 오류가 발생했습니다.")
        }
    }
}

private data class MonthDashboardData(
    val transactions: List<TransactionEntity>,
    val budget: MonthlyBudgetEntity?,
    val categories: List<CategoryEntity>,
    val cards: List<com.personal.expensecalendar.storage.CardProfileWithRules>,
)

private data class ImportedMonthDashboardData(
    val result: SmsImportResult,
    val transactions: List<TransactionEntity>,
    val budget: MonthlyBudgetEntity?,
    val categories: List<CategoryEntity>,
    val cards: List<com.personal.expensecalendar.storage.CardProfileWithRules>,
)
