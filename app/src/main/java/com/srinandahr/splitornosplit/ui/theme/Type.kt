package com.srinandahr.splitornosplit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.srinandahr.splitornosplit.R

// The original layouts set fontFamily="@font/poppins_medium" on every view; keeping
// it here preserves the app's existing look through the Compose migration.
val Poppins = FontFamily(Font(R.font.poppins_medium))

private fun poppins(size: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
    fontFamily = Poppins,
    fontWeight = weight,
    fontSize = size.sp,
    letterSpacing = spacing.sp,
)

val AppTypography = Typography(
    headlineMedium = poppins(26, FontWeight.Bold),
    headlineSmall = poppins(22, FontWeight.Bold),
    titleLarge = poppins(18, FontWeight.SemiBold),
    titleMedium = poppins(16, FontWeight.SemiBold),
    bodyLarge = poppins(15, FontWeight.Normal),
    bodyMedium = poppins(14, FontWeight.Normal),
    bodySmall = poppins(13, FontWeight.Normal),
    labelLarge = poppins(14, FontWeight.SemiBold),
    labelMedium = poppins(12, FontWeight.Medium),
    labelSmall = poppins(11, FontWeight.Medium),
)
