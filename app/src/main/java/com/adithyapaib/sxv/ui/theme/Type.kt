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

/** Roadbook numerals and row titles: a condensed face that packs big digits into narrow boxes. */
val Saira = FontFamily(
    Font(R.font.saira_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.saira_condensed_bold, FontWeight.Bold),
    Font(R.font.saira_condensed_extrabold, FontWeight.ExtraBold)
)

@OptIn(ExperimentalTextApi::class)
private fun monoWeight(weight: FontWeight) = Font(
    resId = R.font.roboto_mono,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/** Labels, units and data notes: the roadbook's measured, typewritten voice. */
val Mono = FontFamily(
    monoWeight(FontWeight.Medium),
    monoWeight(FontWeight.Bold)
)

private const val TABULAR = "tnum"

val Typography = Typography(
    // Speed in the header box
    displayLarge = TextStyle(
        fontFamily = Saira,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 112.sp,
        lineHeight = 104.sp,
        letterSpacing = (-2).sp,
        fontFeatureSettings = TABULAR
    ),
    // Live distance in the NOW row
    displaySmall = TextStyle(
        fontFamily = Saira,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 40.sp,
        fontFeatureSettings = TABULAR
    ),
    // Readings: engine, fuel, distances, screen titles
    headlineMedium = TextStyle(
        fontFamily = Saira,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 36.sp,
        fontFeatureSettings = TABULAR
    ),
    headlineSmall = TextStyle(
        fontFamily = Saira,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.5.sp
    ),
    // Roadbook row titles
    titleLarge = TextStyle(
        fontFamily = Saira,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Saira,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    // Buttons
    labelLarge = TextStyle(
        fontFamily = Saira,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 22.sp,
        letterSpacing = 1.sp
    ),
    // Nav labels, chips
    labelMedium = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    // Box labels (callers upper-case): SPEED · KM/H
    labelSmall = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.sp
    )
)
