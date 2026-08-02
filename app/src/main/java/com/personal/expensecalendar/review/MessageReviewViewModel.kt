package com.personal.expensecalendar.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.expensecalendar.categories.CategoryRepository
import com.personal.expensecalendar.categories.MerchantClassifier
import com.personal.expensecalendar.dashboard.TransactionEditInput
import com.personal.expensecalendar.dashboard.TransactionEditor
import com.personal.expensecalendar.sms.MessageReviewScanResult
import com.personal.expensecalendar.sms.MonthRangeFactory
import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.sms.SmsImportRepository
import com.personal.expensecalendar.sms.SmsRepository
import com.personal.expensecalendar.sms.UnparsedPaymentMessage
import com.personal.expensecalendar.storage.CategoryEntity
import com.personal.expensecalendar.storage.DeletedSourceEntity
import com.personal.expensecalendar.storage.ExpenseDatabase
import com.personal.expensecalendar.storage.MajorCategory
import com.personal.expensecalendar.storage.PaymentMethod
import com.personal.expensecalendar.storage.PerformanceOverride
import com.personal.expensecalendar.storage.TransactionEntity
import com.personal.expensecalendar.storage.TransactionType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MessageReviewItem(
    val message: UnparsedPaymentMessage,
    val suggestedCategoryName: String,
)

data class MessageReviewUiState(
    val yearMonth: YearMonth = YearMonth.now(),
    val isLoading: Boolean = true,
    val scannedCount: Int = 0,
    val candidateCount: Int = 0,
    val items: List<MessageReviewItem> = emptyList(),
    val cardNames: List<String> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val processingFingerprints: Set<String> = emptySet(),
    val actionMessage: String? = null,
    val errorMessage: String? = null,
)

class MessageReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val zoneId = ZoneId.systemDefault()
    private val database = ExpenseDatabase.getInstance(application)
    private val transactionDao = database.transactionDao()
    private val categoryDao = database.categoryDao()
    private val cardProfileDao = database.cardProfileDao()
    private val categoryRepository = CategoryRepository(categoryDao)
    private val importer = SmsImportRepository(
        smsRepository = SmsRepository(application.contentResolver),
        transactionDao = transactionDao,
        cardProfileDao = cardProfileDao,
        categoryDao = categoryDao,
    )
    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow(MessageReviewUiState())
    val uiState: StateFlow<MessageReviewUiState> = _uiState.asStateFlow()

    fun load(yearMonth: YearMonth) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                yearMonth = yearMonth,
                isLoading = true,
                actionMessage = null,
                errorMessage = null,
            )
        }
        loadJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val scan = importer.findUnparsedMessages(
                        MonthRangeFactory.forMonth(yearMonth, zoneId),
                    )
                    val categories = categoryDao.findAll()
                    val categoryPatterns = categoryRepository.activePatterns()
                    val cardNames = cardProfileDao.findAllWithRules()
                        .filter { it.card.isActive }
                        .map { it.card.displayName }
                    LoadedReviewData(
                        scan = scan,
                        items = scan.messages.map { message ->
                            MessageReviewItem(
                                message = message,
                                suggestedCategoryName = MerchantClassifier.classify(
                                    merchant = message.draft.merchant,
                                    patterns = categoryPatterns,
                                ) ?: "기타",
                            )
                        },
                        categories = categories,
                        cardNames = cardNames,
                    )
                }
            }.onSuccess { data ->
                if (_uiState.value.yearMonth == yearMonth) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            scannedCount = data.scan.scannedCount,
                            candidateCount = data.scan.candidateCount,
                            items = data.items,
                            cardNames = data.cardNames,
                            categories = data.categories,
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = if (error is SecurityException) {
                            "검토함을 확인하려면 문자 접근 권한이 필요합니다."
                        } else {
                            error.message ?: "분석 실패 문자를 불러오지 못했습니다."
                        },
                    )
                }
            }
        }
    }

    fun createTransactionDraft(item: MessageReviewItem): TransactionEntity {
        val message = item.message
        return TransactionEntity(
            id = message.sourceFingerprint.hashCode().toLong(),
            sourceSmsId = message.record.id,
            sourceFingerprint = message.sourceFingerprint,
            cardName = message.draft.cardName.orEmpty(),
            merchant = message.draft.merchant,
            amountWon = message.draft.amountWon ?: 0L,
            status = message.draft.status.name,
            occurredAtMillis = message.draft.occurredAtMillis,
            categoryName = item.suggestedCategoryName,
            majorCategory = MajorCategory.LIVING_EXPENSE.name,
            includedInPerformance = true,
            performanceOverride = PerformanceOverride.AUTO.name,
            source = message.record.transport.name,
            memo = "",
            transactionType = TransactionType.EXPENSE.name,
            paymentMethod = PaymentMethod.CARD.name,
            importedAtMillis = Instant.now().toEpochMilli(),
        )
    }

    fun registerMessage(
        item: MessageReviewItem,
        input: TransactionEditInput,
    ) {
        TransactionEditor.validationError(input)?.let { message ->
            _uiState.update { it.copy(errorMessage = message) }
            return
        }
        mutate(item.message.sourceFingerprint) {
            val transaction = TransactionEditor.apply(
                original = createTransactionDraft(item).copy(id = 0),
                input = input,
                zoneId = zoneId,
            )
            val inserted = transactionDao.insertAll(listOf(transaction)).single() != -1L
            if (inserted) "검토한 문자를 생활비 내역에 등록했습니다." else "이미 등록된 문자입니다."
        }
    }

    fun ignoreMessage(item: MessageReviewItem) {
        mutate(item.message.sourceFingerprint) {
            transactionDao.rememberDeletedSource(
                DeletedSourceEntity(
                    sourceFingerprint = item.message.sourceFingerprint,
                    deletedAtMillis = System.currentTimeMillis(),
                ),
            )
            "이 문자는 앞으로 검토함과 자동 가져오기에서 제외됩니다."
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSensitiveMessages() {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = false,
                items = emptyList(),
                scannedCount = 0,
                candidateCount = 0,
                processingFingerprints = emptySet(),
            )
        }
    }

    private fun mutate(
        fingerprint: String,
        action: suspend () -> String,
    ) {
        if (fingerprint in _uiState.value.processingFingerprints) return
        _uiState.update {
            it.copy(
                processingFingerprints = it.processingFingerprints + fingerprint,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { action() }
            }.onSuccess { message ->
                _uiState.update {
                    it.copy(
                        items = it.items.filterNot { item ->
                            item.message.sourceFingerprint == fingerprint
                        },
                        processingFingerprints = it.processingFingerprints - fingerprint,
                        actionMessage = message,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        processingFingerprints = it.processingFingerprints - fingerprint,
                        errorMessage = error.message ?: "검토 항목을 처리하지 못했습니다.",
                    )
                }
            }
        }
    }
}

private data class LoadedReviewData(
    val scan: MessageReviewScanResult,
    val items: List<MessageReviewItem>,
    val categories: List<CategoryEntity>,
    val cardNames: List<String>,
)
