package com.twinmind.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TwinMindColorScheme = darkColorScheme(
    primary = TwinMindAccentBlue,
    onPrimary = TwinMindTextPrimary,
    secondary = TwinMindAccentBlueLight,
    onSecondary = TwinMindTextPrimary,
    background = TwinMindBlack,
    onBackground = TwinMindTextPrimary,
    surface = TwinMindSurface,
    onSurface = TwinMindTextPrimary,
    surfaceVariant = TwinMindCardBg,
    onSurfaceVariant = TwinMindTextSecondary,
    error = TwinMindRed,
    onError = TwinMindTextPrimary
)

@Composable
fun TwinMindTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TwinMindColorScheme,
        typography = Typography,
        content = content
    )
}