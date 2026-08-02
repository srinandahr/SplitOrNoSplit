package com.srinandahr.splitornosplit.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandGold,
    onPrimaryContainer = Color(0xFF3A2A00),
    secondary = BrandGoldDark,
    onSecondary = Color.White,
    background = SurfaceLight,
    onBackground = TextPrimaryLight,
    surface = CardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = TextSecondaryLight,
    error = NegativeRed,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = BrandGold,
    onPrimary = Color(0xFF241900),
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = Color.White,
    secondary = BrandGold,
    onSecondary = Color(0xFF241900),
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    surface = CardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF2A2C31),
    onSurfaceVariant = TextSecondaryDark,
    error = Color(0xFFFF6B5E),
    onError = Color(0xFF3A0906),
)

/**
 * Ledger direction colours. Not part of the Material scheme because "money coming back to
 * you" is a domain signal, not a UI role — and it must stay legible in both themes.
 */
object LedgerColors {
    val lent: Color
        @Composable get() = if (isSystemInDarkTheme()) LentGreenDark else LentGreen
    val borrowed: Color
        @Composable get() = if (isSystemInDarkTheme()) BorrowedOrangeDark else BorrowedOrange
}

@Composable
fun SplitOrNoSplitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
