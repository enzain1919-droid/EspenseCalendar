package com.personal.expensecalendar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.personal.expensecalendar.backup.LocalBackupCoordinator
import com.personal.expensecalendar.backup.LocalBackupManager
import com.personal.expensecalendar.backup.RestoreResult
import com.personal.expensecalendar.storage.ExpenseDatabase
import com.personal.expensecalendar.ui.BackupSetupScreen
import com.personal.expensecalendar.ui.CardManagementScreen
import com.personal.expensecalendar.ui.CardPerformanceDetailScreen
import com.personal.expensecalendar.ui.CategoryManagementScreen
import com.personal.expensecalendar.ui.DashboardScreen
import com.personal.expensecalendar.ui.ExpenseCalendarTheme
import java.time.YearMonth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var startupState by mutableStateOf<StartupState>(StartupState.Checking)
    private var preparationInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExpenseCalendarTheme {
                when (val state = startupState) {
                    StartupState.Ready -> ExpenseCalendarApp()
                    StartupState.Checking -> BackupSetupScreen(
                        isPreparing = true,
                        onOpenSettings = ::openStorageSettings,
                        onRetry = ::prepareDataIfPossible,
                    )
                    StartupState.NeedsStorageAccess -> BackupSetupScreen(
                        isPreparing = false,
                        onOpenSettings = ::openStorageSettings,
                        onRetry = ::prepareDataIfPossible,
                    )
                    is StartupState.Error -> BackupSetupScreen(
                        isPreparing = false,
                        errorMessage = state.message,
                        onOpenSettings = ::openStorageSettings,
                        onRetry = ::prepareDataIfPossible,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (LocalBackupManager.hasRequiredStorageAccess()) {
            if (startupState !is StartupState.Ready) prepareDataIfPossible()
        } else {
            startupState = StartupState.NeedsStorageAccess
        }
    }

    override fun onStop() {
        if (startupState is StartupState.Ready) {
            LocalBackupCoordinator.requestImmediateBackup()
        }
        super.onStop()
    }

    private fun prepareDataIfPossible() {
        if (preparationInProgress) return
        if (!LocalBackupManager.hasRequiredStorageAccess()) {
            startupState = StartupState.NeedsStorageAccess
            return
        }

        preparationInProgress = true
        startupState = StartupState.Checking
        lifecycleScope.launch {
            runCatching {
                val restoreResult = LocalBackupManager.restoreIfPresent(applicationContext)
                val database = ExpenseDatabase.getInstance(applicationContext)
                LocalBackupCoordinator.start(applicationContext, database)
                restoreResult
            }.onSuccess { result ->
                startupState = StartupState.Ready
                if (result is RestoreResult.Restored) {
                    Toast.makeText(
                        this@MainActivity,
                        "백업 데이터를 자동 복원했습니다.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }.onFailure { error ->
                startupState = StartupState.Error(
                    error.message ?: "백업 데이터를 확인하지 못했습니다.",
                )
            }
            preparationInProgress = false
        }
    }

    private fun openStorageSettings() {
        val appSettings = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(appSettings) }
            .onFailure {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
    }
}

@androidx.compose.runtime.Composable
private fun ExpenseCalendarApp() {
    var screen by rememberSaveable { mutableStateOf(AppScreen.DASHBOARD) }
    var selectedCardName by rememberSaveable { mutableStateOf("") }
    var selectedCardMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }

    AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = targetState.depth >= initialState.depth
                        val enterOffset: (Int) -> Int = { width ->
                            if (forward) width / 5 else -width / 5
                        }
                        val exitOffset: (Int) -> Int = { width ->
                            if (forward) -width / 7 else width / 7
                        }
                        (
                            slideInHorizontally(
                                animationSpec = tween(
                                    durationMillis = 380,
                                    easing = FastOutSlowInEasing,
                                ),
                                initialOffsetX = enterOffset,
                            ) + fadeIn(animationSpec = tween(260))
                            ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(
                                    durationMillis = 300,
                                    easing = FastOutSlowInEasing,
                                ),
                                targetOffsetX = exitOffset,
                            ) + fadeOut(animationSpec = tween(220))
                            )
                    },
                    label = "expense-calendar-screen-transition",
    ) { targetScreen ->
        when (targetScreen) {
                        AppScreen.DASHBOARD -> DashboardScreen(
                            onOpenCardManagement = { screen = AppScreen.CARDS },
                            onOpenCategoryManagement = { screen = AppScreen.CATEGORIES },
                            onOpenCardPerformance = { cardName, yearMonth ->
                                selectedCardName = cardName
                                selectedCardMonth = yearMonth.toString()
                                screen = AppScreen.CARD_PERFORMANCE
                            },
                        )
                        AppScreen.CARDS -> CardManagementScreen(
                            onBack = { screen = AppScreen.DASHBOARD },
                        )
                        AppScreen.CATEGORIES -> CategoryManagementScreen(
                            onBack = { screen = AppScreen.DASHBOARD },
                        )
                        AppScreen.CARD_PERFORMANCE -> CardPerformanceDetailScreen(
                            cardName = selectedCardName,
                            yearMonth = YearMonth.parse(selectedCardMonth),
                            onBack = { screen = AppScreen.DASHBOARD },
                        )
        }
    }
}

private sealed interface StartupState {
    data object Checking : StartupState
    data object NeedsStorageAccess : StartupState
    data object Ready : StartupState
    data class Error(val message: String) : StartupState
}

private enum class AppScreen {
    DASHBOARD,
    CARDS,
    CATEGORIES,
    CARD_PERFORMANCE,
    ;

    val depth: Int
        get() = if (this == DASHBOARD) 0 else 1
}
