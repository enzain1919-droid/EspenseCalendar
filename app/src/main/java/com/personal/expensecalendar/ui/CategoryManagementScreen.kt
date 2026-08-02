package com.personal.expensecalendar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.expensecalendar.categories.CategoryManagementViewModel
import com.personal.expensecalendar.storage.CategoryEntity
import com.personal.expensecalendar.storage.CategoryWithRules
import com.personal.expensecalendar.storage.ClassificationRuleEntity

@Composable
fun CategoryManagementScreen(
    onBack: () -> Unit,
    viewModel: CategoryManagementViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var addRuleTarget by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleteCategoryTarget by remember { mutableStateOf<CategoryEntity?>(null) }

    BackHandler(onBack = onBack)

    if (showAddCategoryDialog) {
        SingleTextDialog(
            title = "카테고리 추가",
            label = "카테고리 이름",
            placeholder = "예: 취미",
            onDismiss = { showAddCategoryDialog = false },
            onSave = { name ->
                viewModel.addCategory(name)
                showAddCategoryDialog = false
            },
        )
    }
    addRuleTarget?.let { category ->
        SingleTextDialog(
            title = "${category.name} 자동 분류 문구",
            label = "가맹점에 포함되는 문구",
            placeholder = "예: 스타벅스",
            onDismiss = { addRuleTarget = null },
            onSave = { phrase ->
                viewModel.addRule(category.id, phrase)
                addRuleTarget = null
            },
        )
    }
    deleteCategoryTarget?.let { category ->
        AlertDialog(
            onDismissRequest = { deleteCategoryTarget = null },
            title = { Text("${category.name} 삭제") },
            text = { Text("분류 규칙을 함께 삭제하고, 해당 카테고리의 기존 거래는 기타로 되돌립니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(category)
                        deleteCategoryTarget = null
                    },
                ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCategoryTarget = null }) { Text("취소") }
            },
        )
    }
    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("처리할 수 없음") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("확인") }
            },
        )
    }
    state.resultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearResultMessage,
            title = { Text("자동 분류 완료") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearResultMessage) { Text("확인") }
            },
        )
    }

    Scaffold(modifier = Modifier.statusBarsPadding()) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onBack) { Text("‹ 뒤로") }
                    Text(
                        text = "카테고리 관리",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Button(onClick = { showAddCategoryDialog = true }) { Text("추가") }
                }
            }
            item {
                Text(
                    text = "가맹점 이름에 등록 문구가 포함되면 자동으로 분류합니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedButton(
                    onClick = viewModel::reclassifyOther,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("기존 기타 내역 다시 자동 분류")
                }
            }
            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }
                }
            } else {
                items(
                    items = state.categories,
                    key = { it.category.id },
                ) { item ->
                    CategoryItem(
                        item = item,
                        onAddRule = { addRuleTarget = item.category },
                        onDeleteRule = viewModel::deleteRule,
                        onDeleteCategory = { deleteCategoryTarget = item.category },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    item: CategoryWithRules,
    onAddRule: () -> Unit,
    onDeleteRule: (ClassificationRuleEntity) -> Unit,
    onDeleteCategory: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.category.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (!item.category.isSystem) {
                    TextButton(onClick = onDeleteCategory) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Text(
                text = "자동 분류 문구 ${item.rules.size}개",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.rules.sortedBy { it.id }.forEach { rule ->
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = rule.phrase,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { onDeleteRule(rule) }) { Text("삭제") }
                }
            }
            if (!item.category.isSystem) {
                TextButton(
                    onClick = onAddRule,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("＋ 자동 분류 문구 추가") }
            }
        }
    }
}

@Composable
private fun SingleTextDialog(
    title: String,
    label: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            DialogInput(
                value = value,
                onValueChange = { value = it },
                label = label,
                placeholder = placeholder,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(value) },
                enabled = value.isNotBlank(),
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
