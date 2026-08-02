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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.expensecalendar.cards.CardManagementViewModel
import com.personal.expensecalendar.storage.CardPerformanceExclusionEntity
import com.personal.expensecalendar.storage.CardPerformanceTierEntity
import com.personal.expensecalendar.storage.CardProfileEntity
import com.personal.expensecalendar.storage.CardProfileWithRules
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CardManagementScreen(
    onBack: () -> Unit,
    viewModel: CardManagementViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddCardDialog by remember { mutableStateOf(false) }
    var addRuleTarget by remember { mutableStateOf<CardProfileEntity?>(null) }
    var addTierTarget by remember { mutableStateOf<CardProfileEntity?>(null) }
    var addExclusionTarget by remember { mutableStateOf<CardProfileEntity?>(null) }
    var deleteCardTarget by remember { mutableStateOf<CardProfileEntity?>(null) }

    BackHandler(onBack = onBack)

    if (showAddCardDialog) {
        AddCardDialog(
            onDismiss = { showAddCardDialog = false },
            onSave = { name, phrase ->
                viewModel.addCard(name, phrase)
                showAddCardDialog = false
            },
        )
    }
    addRuleTarget?.let { card ->
        AddRuleDialog(
            cardName = card.displayName,
            onDismiss = { addRuleTarget = null },
            onSave = { phrase ->
                viewModel.addRule(card.id, phrase)
                addRuleTarget = null
            },
        )
    }
    addTierTarget?.let { card ->
        AddPerformanceTierDialog(
            cardName = card.displayName,
            onDismiss = { addTierTarget = null },
            onSave = { minimumSpendWon, benefitWon ->
                viewModel.addPerformanceTier(card.id, minimumSpendWon, benefitWon)
                addTierTarget = null
            },
        )
    }
    addExclusionTarget?.let { card ->
        AddPerformanceExclusionDialog(
            cardName = card.displayName,
            onDismiss = { addExclusionTarget = null },
            onSave = { phrase ->
                viewModel.addPerformanceExclusion(card.id, phrase)
                addExclusionTarget = null
            },
        )
    }
    deleteCardTarget?.let { card ->
        AlertDialog(
            onDismissRequest = { deleteCardTarget = null },
            title = { Text("${card.displayName} 삭제") },
            text = {
                Text("카드와 식별 문장을 삭제합니다. 이미 저장된 결제 내역의 카드명은 유지됩니다.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCard(card)
                        deleteCardTarget = null
                    },
                ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCardTarget = null }) { Text("취소") }
            },
        )
    }
    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("저장할 수 없음") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("확인") }
            },
        )
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CardManagementHeader(
                    onBack = onBack,
                    onAddCard = { showAddCardDialog = true },
                )
            }
            item {
                Text(
                    text = "문자에 아래 문장이 포함되면 연결된 카드로 인식합니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.cards.isEmpty()) {
                item {
                    Text(
                        text = "등록된 카드가 없습니다. 카드를 추가해 주세요.",
                        modifier = Modifier.padding(vertical = 28.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    items = state.cards,
                    key = { it.card.id },
                ) { cardWithRules ->
                    CardProfileItem(
                        item = cardWithRules,
                        onAddRule = { addRuleTarget = cardWithRules.card },
                        onDeleteRule = viewModel::deleteRule,
                        onAddTier = { addTierTarget = cardWithRules.card },
                        onDeleteTier = viewModel::deletePerformanceTier,
                        onAddExclusion = { addExclusionTarget = cardWithRules.card },
                        onDeleteExclusion = viewModel::deletePerformanceExclusion,
                        onDeleteCard = { deleteCardTarget = cardWithRules.card },
                    )
                }
            }
        }
    }
}

@Composable
private fun CardManagementHeader(
    onBack: () -> Unit,
    onAddCard: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onBack) { Text("‹ 뒤로") }
        Text(
            text = "카드 관리",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Button(onClick = onAddCard) { Text("카드 추가") }
    }
}

