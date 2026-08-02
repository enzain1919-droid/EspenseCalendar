package com.personal.expensecalendar.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.expensecalendar.review.MessageReviewItem
import com.personal.expensecalendar.review.MessageReviewViewModel
import com.personal.expensecalendar.sms.PaymentStatus
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageReviewScreen(
    yearMonth: YearMonth,
    onBack: () -> Unit,
    viewModel: MessageReviewViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedItem by remember { mutableStateOf<MessageReviewItem?>(null) }
    var ignoreTarget by remember { mutableStateOf<MessageReviewItem?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.load(yearMonth)
    }

    BackHandler(onBack = onBack)
    DisposableEffect(viewModel) {
        onDispose { viewModel.clearSensitiveMessages() }
    }
    LaunchedEffect(yearMonth) {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            viewModel.load(yearMonth)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("확인") }
            },
            title = { Text("검토함 처리 실패") },
            text = { Text(message) },
        )
    }

    ignoreTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { ignoreTarget = null },
            title = { Text("이 문자를 제외할까요?") },
            text = {
                Text("제외하면 다음 자동 업데이트와 검토함에서 다시 나타나지 않습니다.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.ignoreMessage(item)
                        ignoreTarget = null
                    },
                ) { Text("제외") }
            },
            dismissButton = {
                TextButton(onClick = { ignoreTarget = null }) { Text("취소") }
            },
        )
    }

    selectedItem?.let { item ->
        TransactionEditDialog(
            transaction = viewModel.createTransactionDraft(item),
            cardNames = state.cardNames,
            categories = state.categories,
            onDismiss = { selectedItem = null },
            onSave = { input ->
                viewModel.registerMessage(item, input)
                selectedItem = null
            },
            title = "실패 문자 내역 등록",
            confirmLabel = "내역 등록",
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
            item {
                ReviewHeader(
                    yearMonth = yearMonth,
                    onBack = onBack,
                )
            }
            item {
                ReviewSummaryCard(
                    isLoading = state.isLoading,
                    reviewCount = state.items.size,
                    candidateCount = state.candidateCount,
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
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (permissionDenied) {
                item {
                    PermissionRequiredCard(
                        onRequestPermission = {
                            permissionDenied = false
                            permissionLauncher.launch(Manifest.permission.READ_SMS)
                        },
                    )
                }
            } else if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 52.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.items.isEmpty()) {
                item { EmptyReviewInbox() }
            } else {
                items(
                    items = state.items,
                    key = { it.message.sourceFingerprint },
                ) { item ->
                    ReviewMessageCard(
                        item = item,
                        isProcessing = item.message.sourceFingerprint in state.processingFingerprints,
                        onRegister = { selectedItem = item },
                        onIgnore = { ignoreTarget = item },
                    )
                }
            }
            item {
                Text(
                    text = "문자 원문은 검토하는 동안에만 읽으며 앱 DB나 백업 파일에는 저장하지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewHeader(
    yearMonth: YearMonth,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("‹ 뒤로") }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "문자 분석 실패 검토함",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${yearMonth.year}년 ${yearMonth.monthValue}월",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ReviewSummaryCard(
    isLoading: Boolean,
    reviewCount: Int,
    candidateCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = if (isLoading) "결제 후보를 다시 확인하고 있어요" else "검토 필요 ${reviewCount}건",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (isLoading) {
                    "저장된 내역과 제외한 문자는 자동으로 걸러냅니다."
                } else {
                    "결제 후보 ${candidateCount}건 중 자동 분석하지 못한 문자입니다."
                },
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewMessageCard(
    item: MessageReviewItem,
    isProcessing: Boolean,
    onRegister: () -> Unit,
    onIgnore: () -> Unit,
) {
    val message = item.message
    val receivedAt = Instant.ofEpochMilli(message.record.receivedAtMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
    val amountText = message.draft.amountWon?.let(::formatReviewWon) ?: "금액 확인 필요"
    val statusText = if (message.draft.status == PaymentStatus.CANCELED) "취소" else "승인"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isProcessing, onClick = onRegister),
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
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.record.sender.ifBlank { "발신자 미확인" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$receivedAt · ${message.record.transport.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = amountText,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                message.draft.missingReasons.forEach { reason ->
                    ReviewBadge(reason)
                }
                ReviewBadge(statusText)
                ReviewBadge(item.suggestedCategoryName)
            }
            Text(
                text = message.record.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 7,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(14.dp),
                    )
                    .padding(12.dp),
            )
            if (isProcessing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onIgnore) { Text("이 문자 제외") }
                    TextButton(onClick = onRegister) { Text("보완하여 등록") }
                }
            }
        }
    }
}

@Composable
private fun ReviewBadge(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(50),
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyReviewInbox() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "검토할 문자가 없습니다",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "이 달의 결제 후보가 모두 등록되었거나 제외되었습니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PermissionRequiredCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "문자 접근 권한이 필요합니다",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "선택한 달의 결제 후보만 다시 확인하며 원문은 저장하지 않습니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRequestPermission) { Text("문자 권한 허용") }
        }
    }
}

private fun formatReviewWon(amount: Long): String =
    "${NumberFormat.getNumberInstance(Locale.KOREA).format(amount)}원"
