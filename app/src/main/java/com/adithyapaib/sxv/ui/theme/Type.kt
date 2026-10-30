package com.adithyapaib.sxv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.adithyapaib.sxv.R

@OptIn(ExperimentalTextApi::class)
private fun soraWeight(weight: FontWeight) = Font(
    resId = R.font.sora_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

private val Sora = FontFamily(
    soraWeight(FontWeight.Normal),
    soraWeight(FontWeight.SemiBold),
    soraWeight(FontWeight.Bold)
)

// Tabular numerals keep digits from jittering as values change.
private const val TABULAR = "tnum"

val Typography = Typography(
    // Hero speed
    displayLarge = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Bold,
        fontSize = 88.sp,
        lineHeight = 92.sp,
        letterSpacing = (-2).sp,
        fontFeatureSettings = TABULAR
    ),
    // Card values
    headlineMedium = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontFeatureSettings = TABULAR
    ),
    headlineSmall = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // Buttons and nav
    labelLarge = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    // Small caps labels: callers upper-case the text (see Label)
    labelSmall = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp
    )
)
