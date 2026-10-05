package com.personal.expensecalendar.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.expensecalendar.cards.CardPerformanceSummary
import com.personal.expensecalendar.dashboard.CalendarDay
import com.personal.expensecalendar.dashboard.CalendarMonthGrid
import com.personal.expensecalendar.dashboard.BudgetBalanceCalculator
import com.personal.expensecalendar.dashboard.DailyExpenseSummary
import com.personal.expensecalendar.dashboard.DashboardUiState
import com.personal.expensecalendar.dashboard.DashboardViewModel
import com.personal.expensecalendar.dashboard.ExpenseAggregation
import com.personal.expensecalendar.sms.PaymentStatus
import com.personal.expensecalendar.storage.CardPerformanceTierEntity
import com.personal.expensecalendar.storage.MajorCategory
import com.personal.expensecalendar.storage.TransactionEntity
import com.personal.expensecalendar.storage.CategoryEntity
import com.personal.expensecalendar.storage.PaymentMethod
import com.personal.expensecalendar.storage.PerformanceOverride
import com.personal.expensecalendar.storage.TransactionType
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    onOpenCardManagement: () -> Unit = {},
    onOpenCategoryManagement: () -> Unit = {},
    onOpenCardPerformance: (String, YearMonth) -> Unit = { _, _ -> },
    onOpenMessageReview: (YearMonth) -> Unit = {},
    onOpenDeletedTransactions: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<TransactionEntity?>(null) }
    var editTarget by remember { mutableStateOf<TransactionEntity?>(null) }
    var classificationTarget by remember { mutableStateOf<TransactionEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<TransactionEntity?>(null) }
    var expenseGroupSelection by remember(state.displayedMonth) {
        mutableStateOf<ExpenseGroupSelection?>(null)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.importDisplayedMonth()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshDisplayedMonth()
    }

    LaunchedEffect(state.displayedMonth, state.isImporting) {
        if (viewModel.claimAutomaticSync(state.displayedMonth)) {
            permissionDenied = false
            if (
                context.checkSelfPermission(Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.importDisplayedMonth()
            } else {
                permissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }
    }

    if (showBudgetDialog) {
        BudgetDialog(
            initialBudget = state.budgetAmountWon,
            onDismiss = { showBudgetDialog = false },
            onSave = { amount ->
                viewModel.setBudget(amount)
                showBudgetDialog = false
            },
        )
    }
    if (showManualDialog) {
        ManualExpenseDialog(
            date = state.selectedDate,
            cardNames = state.cardPerformance.map { it.cardName },
            onDismiss = { showManualDialog = false },
            onSave = { merchant, amount, memo, transactionType, paymentMethod, cardName ->
                viewModel.addManualTransaction(
                    merchant = merchant,
                    amountWon = amount,
                    memo = memo,
                    transactionType = transactionType,
                    paymentMethod = paymentMethod,
                    cardName = cardName,
                )
                showManualDialog = false
            },
        )
    }
    expenseGroupSelection?.let { selection ->
        ExpenseGroupTransactionsDialog(
            selection = selection,
            yearMonth = state.displayedMonth,
            transactions = state.monthTransactions,
            onDismiss = { expenseGroupSelection = null },
        )
    }
    editTarget?.let { transaction ->
        TransactionEditDialog(
            transaction = transaction,
            cardNames = state.cardPerformance.map { it.cardName },
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
    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("확인") }
            },
            title = { Text("처리 실패") },
            text = { Text(message) },
        )
    }
    actionTarget?.let { transaction ->
        TransactionActionDialog(
            transaction = transaction,
            onDismiss = { actionTarget = null },
            onEdit = {
                editTarget = transaction
                actionTarget = null
            },
            onClassify = {
                classificationTarget = transaction
                actionTarget = null
            },
            onDelete = {
                deleteTarget = transaction
                actionTarget = null
            },
        )
    }
    classificationTarget?.let { transaction ->
        CategoryAssignmentDialog(
            transaction = transaction,
            categories = state.categories,
            onDismiss = { classificationTarget = null },
            onSave = { category, saveRule, phrase ->
                viewModel.assignCategory(transaction, category, saveRule, phrase)
                classificationTarget = null
            },
        )
    }
    deleteTarget?.let { transaction ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("내역 삭제") },
            text = {
                Text("${transaction.merchant} 내역을 삭제합니다. 문자 내역은 다시 가져오지 않습니다.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTransaction(transaction)
                        deleteTarget = null
                    },
                ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소") }
            },
        )
    }

    Scaffold { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item {
                MonthHeader(
                    yearMonth = state.displayedMonth,
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                    onToday = viewModel::showCurrentMonth,
                )
            }
            item {
                BudgetSummaryCard(
                    state = state,
                    onSetBudget = { showBudgetDialog = true },
                )
            }
            item {
                CardPerformanceOverview(
                    summaries = state.cardPerformance,
                    onCardSelected = { cardName ->
                        onOpenCardPerformance(cardName, state.displayedMonth)
                    },
                )
            }
            item {
                ImportStatusAndManagement(
                    state = state,
                    permissionDenied = permissionDenied,
                    onOpenCardManagement = onOpenCardManagement,
                    onOpenCategoryManagement = onOpenCategoryManagement,
                    onOpenMessageReview = { onOpenMessageReview(state.displayedMonth) },
                    onOpenDeletedTransactions = onOpenDeletedTransactions,
                )
            }
            item {
                MonthCalendar(
                    state = state,
                    onDateSelected = viewModel::selectDate,
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                )
            }
            item {
                SelectedDateHeader(
                    state = state,
                    onAddManual = { showManualDialog = true },
                )
            }

            val selectedTransactions = state.monthTransactions.filter { transaction ->
                Instant.ofEpochMilli(transaction.occurredAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate() == state.selectedDate
            }
            if (selectedTransactions.isEmpty()) {
                item { EmptySelectedDate() }
            } else {
                items(
                    items = selectedTransactions,
                    key = TransactionEntity::id,
                ) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        onClick = { editTarget = transaction },
                        onLongClick = { actionTarget = transaction },
                    )
                }
            }
            item {
                MonthlyCategoryChart(
                    transactions = state.monthTransactions,
                    onCategorySelected = { categoryName ->
                        expenseGroupSelection = ExpenseGroupSelection.Category(categoryName)
                    },
                )
            }
            item {
                MonthlyMajorCategoryChart(
                    transactions = state.monthTransactions,
                    onCategorySelected = { majorCategory ->
                        expenseGroupSelection = ExpenseGroupSelection.Major(majorCategory)
                    },
                )
            }
        }
    }
}

