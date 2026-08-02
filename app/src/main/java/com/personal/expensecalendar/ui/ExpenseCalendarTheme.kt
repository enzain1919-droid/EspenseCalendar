package com.personal.expensecalendar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3157C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF10245E),
    secondary = Color(0xFF52606F),
    background = Color(0xFFF9F9FC),
    surface = Color.White,
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF173F9E),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = Color(0xFFBAC7D6),
    background = Color(0xFF111318),
    surface = Color(0xFF191C22),
)

@Composable
fun ExpenseCalendarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

