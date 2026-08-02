package com.personal.expensecalendar.cards

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.expensecalendar.categories.normalizeMerchantText
import com.personal.expensecalendar.sms.CardRuleNormalizer
import com.personal.expensecalendar.storage.CardDetectionRuleEntity
import com.personal.expensecalendar.storage.CardPerformanceExclusionEntity
import com.personal.expensecalendar.storage.CardPerformanceTierEntity
import com.personal.expensecalendar.storage.CardProfileEntity
import com.personal.expensecalendar.storage.CardProfileWithRules
import com.personal.expensecalendar.storage.ExpenseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CardManagementUiState(
    val cards: List<CardProfileWithRules> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class CardManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ExpenseDatabase.getInstance(application).cardProfileDao()

    private val _uiState = MutableStateFlow(CardManagementUiState())
    val uiState: StateFlow<CardManagementUiState> = _uiState.asStateFlow()

    init {
        loadCards()
    }

    fun addCard(
        displayName: String,
        detectionPhrase: String,
    ) {
        val cleanName = displayName.trim()
        val cleanPhrase = detectionPhrase.trim()
        if (cleanName.isBlank() || cleanPhrase.isBlank()) {
            showError("카드 이름과 식별 문장을 모두 입력해 주세요.")
            return
        }
        performMutation {
            dao.insertCardWithRule(
                card = CardProfileEntity(
                    displayName = cleanName,
                    normalizedName = CardRuleNormalizer.normalize(cleanName),
                ),
                phrase = cleanPhrase,
                normalizedPhrase = CardRuleNormalizer.normalize(cleanPhrase),
            )
        }
    }

    fun addRule(
        cardId: Long,
        phrase: String,
    ) {
        val cleanPhrase = phrase.trim()
        if (cleanPhrase.isBlank()) {
            showError("식별 문장을 입력해 주세요.")
            return
        }
        performMutation {
            dao.insertRule(
                CardDetectionRuleEntity(
                    cardProfileId = cardId,
                    phrase = cleanPhrase,
                    normalizedPhrase = CardRuleNormalizer.normalize(cleanPhrase),
                ),
            )
        }
    }

    fun deleteRule(rule: CardDetectionRuleEntity) {
        performMutation { dao.deleteRule(rule) }
    }

    fun deleteCard(card: CardProfileEntity) {
        performMutation { dao.deleteCard(card) }
    }

    fun addPerformanceTier(
        cardId: Long,
        minimumSpendWon: Long,
        benefitWon: Long,
    ) {
        if (minimumSpendWon < 0L || benefitWon < 0L) {
            showError("실적 금액과 혜택 금액은 0원 이상이어야 합니다.")
            return
        }
        performMutation("같은 실적 금액의 구간이 이미 등록되어 있는지 확인해 주세요.") {
            dao.insertPerformanceTier(
                CardPerformanceTierEntity(
                    cardProfileId = cardId,
                    minimumSpendWon = minimumSpendWon,
                    benefitWon = benefitWon,
                ),
            )
        }
    }

    fun deletePerformanceTier(tier: CardPerformanceTierEntity) {
        performMutation { dao.deletePerformanceTier(tier) }
    }

    fun addPerformanceExclusion(
        cardId: Long,
        phrase: String,
    ) {
        val cleanPhrase = phrase.trim()
        val normalizedPhrase = normalizeMerchantText(cleanPhrase)
        if (normalizedPhrase.isBlank()) {
            showError("실적에서 제외할 문장을 입력해 주세요.")
            return
        }
        performMutation("같은 실적 제외 문장이 이미 등록되어 있는지 확인해 주세요.") {
            dao.insertPerformanceExclusion(
                CardPerformanceExclusionEntity(
                    cardProfileId = cardId,
                    phrase = cleanPhrase,
                    normalizedPhrase = normalizedPhrase,
                ),
            )
        }
    }

    fun deletePerformanceExclusion(exclusion: CardPerformanceExclusionEntity) {
        performMutation { dao.deletePerformanceExclusion(exclusion) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun loadCards() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { dao.findAllWithRules() }
            }.onSuccess { cards ->
                _uiState.value = CardManagementUiState(
                    cards = cards,
                    isLoading = false,
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "카드 목록을 불러오지 못했습니다.",
                    )
                }
            }
        }
    }

    private fun performMutation(
        failureMessage: String = "이미 등록된 카드 이름 또는 문장인지 확인해 주세요.",
        action: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { action() }
            }.onSuccess {
                loadCards()
            }.onFailure {
                showError(failureMessage)
            }
        }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }
}