@Composable
private fun MonthlyCategoryChart(
    transactions: List<TransactionEntity>,
    onCategorySelected: (String) -> Unit,
) {
    val summaries = remember(transactions) { ExpenseAggregation.byCategory(transactions) }
    val maximumAbsoluteWon = remember(summaries) {
        summaries.maxOfOrNull { abs(it.netAmountWon) }?.coerceAtLeast(1L) ?: 1L
    }
    val positiveTotalWon = remember(summaries) {
        summaries.filter { it.netAmountWon > 0L }.sumOf { it.netAmountWon }
    }
    val netTotalWon = remember(summaries) { summaries.sumOf { it.netAmountWon } }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "카테고리별 지출",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "항목을 누르면 이용내역을 볼 수 있어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summaries.isNotEmpty()) {
                Text(
                    text = "순지출 ${formatWon(netTotalWon)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            if (summaries.isEmpty()) {
                Text(
                    text = "이 달에는 표시할 지출이 없습니다.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    summaries.forEach { summary ->
                        val progress by animateFloatAsState(
                            targetValue = (
                                abs(summary.netAmountWon).toFloat() / maximumAbsoluteWon
                                ).coerceIn(0f, 1f),
                            animationSpec = tween(
                                durationMillis = 650,
                                easing = FastOutSlowInEasing,
                            ),
                            label = "${summary.categoryName}-monthly-expense",
                        )
                        val isRefund = summary.netAmountWon < 0L
                        val barColor = if (isRefund) {
                            MaterialTheme.colorScheme.error
                        } else {
                            categoryChartColor(summary.categoryName)
                        }
                        val shareText = if (isRefund) {
                            "순환급"
                        } else if (positiveTotalWon > 0L) {
                            val percentage = (
                                summary.netAmountWon * 100f / positiveTotalWon
                                ).roundToInt().coerceAtLeast(1)
                            "${percentage}%"
                        } else {
                            "0%"
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "${summary.categoryName} 지출 내역 보기",
                                    onClick = { onCategorySelected(summary.categoryName) },
                                ),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(barColor, CircleShape),
                                    )
                                    Text(
                                        text = summary.categoryName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${summary.transactionCount}건",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatWon(summary.netAmountWon),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRefund) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        text = shareText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(9.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                        RoundedCornerShape(50),
                                    ),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .background(barColor, RoundedCornerShape(50)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyMajorCategoryChart(
    transactions: List<TransactionEntity>,
    onCategorySelected: (MajorCategory) -> Unit,
) {
    val summaries = remember(transactions) {
        ExpenseAggregation.byMajorCategory(transactions)
    }
    val maximumAbsoluteWon = remember(summaries) {
        summaries.maxOfOrNull { abs(it.netAmountWon) }?.coerceAtLeast(1L) ?: 1L
    }
    val positiveTotalWon = remember(summaries) {
        summaries.filter { it.netAmountWon > 0L }.sumOf { it.netAmountWon }
    }
    val netTotalWon = remember(summaries) { summaries.sumOf { it.netAmountWon } }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "상위 분류별 지출",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "항목을 누르면 이용내역을 볼 수 있어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "순지출 ${formatWon(netTotalWon)}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                summaries.forEach { summary ->
                    val progress by animateFloatAsState(
                        targetValue = (
                            abs(summary.netAmountWon).toFloat() / maximumAbsoluteWon
                            ).coerceIn(0f, 1f),
                        animationSpec = tween(
                            durationMillis = 650,
                            easing = FastOutSlowInEasing,
                        ),
                        label = "${summary.majorCategory.name}-major-category-expense",
                    )
                    val isRefund = summary.netAmountWon < 0L
                    val barColor = if (isRefund) {
                        MaterialTheme.colorScheme.error
                    } else {
                        majorCategoryChartColor(summary.majorCategory)
                    }
                    val shareText = if (isRefund) {
                        "순환급"
                    } else if (positiveTotalWon > 0L && summary.netAmountWon > 0L) {
                        val percentage = (
                            summary.netAmountWon * 100f / positiveTotalWon
                            ).roundToInt().coerceAtLeast(1)
                        "${percentage}%"
                    } else {
                        "0%"
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "${summary.majorCategory.displayName} 지출 내역 보기",
                                onClick = { onCategorySelected(summary.majorCategory) },
                            ),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(barColor, CircleShape),
                                )
                                Text(
                                    text = summary.majorCategory.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${summary.transactionCount}건",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatWon(summary.netAmountWon),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRefund) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = shareText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(9.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    RoundedCornerShape(50),
                                ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(barColor, RoundedCornerShape(50)),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun majorCategoryChartColor(majorCategory: MajorCategory): Color = when (majorCategory) {
    MajorCategory.LIVING_EXPENSE -> Color(0xFF00897B)
    MajorCategory.FIXED_EXPENSE -> Color(0xFF6A5ACD)
    MajorCategory.JINYOUNG_ALLOWANCE -> Color(0xFFF28C28)
}

private fun categoryChartColor(categoryName: String): Color {
    val palette = listOf(
        Color(0xFF3157C8),
        Color(0xFF00897B),
        Color(0xFFF28C28),
        Color(0xFF7E57C2),
        Color(0xFF43A047),
        Color(0xFFD85C8B),
        Color(0xFF0288D1),
    )
    return when (categoryName) {
        "외식비" -> Color(0xFFEF6C45)
        "통신비" -> Color(0xFF0086A8)
        "보험비" -> Color(0xFF43A047)
        "교통비" -> Color(0xFF3157C8)
        "쇼핑" -> Color(0xFFD85C8B)
        "생활비" -> Color(0xFF00897B)
        "의료비" -> Color(0xFFE05252)
        "식료품" -> Color(0xFF6A9F36)
        "주거/공과금" -> Color(0xFF7E57C2)
        "여가/문화" -> Color(0xFFF28C28)
        "교육비" -> Color(0xFF5C6BC0)
        "기타" -> Color(0xFF6F7D8C)
        else -> palette[Math.floorMod(categoryName.hashCode(), palette.size)]
    }
}

@Composable
private fun CardPerformanceOverview(
    summaries: List<CardPerformanceSummary>,
    onCardSelected: (String) -> Unit,
) {
    if (summaries.isEmpty()) return

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "카드 실적",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        summaries.forEach { summary ->
            CardPerformanceBar(
                summary = summary,
                onClick = { onCardSelected(summary.cardName) },
            )
        }
    }
}

@Composable
private fun CardPerformanceBar(
    summary: CardPerformanceSummary,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = summary.cardName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "인정 실적",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatWon(summary.performanceWon),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "총 이용금액",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = formatWon(summary.totalCardSpendWon),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { summary.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            if (summary.tiers.isEmpty()) {
                Text(
                    text = "카드 관리에서 실적 구간과 혜택을 설정해 주세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val points = listOf<CardPerformanceTierEntity?>(null) + summary.tiers
                Row(modifier = Modifier.fillMaxWidth()) {
                    points.forEach { tier ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = tier?.minimumSpendWon?.let(::compactWon) ?: "0원",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(
                                text = tier?.benefitWon?.let { "혜택 ${compactWon(it)}" }
                                    ?: "혜택 없음",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                val statusText = summary.remainingToNextWon?.let { remaining ->
                    "현재 혜택 ${formatWon(summary.currentBenefitWon)} · 다음 구간까지 ${formatWon(remaining)}"
                } ?: "최고 구간 달성 · 현재 혜택 ${formatWon(summary.currentBenefitWon)}"
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (summary.excludedCount > 0) {
                Text(
                    text = "실적 제외 ${summary.excludedCount}건 · ${formatWon(summary.excludedWon)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "이용내역 보기  ›",
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MonthHeader(
    yearMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 22.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrevious) {
            Text("‹", style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            text = "${yearMonth.year}년 ${yearMonth.monthValue}월",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onNext) {
                Text("›", style = MaterialTheme.typography.headlineMedium)
            }
            TextButton(onClick = onToday) { Text("오늘") }
        }
    }
}

@Composable
private fun BudgetSummaryCard(
    state: DashboardUiState,
    onSetBudget: () -> Unit,
) {
    val budget = state.budgetAmountWon
    val spent = state.monthlySpentWon
    val balance = budget?.let {
        BudgetBalanceCalculator.calculate(
            budgetWon = it,
            spentWon = spent,
            incomeWon = state.monthlyIncomeWon,
        )
    }
    val remaining = balance?.remainingWon
    val progress = balance?.usageRatio ?: 0f
    val progressColor = when {
        progress >= 1f -> MaterialTheme.colorScheme.error
        progress >= 0.9f -> Color(0xFFE85D3F)
        progress >= 0.7f -> Color(0xFFE6A23C)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("이번 달 지출", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = formatWon(spent),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(onClick = onSetBudget) {
                    Text(if (budget == null) "예산 설정" else "예산 변경")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("예산 ${budget?.let(::formatWon) ?: "미설정"}")
                Text(
                    text = remaining?.let { "남음 ${formatWon(it)}" } ?: "남은 금액 -",
                    color = if (remaining != null && remaining < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = if (balance != null && state.monthlyIncomeWon > 0L) {
                    "이번 달 입금 +${formatWon(state.monthlyIncomeWon)} · " +
                        "사용 가능 ${formatWon(balance.availableWon)}"
                } else {
                    "이번 달 입금 ${formatWon(state.monthlyIncomeWon)}"
                },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            if (balance != null && balance.availableWon > 0L) {
                Text(
                    text = if (state.monthlyIncomeWon > 0L) {
                        "사용 가능 금액의 ${(progress * 100).toInt()}% 사용"
                    } else {
                        "예산의 ${(progress * 100).toInt()}% 사용"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = progressColor,
                )
            }
        }
    }
}

@Composable
private fun ImportStatusAndManagement(
    state: DashboardUiState,
    permissionDenied: Boolean,
    onOpenCardManagement: () -> Unit,
    onOpenCategoryManagement: () -> Unit,
    onOpenMessageReview: () -> Unit,
    onOpenDeletedTransactions: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.isImporting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.padding(horizontal = 5.dp))
                Text("${state.displayedMonth.monthValue}월 결제 문자 자동 업데이트 중")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            val reviewCount = state.lastImportResult?.unparsedCount ?: 0
            TextButton(onClick = onOpenMessageReview) {
                Text(
                    text = if (reviewCount > 0) "문자 검토함 $reviewCount" else "문자 검토함",
                    color = if (reviewCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            TextButton(onClick = onOpenDeletedTransactions) {
                Text("삭제 내역")
            }
            TextButton(onClick = onOpenCategoryManagement) {
                Text("카테고리 관리")
            }
            TextButton(onClick = onOpenCardManagement) {
                Text("카드 관리")
            }
        }
        if (permissionDenied) {
            Text(
                text = "문자 권한이 거부되어 자동 업데이트할 수 없습니다. 다음 앱 실행 때 다시 요청합니다.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        state.lastImportResult?.let { result ->
            Text(
                text = "문자 자동 업데이트 완료 · 새 내역 ${result.importedCount}건 · " +
                    "이미 반영 ${result.duplicateCount}건" +
                    if (result.unparsedCount > 0) " · 검토 필요 ${result.unparsedCount}건" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthCalendar(
    state: DashboardUiState,
    onDateSelected: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val days = remember(state.displayedMonth) {
        CalendarMonthGrid.build(state.displayedMonth)
    }
    var horizontalDrag by remember(state.displayedMonth) { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .pointerInput(state.displayedMonth) {
                detectHorizontalDragGestures(
                    onDragStart = { horizontalDrag = 0f },
                    onDragEnd = {
                        when {
                            horizontalDrag > 90f -> onPrevious()
                            horizontalDrag < -90f -> onNext()
                        }
                        horizontalDrag = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        horizontalDrag += dragAmount
                    },
                )
            },
    ) {
        WeekdayHeader()
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        summary = state.daySummaries[day.date],
                        isSelected = day.date == state.selectedDate,
                        isToday = day.date == LocalDate.now(),
                        onClick = { onDateSelected(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = when (index) {
                    0 -> Color(0xFFD95151)
                    6 -> Color(0xFF3979C9)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    summary: DailyExpenseSummary?,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateColor = when (day.date.dayOfWeek) {
        DayOfWeek.SUNDAY -> Color(0xFFD95151)
        DayOfWeek.SATURDAY -> Color(0xFF3979C9)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val selectedModifier = if (isSelected) {
        Modifier.border(
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(14.dp),
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(0.78f)
            .then(selectedModifier)
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 5.dp)
            .alpha(if (day.isInDisplayedMonth) 1f else 0.32f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isToday) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text(
                text = day.date.dayOfMonth.toString(),
                color = dateColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            )
        }
        summary?.let {
            Spacer(Modifier.height(4.dp))
            val displayedAmount = when {
                it.netAmountWon != 0L -> compactWon(it.netAmountWon)
                it.incomeAmountWon > 0L -> "+${compactWon(it.incomeAmountWon)}"
                else -> "0"
            }
            Text(
                text = displayedAmount,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.labelSmall,
                color = if (it.netAmountWon < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (it.transactionCount > 1) {
                    "${it.representativeCategory} 외 ${it.transactionCount - 1}"
                } else {
                    it.representativeCategory
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectedDateHeader(
    state: DashboardUiState,
    onAddManual: () -> Unit,
) {
    val summary = state.daySummaries[state.selectedDate]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = state.selectedDate.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "지출 ${formatWon(summary?.netAmountWon ?: 0)} · 입금 ${formatWon(summary?.incomeAmountWon ?: 0)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onAddManual) { Text("＋ 수동 입력") }
    }
}

@Composable
private fun EmptySelectedDate() {
    Text(
        text = "이 날짜에 저장된 내역이 없습니다.",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val signedAmount = ExpenseAggregation.signedAmount(transaction)
    val isIncome = transaction.transactionType == TransactionType.INCOME.name
    val displayedAmount = if (isIncome) transaction.amountWon else signedAmount
    val paymentLabel = if (transaction.paymentMethod == PaymentMethod.CASH.name) {
        "현금"
    } else {
        transaction.cardName
    }
    val time = remember(transaction.occurredAtMillis) {
        Instant.ofEpochMilli(transaction.occurredAtMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(transaction.merchant, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (isIncome) "+${formatWon(displayedAmount)}" else formatWon(displayedAmount),
                color = if (isIncome) {
                    MaterialTheme.colorScheme.primary
                } else if (signedAmount < 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "$time · $paymentLabel · " +
                "${MajorCategory.fromStored(transaction.majorCategory).displayName} > " +
                transaction.categoryName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isIncome) {
            Text(
                text = "입금",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else if (transaction.status == PaymentStatus.CANCELED.name) {
            Text(
                text = "승인 취소",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!isIncome && !transaction.includedInExpense) {
            Text(
                text = "지출 제외",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun TransactionActionDialog(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onClassify: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(transaction.merchant) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("내역 전체 수정") }
                TextButton(
                    onClick = onClassify,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("분류 규칙 설정") }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("내역 삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun CategoryAssignmentDialog(
    transaction: TransactionEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity, Boolean, String) -> Unit,
) {
    var selectedCategoryId by remember(transaction.id, categories) {
        mutableStateOf(
            categories.firstOrNull { it.name == transaction.categoryName }?.id
                ?: categories.firstOrNull()?.id,
        )
    }
    var saveRule by remember(transaction.id) { mutableStateOf(true) }
    var rulePhrase by remember(transaction.id) { mutableStateOf(transaction.merchant) }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("분류 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(transaction.merchant, fontWeight = FontWeight.SemiBold)
                Text("카테고리", style = MaterialTheme.typography.labelLarge)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 250.dp),
                ) {
                    items(
                        items = categories,
                        key = CategoryEntity::id,
                    ) { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategoryId = category.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedCategoryId == category.id,
                                onClick = { selectedCategoryId = category.id },
                            )
                            Text(category.name)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { saveRule = !saveRule },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = saveRule,
                        onCheckedChange = { saveRule = it },
                    )
                    Text("이 가맹점 문구를 자동 분류 규칙으로 저장")
                }
                if (saveRule) {
                    DialogInput(
                        value = rulePhrase,
                        onValueChange = { rulePhrase = it },
                        label = "자동 분류 문구",
                        supportingText = "기존 기타 내역에도 즉시 적용됩니다.",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedCategory?.let { onSave(it, saveRule, rulePhrase) }
                },
                enabled = selectedCategory != null && (!saveRule || rulePhrase.isNotBlank()),
            ) { Text("적용") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UnusedDashboardTransactionEditDialog(
    transaction: TransactionEntity,
    cardNames: List<String>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (
        String,
        Long,
        LocalDate,
        String,
        TransactionType,
        PaymentMethod,
        String?,
        String,
        PaymentStatus,
        PerformanceOverride,
    ) -> Unit,
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
        title = { Text("내역 수정") },
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

                EditSectionLabel("카테고리")
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

                    EditSectionLabel("카드 실적")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PerformanceOverride.entries.forEach { option ->
                            FilterChip(
                                selected = performanceOverride == option,
                                onClick = { performanceOverride = option },
                                label = {
                                    Text(
                                        when (option) {
                                            PerformanceOverride.AUTO -> "자동"
                                            PerformanceOverride.INCLUDE -> "포함"
                                            PerformanceOverride.EXCLUDE -> "제외"
                                        },
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        text = when (performanceOverride) {
                            PerformanceOverride.AUTO -> "카드에 등록한 실적 제외 문장을 자동으로 적용합니다."
                            PerformanceOverride.INCLUDE -> "제외 문장과 일치해도 이 거래는 실적에 포함합니다."
                            PerformanceOverride.EXCLUDE -> "이 거래는 카드 실적에서 제외합니다."
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
                            merchant,
                            validAmount,
                            selectedDate,
                            memo,
                            transactionType,
                            paymentMethod,
                            selectedCardName,
                            categoryName,
                            status,
                            performanceOverride,
                        )
                    }
                },
                enabled = merchant.isNotBlank() && amount != null && amount > 0L &&
                    categoryName.isNotBlank() &&
                    (paymentMethod == PaymentMethod.CASH || selectedCardName != null),
            ) { Text("저장") }
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

@Composable
private fun BudgetDialog(
    initialBudget: Long?,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var amountText by remember(initialBudget) {
        mutableStateOf(initialBudget?.toString().orEmpty())
    }
    val amount = amountText.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("월 예산 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogInput(
                    value = amountText,
                    onValueChange = { value -> amountText = value.filter(Char::isDigit) },
                    label = "예산 금액",
                    suffix = "원",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = WonAmountVisualTransformation,
                )
                Text(
                    text = "이 달에 설정한 예산은 다음 달 이후에도 계속 적용됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amount?.let(onSave) },
                enabled = amount != null,
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun ManualExpenseDialog(
    date: LocalDate,
    cardNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, Long, String, TransactionType, PaymentMethod, String?) -> Unit,
) {
    var merchant by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var selectedCardName by remember(cardNames) { mutableStateOf(cardNames.firstOrNull()) }
    val amount = amountText.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.monthValue}월 ${date.dayOfMonth}일 내역 입력") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("거래 유형", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Text("결제 수단", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = paymentMethod == PaymentMethod.CASH,
                        onClick = { paymentMethod = PaymentMethod.CASH },
                        label = { Text("현금") },
                    )
                    FilterChip(
                        selected = paymentMethod == PaymentMethod.CARD,
                        onClick = { paymentMethod = PaymentMethod.CARD },
                        enabled = cardNames.isNotEmpty(),
                        label = { Text("카드") },
                    )
                }
                if (paymentMethod == PaymentMethod.CARD) {
                    Text("카드 선택", style = MaterialTheme.typography.labelLarge)
                    cardNames.forEach { cardName ->
                        FilterChip(
                            selected = selectedCardName == cardName,
                            onClick = { selectedCardName = cardName },
                            label = { Text(cardName) },
                        )
                    }
                } else if (cardNames.isEmpty()) {
                    Text(
                        text = "카드 결제를 입력하려면 카드 관리에서 카드를 먼저 추가해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DialogInput(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = "가맹점 또는 내용",
                    singleLine = true,
                )
                DialogInput(
                    value = amountText,
                    onValueChange = { value -> amountText = value.filter(Char::isDigit) },
                    label = if (transactionType == TransactionType.INCOME) "입금 금액" else "지출 금액",
                    suffix = "원",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = WonAmountVisualTransformation,
                )
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
                    amount?.let {
                        onSave(
                            merchant,
                            it,
                            memo,
                            transactionType,
                            paymentMethod,
                            selectedCardName,
                        )
                    }
                },
                enabled = amount != null && amount > 0 &&
                    (paymentMethod == PaymentMethod.CASH || selectedCardName != null),
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun formatWon(amount: Long): String =
    "${NumberFormat.getNumberInstance(Locale.KOREA).format(amount)}원"

private fun compactWon(amount: Long): String {
    val absoluteAmount = abs(amount)
    val sign = if (amount < 0) "-" else ""
    return when {
        absoluteAmount >= 100_000_000 -> "$sign${trimDecimal(absoluteAmount / 100_000_000.0)}억"
        absoluteAmount >= 10_000 -> "$sign${trimDecimal(absoluteAmount / 10_000.0)}만"
        else -> "$sign${NumberFormat.getNumberInstance(Locale.KOREA).format(absoluteAmount)}"
    }
}

private fun trimDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.KOREA, "%.1f", value)
