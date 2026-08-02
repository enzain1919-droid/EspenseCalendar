package com.personal.expensecalendar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.expensecalendar.deleted.DeletedTransactionsViewModel
import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.storage.DeletedTransactionEntity
import com.personal.expensecalendar.storage.MajorCategory
import com.personal.expensecalendar.storage.PaymentMethod
import com.personal.expensecalendar.storage.TransactionType
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DeletedTransactionsScreen(
    onBack: () -> Unit,
    viewModel: DeletedTransactionsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var restoreTarget by remember { mutableStateOf<DeletedTransactionEntity?>(null) }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { viewModel.load() }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("삭제 내역 처리 실패") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("확인") }
            },
        )
    }

    restoreTarget?.let { transaction ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("삭제 내역 복구") },
            text = {
                Text("${transaction.merchant} 내역을 원래 거래 날짜로 복구할까요?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restore(transaction)
                        restoreTarget = null
                    },
                ) { Text("복구") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("취소") }
            },
        )
    }

    Scaffold(modifier = Modifier.statusBarsPadding()) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DeletedTransactionsHeader(onBack) }
            item {
                DeletedTransactionsSummary(
                    isLoading = state.isLoading,
                    count = state.items.size,
                )
            }
            state.actionMessage?.let { message ->
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            when {
                state.isLoading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                state.items.isEmpty() -> item {
                    Text(
                        text = "복구할 수 있는 삭제 내역이 없습니다.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> items(
                    items = state.items,
                    key = DeletedTransactionEntity::sourceFingerprint,
                ) { transaction ->
                    DeletedTransactionCard(
                        transaction = transaction,
                        isProcessing = transaction.sourceFingerprint in
                            state.processingFingerprints,
                        onLongClick = { restoreTarget = transaction },
                    )
                }
            }
            item {
                Text(
                    text = "내역을 길게 누르면 복구할 수 있습니다. 복구하면 원래 날짜의 월 합계와 그래프가 다시 계산됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun DeletedTransactionsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("‹ 뒤로") }
        Text(
            text = "삭제한 카드 내역",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DeletedTransactionsSummary(
    isLoading: Boolean,
    count: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = if (isLoading) "삭제 내역을 불러오고 있어요" else "삭제 내역 ${count}건",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "거래 정보와 분류 설정을 보관하며 원하는 내역만 복구할 수 있습니다.",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeletedTransactionCard(
    transaction: DeletedTransactionEntity,
    isProcessing: Boolean,
    onLongClick: () -> Unit,
) {
    val isIncome = transaction.transactionType == TransactionType.INCOME.name
    val isCanceled = transaction.status == PaymentStatus.CANCELED.name
    val amountPrefix = when {
        isIncome -> "+"
        isCanceled -> "-"
        else -> ""
    }
    val occurredAt = remember(transaction.occurredAtMillis) {
        formatDeletedTime(transaction.occurredAtMillis)
    }
    val deletedAt = remember(transaction.deletedAtMillis) {
        formatDeletedTime(transaction.deletedAtMillis)
    }
    val paymentLabel = if (transaction.paymentMethod == PaymentMethod.CASH.name) {
        "현금"
    } else {
        transaction.cardName
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = !isProcessing,
                onClick = {},
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = transaction.merchant,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = amountPrefix + formatDeletedWon(transaction.amountWon),
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isIncome -> MaterialTheme.colorScheme.primary
                        isCanceled -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Text(
                text = "거래 $occurredAt · $paymentLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${MajorCategory.fromStored(transaction.majorCategory).displayName} > " +
                    transaction.categoryName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (isProcessing) "복구 중…" else "삭제 $deletedAt · 길게 눌러 복구",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

private fun formatDeletedTime(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))

private fun formatDeletedWon(value: Long): String =
    "${NumberFormat.getNumberInstance(Locale.KOREA).format(value)}원"