@Composable
private fun CardProfileItem(
    item: CardProfileWithRules,
    onAddRule: () -> Unit,
    onDeleteRule: (com.personal.expensecalendar.storage.CardDetectionRuleEntity) -> Unit,
    onAddTier: () -> Unit,
    onDeleteTier: (CardPerformanceTierEntity) -> Unit,
    onAddExclusion: () -> Unit,
    onDeleteExclusion: (CardPerformanceExclusionEntity) -> Unit,
    onDeleteCard: () -> Unit,
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.card.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onDeleteCard) {
                    Text("카드 삭제", color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                text = "식별 문장 ${item.rules.size}개",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.rules.sortedBy { it.id }.forEach { rule ->
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = rule.phrase,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { onDeleteRule(rule) }) {
                        Text("삭제")
                    }
                }
            }
            TextButton(
                onClick = onAddRule,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("＋ 식별 문장 추가")
            }
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            Text(
                text = "실적 구간 ${item.performanceTiers.size}개",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (item.performanceTiers.isEmpty()) {
                Text(
                    text = "구간을 추가하면 대시보드에 카드 실적 바가 표시됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.performanceTiers.sortedBy { it.minimumSpendWon }.forEach { tier ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${formatPerformanceWon(tier.minimumSpendWon)} 이상",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "혜택 ${formatPerformanceWon(tier.benefitWon)}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(onClick = { onDeleteTier(tier) }) { Text("삭제") }
                }
            }
            TextButton(
                onClick = onAddTier,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("＋ 실적 구간 추가")
            }
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            Text(
                text = "실적 제외 문장 ${item.performanceExclusions.size}개",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "가맹점·카테고리·메모에 문장이 포함된 결제는 생활비에는 남고 카드 실적에서만 빠집니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.performanceExclusions.sortedBy { it.id }.forEach { exclusion ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = exclusion.phrase,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { onDeleteExclusion(exclusion) }) {
                        Text("삭제")
                    }
                }
            }
            TextButton(
                onClick = onAddExclusion,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("＋ 제외 문장 추가")
            }
        }
    }
}

@Composable
private fun AddCardDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("카드 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogInput(
                    value = name,
                    onValueChange = { name = it },
                    label = "카드 이름",
                    placeholder = "예: 삼성카드",
                    singleLine = true,
                )
                DialogInput(
                    value = phrase,
                    onValueChange = { phrase = it },
                    label = "첫 식별 문장",
                    placeholder = "문자에 포함되는 카드 고유 문장",
                    supportingText = "대소문자는 구분하지 않습니다.",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, phrase) },
                enabled = name.isNotBlank() && phrase.isNotBlank(),
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun AddRuleDialog(
    cardName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var phrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$cardName 식별 문장 추가") },
        text = {
            DialogInput(
                value = phrase,
                onValueChange = { phrase = it },
                label = "식별 문장",
                placeholder = "예: MY CARD 1234",
                supportingText = "이 문장이 포함된 결제 문자를 해당 카드로 인식합니다.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(phrase) },
                enabled = phrase.isNotBlank(),
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun AddPerformanceTierDialog(
    cardName: String,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit,
) {
    var minimumSpend by remember { mutableStateOf("") }
    var benefit by remember { mutableStateOf("") }
    val minimumSpendWon = minimumSpend.toLongOrNull()
    val benefitWon = benefit.toLongOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$cardName 실적 구간 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DialogInput(
                    value = minimumSpend,
                    onValueChange = { minimumSpend = it.filter(Char::isDigit) },
                    label = "최소 실적 금액",
                    placeholder = "예: 300,000",
                    suffix = "원",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = WonAmountVisualTransformation,
                )
                DialogInput(
                    value = benefit,
                    onValueChange = { benefit = it.filter(Char::isDigit) },
                    label = "할인·캐시백 금액",
                    placeholder = "예: 10,000",
                    suffix = "원",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = WonAmountVisualTransformation,
                )
                Text(
                    text = "같은 카드에서 최소 실적 금액은 중복될 수 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(requireNotNull(minimumSpendWon), requireNotNull(benefitWon)) },
                enabled = minimumSpendWon != null && benefitWon != null,
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun AddPerformanceExclusionDialog(
    cardName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var phrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$cardName 실적 제외 문장") },
        text = {
            DialogInput(
                value = phrase,
                onValueChange = { phrase = it },
                label = "제외 문장",
                placeholder = "예: 건강보험공단",
                supportingText = "특수문자와 띄어쓰기 차이는 무시합니다.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(phrase) },
                enabled = phrase.isNotBlank(),
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun formatPerformanceWon(value: Long): String =
    "${NumberFormat.getNumberInstance(Locale.KOREA).format(value)}원"
