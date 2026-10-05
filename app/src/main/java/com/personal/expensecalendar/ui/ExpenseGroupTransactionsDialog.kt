package com.personal.expensecalendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.expensecalendar.dashboard.ExpenseAggregation
import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.storage.MajorCategory
import com.personal.expensecalendar.storage.PaymentMethod
import com.personal.expensecalendar.storage.TransactionEntity
import com.personal.expensecalendar.storage.TransactionType
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal sealed interface ExpenseGroupSelection {
    val title: String

    fun matches(transaction: TransactionEntity): Boolean

    data class Category(val categoryName: String) : ExpenseGroupSelection {
        override val title: String get() = categoryName

        override fun matches(transaction: TransactionEntity): Boolean =
            transaction.categoryName == categoryName
    }

    data class Major(val majorCategory: MajorCategory) : ExpenseGroupSelection {
        override val title: String get() = majorCategory.displayName

        override fun matches(transaction: TransactionEntity): Boolean =
            MajorCategory.fromStored(transaction.majorCategory) == majorCategory
    }
}

@Composable
internal fun ExpenseGroupTransactionsDialog(
    selection: ExpenseGroupSelection,
    yearMonth: YearMonth,
    transactions: List<TransactionEntity>,
    onDismiss: () -> Unit,
) {
    val items = remember(transactions, selection) {
        transactions
            .filter {
                it.includedInExpense &&
                    it.transactionType != TransactionType.INCOME.name &&
                    selection.matches(it)
            }
            .sortedWith(
                compareByDescending<TransactionEntity> { it.occurredAtMillis }
                    .thenByDescending { it.id },
            )
    }
    val totalWon = remember(items) { ExpenseAggregation.monthlyTotal(items) }
    val maximumListHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${selection.title} 이용내역") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "${yearMonth.year}년 ${yearMonth.monthValue}월 · ${items.size}건 · 최신순",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("순지출", style = MaterialTheme.typography.labelLarge)
                        Text(
                            text = formatGroupWon(totalWon),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (items.isEmpty()) {
                    Text(
                        text = "이 분류에 포함된 지출 내역이 없습니다.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maximumListHeight),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items = items, key = TransactionEntity::id) { transaction ->
                            ExpenseGroupTransactionRow(transaction)
                        }
                    }
                }
                Text(
                    text = "승인 취소는 차감하며, 입금과 지출 제외 내역은 집계에서 제외합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
    )
}

@Composable
private fun ExpenseGroupTransactionRow(transaction: TransactionEntity) {
    val isCanceled = transaction.status == PaymentStatus.CANCELED.name
    val occurredAt = remember(transaction.occurredAtMillis) {
        Instant.ofEpochMilli(transaction.occurredAtMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN))
    }
    val paymentLabel = if (transaction.paymentMethod == PaymentMethod.CASH.name) {
        "현금"
    } else {
        transaction.cardName
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$occurredAt · $paymentLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                text = formatGroupWon(ExpenseAggregation.signedAmount(transaction)),
                fontWeight = FontWeight.Bold,
                color = if (isCanceled) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Text(
            text = "${MajorCategory.fromStored(transaction.majorCategory).displayName} > " +
                transaction.categoryName + if (isCanceled) " · 승인 취소" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
}

private fun formatGroupWon(value: Long): String =
    "${NumberFormat.getNumberInstance(Locale.KOREA).format(value)}원"
