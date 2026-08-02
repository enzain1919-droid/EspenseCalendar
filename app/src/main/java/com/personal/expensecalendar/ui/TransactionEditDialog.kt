package com.personal.expensecalendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personal.expensecalendar.dashboard.TransactionEditInput
import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.storage.CategoryEntity
import com.personal.expensecalendar.storage.MajorCategory
import com.personal.expensecalendar.storage.PaymentMethod
import com.personal.expensecalendar.storage.PerformanceOverride
import com.personal.expensecalendar.storage.TransactionEntity
import com.personal.expensecalendar.storage.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TransactionEditDialog(
    transaction: TransactionEntity,
    cardNames: List<String>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (TransactionEditInput) -> Unit,
    title: String = "내역 수정",
    confirmLabel: String = "저장",
) {
    val originalDate = remember(transaction.id, transaction.occurredAtMillis) {
        Instant.ofEpochMilli(transaction.occurredAtMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }
    val availableCardNames = remember(cardNames, transaction.cardName, transaction.paymentMethod) {
        buildList {
            addAll(cardNames)
            if (transaction.paymentMethod == PaymentMethod.CARD.name) add(transaction.cardName)
        }.filter(String::isNotBlank).distinct()
    }
    var merchant by remember(transaction.id) { mutableStateOf(transaction.merchant) }
    var amountText by remember(transaction.id) { mutableStateOf(transaction.amountWon.toString()) }
    var selectedDate by remember(transaction.id) { mutableStateOf(originalDate) }
    var memo by remember(transaction.id) { mutableStateOf(transaction.memo) }
    var transactionType by remember(transaction.id) {
        mutableStateOf(
            runCatching { TransactionType.valueOf(transaction.transactionType) }
                .getOrDefault(TransactionType.EXPENSE),
        )
    }
    var paymentMethod by remember(transaction.id) {
        mutableStateOf(
            runCatching { PaymentMethod.valueOf(transaction.paymentMethod) }
                .getOrDefault(PaymentMethod.CARD),
        )
    }
    var selectedCardName by remember(transaction.id, availableCardNames) {
        mutableStateOf(
            transaction.cardName.takeIf(availableCardNames::contains)
                ?: availableCardNames.firstOrNull(),
        )
    }
    var categoryName by remember(transaction.id, categories) {
        mutableStateOf(
            transaction.categoryName.takeIf { current -> categories.any { it.name == current } }
                ?: categories.firstOrNull()?.name.orEmpty(),
        )
    }
    var majorCategory by remember(transaction.id) {
        mutableStateOf(MajorCategory.fromStored(transaction.majorCategory))
    }
    var status by remember(transaction.id) {
        mutableStateOf(
            runCatching { PaymentStatus.valueOf(transaction.status) }
                .getOrDefault(PaymentStatus.APPROVED),
        )
    }
    var performanceOverride by remember(transaction.id) {
        mutableStateOf(
            runCatching { PerformanceOverride.valueOf(transaction.performanceOverride) }
                .getOrDefault(
                    if (transaction.includedInPerformance) {
                        PerformanceOverride.AUTO
                    } else {
                        PerformanceOverride.EXCLUDE
                    },
                ),
        )
    }
    var includedInExpense by remember(transaction.id) {
        mutableStateOf(transaction.includedInExpense)
    }
    var showDatePicker by remember(transaction.id) { mutableStateOf(false) }
    val amount = amountText.toLongOrNull()
    val isCardExpense = transactionType == TransactionType.EXPENSE &&
        paymentMethod == PaymentMethod.CARD

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) { Text("선택") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소") }
            },
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 510.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EditSectionLabel("거래 유형")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = transactionType == TransactionType.EXPENSE,
                        onClick = { transactionType = TransactionType.EXPENSE },
                        label = { Text("지출") },
                    )
                    FilterChip(
                        selected = transactionType == TransactionType.INCOME,
                        onClick = { transactionType = TransactionType.INCOME },
                        label = { Text("입금") },
                    )
                }

                EditSectionLabel("결제 수단")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = paymentMethod == PaymentMethod.CASH,
                        onClick = { paymentMethod = PaymentMethod.CASH },
                        label = { Text("현금") },
                    )
                    FilterChip(
                        selected = paymentMethod == PaymentMethod.CARD,
                        onClick = { paymentMethod = PaymentMethod.CARD },
                        enabled = availableCardNames.isNotEmpty(),
                        label = { Text("카드") },
                    )
                }

                if (paymentMethod == PaymentMethod.CARD) {
                    EditSectionLabel("카드")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        availableCardNames.forEach { cardName ->
                            FilterChip(
                                selected = selectedCardName == cardName,
                                onClick = { selectedCardName = cardName },
                                label = { Text(cardName) },
                            )
                        }
                    }
                }

                DialogInput(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = "가맹점 또는 내용",
                    singleLine = true,
                )
                DialogInput(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit) },
                    label = if (transactionType == TransactionType.INCOME) "입금 금액" else "지출 금액",
                    suffix = "원",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = WonAmountVisualTransformation,
                )

                EditSectionLabel("날짜")
                FilledTonalButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        selectedDate.format(
                            DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN),
                        ),
                    )
                }

                EditSectionLabel("상위 분류")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MajorCategory.entries.forEach { option ->
                        FilterChip(
                            selected = majorCategory == option,
                            onClick = { majorCategory = option },
                            label = { Text(option.displayName) },
                        )
                    }
                }

                EditSectionLabel("세부 카테고리")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = categoryName == category.name,
                            onClick = { categoryName = category.name },
                            label = { Text(category.name) },
                        )
                    }
                }

                if (isCardExpense) {
                    EditSectionLabel("결제 상태")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = status == PaymentStatus.APPROVED,
                            onClick = { status = PaymentStatus.APPROVED },
                            label = { Text("승인") },
                        )
                        FilterChip(
                            selected = status == PaymentStatus.CANCELED,
                            onClick = { status = PaymentStatus.CANCELED },
                            label = { Text("승인 취소") },
                        )
                    }

                    EditSectionLabel("카드 실적 제외")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            PerformanceOverride.INCLUDE,
                            PerformanceOverride.EXCLUDE,
                        ).forEach { option ->
                            FilterChip(
                                selected = when (option) {
                                    PerformanceOverride.INCLUDE ->
                                        performanceOverride != PerformanceOverride.EXCLUDE
                                    PerformanceOverride.EXCLUDE ->
                                        performanceOverride == PerformanceOverride.EXCLUDE
                                    PerformanceOverride.AUTO -> false
                                },
                                onClick = { performanceOverride = option },
                                label = {
                                    Text(
                                        when (option) {
                                            PerformanceOverride.INCLUDE -> "포함"
                                            PerformanceOverride.EXCLUDE -> "제외"
                                            PerformanceOverride.AUTO -> "포함"
                                        },
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        text = when (performanceOverride) {
                            PerformanceOverride.AUTO ->
                                "기본은 포함이며, 카드에 등록한 제외 문장과 일치하면 자동 제외됩니다."
                            PerformanceOverride.INCLUDE -> "제외 문장과 일치해도 이 거래는 실적에 포함합니다."
                            PerformanceOverride.EXCLUDE -> "이 거래는 카드 실적에서 제외합니다."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (transactionType == TransactionType.EXPENSE) {
                    EditSectionLabel("지출 제외")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = includedInExpense,
                            onClick = { includedInExpense = true },
                            label = { Text("포함") },
                        )
                        FilterChip(
                            selected = !includedInExpense,
                            onClick = { includedInExpense = false },
                            label = { Text("제외") },
                        )
                    }
                    Text(
                        text = if (includedInExpense) {
                            "월 지출 합계, 남은 예산과 분류 그래프에 포함합니다."
                        } else {
                            "내역은 보관하지만 모든 지출 계산에서는 제외합니다."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DialogInput(
                    value = memo,
                    onValueChange = { memo = it },
                    label = "메모 (선택)",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    amount?.let { validAmount ->
                        onSave(
                            TransactionEditInput(
                                merchant = merchant,
                                amountWon = validAmount,
                                date = selectedDate,
                                memo = memo,
                                transactionType = transactionType,
                                paymentMethod = paymentMethod,
                                cardName = selectedCardName,
                                majorCategory = majorCategory,
                                categoryName = categoryName,
                                status = status,
                                performanceOverride = performanceOverride,
                                includedInExpense = includedInExpense,
                            ),
                        )
                    }
                },
                enabled = merchant.isNotBlank() && amount != null && amount > 0L &&
                    categoryName.isNotBlank() &&
                    (paymentMethod == PaymentMethod.CASH || selectedCardName != null),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun EditSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
