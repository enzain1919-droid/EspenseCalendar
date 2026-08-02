package com.personal.expensecalendar.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.expensecalendar.sms.PaymentPreview
import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.sms.SmsVerificationUiState
import com.personal.expensecalendar.sms.SmsVerificationViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsVerificationScreen(
    viewModel: SmsVerificationViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var permissionGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
        if (granted) viewModel.scanCurrentMonth()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "문자 가져오기 검증",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PermissionCard(
                    permissionGranted = permissionGranted,
                    permissionDenied = permissionDenied,
                    loading = uiState is SmsVerificationUiState.Loading,
                    onAction = {
                        if (permissionGranted) {
                            viewModel.scanCurrentMonth()
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_SMS)
                        }
                    },
                )
            }

            when (val state = uiState) {
                SmsVerificationUiState.Idle -> Unit
                is SmsVerificationUiState.Loading -> item {
                    LoadingCard(monthName = state.monthName)
                }
                is SmsVerificationUiState.Error -> item {
                    MessageCard(
                        title = "가져오기 실패",
                        message = state.message,
                        isError = true,
                    )
                }
                is SmsVerificationUiState.Loaded -> {
                    item { ImportSummaryCard(state) }
                    if (state.result.payments.isEmpty()) {
                        item {
                            MessageCard(
                                title = "저장할 결제 내역 없음",
                                message = "등록된 카드 문구와 금액을 모두 확인할 수 있는 문자가 없습니다.",
                            )
                        }
                    } else {
                        items(
                            items = state.result.payments,
                            key = PaymentPreview::sourceFingerprint,
                        ) { payment ->
                            PaymentPreviewCard(payment)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permissionGranted: Boolean,
    permissionDenied: Boolean,
    loading: Boolean,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (permissionGranted) "문자 권한이 허용되었습니다" else "문자 권한 확인이 필요합니다",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "이번 달 받은 문자만 기기 안에서 확인하고, 새 결제 내역만 저장합니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (permissionDenied) {
                Text(
                    text = "권한을 허용하지 않으면 카드 문자를 가져올 수 없습니다.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onAction,
                enabled = !loading,
            ) {
                Text(if (permissionGranted) "이번 달 SMS·MMS 가져오기" else "문자 권한 허용")
            }
        }
    }
}

@Composable
private fun LoadingCard(monthName: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text("$monthName 문자를 분석하고 저장하고 있습니다.")
        }
    }
}

@Composable
private fun ImportSummaryCard(state: SmsVerificationUiState.Loaded) {
    val result = state.result
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = result.range.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("받은 문자 ${result.scannedCount}건 · 결제 후보 ${result.candidateCount}건")
            Text(
                text = "새로 저장 ${result.importedCount}건",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "조회 SMS ${result.smsScannedCount}건 · MMS ${result.mmsScannedCount}건\n" +
                    "이미 저장됨 ${result.duplicateCount}건 · 삭제 제외 ${result.deletedCount}건 · " +
                    "분석 불가 ${result.unparsedCount}건",
            )
            Text(
                text = "기기에 저장된 전체 결제 내역 ${result.totalSavedCount}건",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    message: String,
    isError: Boolean = false,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(message)
        }
    }
}

@Composable
private fun PaymentPreviewCard(payment: PaymentPreview) {
    val occurredAt = remember(payment.occurredAtMillis) {
        DateTimeFormatter.ofPattern("MM/dd HH:mm").format(
            Instant.ofEpochMilli(payment.occurredAtMillis).atZone(ZoneId.systemDefault()),
        )
    }
    val amount = remember(payment.amountWon) {
        NumberFormat.getNumberInstance(Locale.KOREA).format(payment.amountWon)
    }
    val statusText = if (payment.status == PaymentStatus.CANCELED) "취소" else "승인"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = payment.cardName,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = occurredAt,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(payment.merchant, fontWeight = FontWeight.SemiBold)
            Text(
                text = "$statusText ${amount}원",
                color = if (payment.status == PaymentStatus.CANCELED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
