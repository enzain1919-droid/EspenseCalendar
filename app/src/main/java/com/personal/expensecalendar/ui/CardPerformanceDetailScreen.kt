package com.personal.expensecalendar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.expensecalendar.cards.CardPerformanceDetailViewModel
import com.personal.expensecalendar.cards.CardPerformanceTransactionItem
import com.personal.expensecalendar.dashboard.ExpenseAggregation
import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.storage.MajorCategory
import com.personal.expensecalendar.storage.TransactionEntity
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CardPerformanceDetailScreen(
    cardName: String,
    yearMonth: YearMonth,
    onBack: () -> Unit,
    viewModel: CardPerformanceDetailViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var actionTarget by remember { mutableStateOf<TransactionEntity?>(null) }
    var editTarget by remember { mutableStateOf<TransactionEntity?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(cardName, yearMonth) {
        viewModel.load(cardName, yearMonth)
    }

    Scaffold(modifier = Modifier.statusBarsPadding()) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PerformanceDetailHeader(
                    cardName = cardName,
                    yearMonth = yearMonth,
                    onBack = onBack,
                )
            }
            if (state.isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.errorMessage != null) {
                item {
                    Text(
                        text = requireNotNull(state.errorMessage),
                        modifier = Modifier.padding(vertical = 36.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                state.summary?.let { summary ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "인정 실적 ${formatDetailWon(summary.performanceWon)}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "전체 카드 이용 ${formatDetailWon(summary.totalCardSpendWon)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (summary.excludedCount > 0) {
                                    Text(
                                        text = "실적 제외 ${summary.excludedCount}건 · " +
                                            formatDetailWon(summary.excludedWon),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = "이용내역 ${state.transactions.size}건",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "내역을 길게 누르면 수정할 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.transactions.isEmpty()) {
                    item {
                        Text(
                            text = "이 달의 카드 이용내역이 없습니다.",
                            modifier = Modifier.padding(vertical = 36.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(
                        items = state.transactions,
                        key = { it.transaction.id },
                    ) { item ->
                        PerformanceTransactionRow(
                            item = item,
                            onLongClick = { actionTarget = item.transaction },
                        )
                    }
                }
            }
        }
    }

    actionTarget?.let { transaction ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(transaction.merchant) },
            text = { Text("이 카드 이용내역을 수정할 수 있습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        editTarget = transaction
                        actionTarget = null
                    },
                ) {
                    Text("내역 전체 수정")
                }
            },
            dismissButton = {
                TextButton(onClick = { actionTarget = null }) {
                    Text("닫기")
                }
            },
        )
    }

    editTarget?.let { transaction ->
        TransactionEditDialog(
            transaction = transaction,
            cardNames = state.cardNames,
            categories = state.categories,
            onDismiss = { editTarget = null },
            onSave = { input ->
                viewModel.updateTransaction(
                    transaction = transaction,
                    input = input,
                )
                editTarget = null
            },
        )
    }
}

@Composable
private fun PerformanceDetailHeader(
    cardName: String,
    yearMonth: YearMonth,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onBack) { Text("‹ 뒤로") }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = cardName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${yearMonth.year}년 ${yearMonth.monthValue}월 실적",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PerformanceTransactionRow(
    item: CardPerformanceTransactionItem,
    onLongClick: () -> Unit,
) {
    val transaction = item.transaction
    val signedAmount = ExpenseAggregation.signedAmount(transaction)
    val occurredAt = Instant.ofEpochMilli(transaction.occurredAtMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM월 dd일 HH:mm"))
    val isCanceled = transaction.status == PaymentStatus.CANCELED.name
    val badgeText = when {
        !item.inclusion.isIncluded -> "실적 제외"
        isCanceled -> "실적 차감"
        else -> "실적 포함"
    }
    val badgeColor = when {
        !item.inclusion.isIncluded -> MaterialTheme.colorScheme.surfaceContainerHighest
        isCanceled -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = transaction.merchant,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.padding(horizontal = 6.dp))
                Text(
                    text = formatDetailWon(signedAmount),
                    fontWeight = FontWeight.Bold,
                    color = if (signedAmount < 0L) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$occurredAt · " +
                        "${MajorCategory.fromStored(transaction.majorCategory).displayName} > " +
                        transaction.categoryName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeColor,
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            item.inclusion.exclusionReason?.let { reason ->
                HorizontalDivider(color = Color.Transparent)
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!transaction.includedInExpense) {
                Text(
                    text = "월 지출 합계와 예산 계산에서 제외됨",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

private fun formatDetailWon(value: Long): String =
    "${NumberFormat.getNumberInstance(Locale.KOREA).format(value)}원"
