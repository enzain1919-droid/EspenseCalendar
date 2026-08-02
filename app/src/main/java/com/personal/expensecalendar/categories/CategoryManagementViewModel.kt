package com.personal.expensecalendar.categories

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.expensecalendar.storage.CategoryEntity
import com.personal.expensecalendar.storage.CategoryWithRules
import com.personal.expensecalendar.storage.ClassificationRuleEntity
import com.personal.expensecalendar.storage.ExpenseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CategoryManagementUiState(
    val categories: List<CategoryWithRules> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val resultMessage: String? = null,
)

class CategoryManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ExpenseDatabase.getInstance(application).categoryDao()
    private val repository = CategoryRepository(dao)

    private val _uiState = MutableStateFlow(CategoryManagementUiState())
    val uiState: StateFlow<CategoryManagementUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    fun addCategory(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            showError("카테고리 이름을 입력해 주세요.")
            return
        }
        performMutation { repository.addCategory(cleanName) }
    }

    fun addRule(
        categoryId: Long,
        phrase: String,
    ) {
        val cleanPhrase = phrase.trim()
        if (cleanPhrase.isBlank()) {
            showError("가맹점 식별 문구를 입력해 주세요.")
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.addRule(categoryId, cleanPhrase)
                }
            }.onSuccess { count ->
                _uiState.update {
                    it.copy(resultMessage = "기존 기타 내역 ${count}건을 자동 분류했습니다.")
                }
                loadCategories()
            }.onFailure {
                showError("이미 등록된 분류 문구인지 확인해 주세요.")
            }
        }
    }

    fun deleteRule(rule: ClassificationRuleEntity) {
        performMutation { repository.deleteRule(rule) }
    }

    fun deleteCategory(category: CategoryEntity) {
        performMutation { repository.deleteCategory(category) }
    }

    fun reclassifyOther() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.reclassifyOther() }
            }.onSuccess { count ->
                _uiState.update {
                    it.copy(resultMessage = "기타 내역 ${count}건을 자동 분류했습니다.")
                }
                loadCategories()
            }.onFailure { error ->
                showError(error.message ?: "자동 분류를 실행하지 못했습니다.")
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearResultMessage() {
        _uiState.update { it.copy(resultMessage = null) }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { dao.findAllWithRules() }
            }.onSuccess { categories ->
                _uiState.update {
                    it.copy(categories = categories, isLoading = false)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "카테고리를 불러오지 못했습니다.",
                    )
                }
            }
        }
    }

    private fun performMutation(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { action() }
            }.onSuccess {
                loadCategories()
            }.onFailure { error ->
                showError(error.message ?: "이미 등록된 이름 또는 문구인지 확인해 주세요.")
            }
        }
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }
}
