package com.droid.dolphy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.droid.dolphy.R

val AppFontFamily = FontFamily(Font(R.font.born2b_sporty_v2))
val AppSansFamily = FontFamily.SansSerif

fun buildAppTypography(useFlipperFont: Boolean, fontScale: Float): Typography {
    val family = if (useFlipperFont) AppFontFamily else AppSansFamily
    val weight = if (useFlipperFont) FontWeight.Normal else FontWeight.Bold

    val baseScale = if (useFlipperFont) fontScale else 0.95f
    val safeScale = baseScale.coerceIn(0.7f, 1.5f)
    val base = Typography()

    return base.copy(
        displayLarge = base.displayLarge.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.displayLarge.fontSize * safeScale,
            lineHeight = base.displayLarge.lineHeight * safeScale,
            letterSpacing = 0.3.sp
        ),
        displayMedium = base.displayMedium.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.displayMedium.fontSize * safeScale,
            lineHeight = base.displayMedium.lineHeight * safeScale,
            letterSpacing = 0.3.sp
        ),
        displaySmall = base.displaySmall.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.displaySmall.fontSize * safeScale,
            lineHeight = base.displaySmall.lineHeight * safeScale,
            letterSpacing = 0.25.sp
        ),
        headlineLarge = base.headlineLarge.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.headlineLarge.fontSize * safeScale,
            lineHeight = base.headlineLarge.lineHeight * safeScale,
            letterSpacing = 0.25.sp
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.headlineMedium.fontSize * safeScale,
            lineHeight = base.headlineMedium.lineHeight * safeScale,
            letterSpacing = 0.25.sp
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.headlineSmall.fontSize * safeScale,
            lineHeight = base.headlineSmall.lineHeight * safeScale,
            letterSpacing = 0.2.sp
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.titleLarge.fontSize * safeScale,
            lineHeight = base.titleLarge.lineHeight * safeScale,
            letterSpacing = 0.2.sp
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.titleMedium.fontSize * safeScale,
            lineHeight = base.titleMedium.lineHeight * safeScale,
            letterSpacing = 0.2.sp
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.titleSmall.fontSize * safeScale,
            lineHeight = base.titleSmall.lineHeight * safeScale,
            letterSpacing = 0.15.sp
        ),
        bodyLarge = base.bodyLarge.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.bodyLarge.fontSize * safeScale,
            lineHeight = base.bodyLarge.lineHeight * safeScale,
            letterSpacing = 0.2.sp
        ),
        bodyMedium = base.bodyMedium.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.bodyMedium.fontSize * safeScale,
            lineHeight = base.bodyMedium.lineHeight * safeScale,
            letterSpacing = 0.15.sp
        ),
        bodySmall = base.bodySmall.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.bodySmall.fontSize * safeScale,
            lineHeight = base.bodySmall.lineHeight * safeScale,
            letterSpacing = 0.15.sp
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.labelLarge.fontSize * safeScale,
            lineHeight = base.labelLarge.lineHeight * safeScale,
            letterSpacing = 0.2.sp
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.labelMedium.fontSize * safeScale,
            lineHeight = base.labelMedium.lineHeight * safeScale,
            letterSpacing = 0.15.sp
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = family,
            fontWeight = weight,
            fontSize = base.labelSmall.fontSize * safeScale,
            lineHeight = base.labelSmall.lineHeight * safeScale,
            letterSpacing = 0.15.sp
        ),
    )
}

fun buildExpressiveTypography(useFlipperFont: Boolean, fontScale: Float): Typography {
    val base = buildAppTypography(useFlipperFont, fontScale)
    return base.copy(
        displayLarge = base.displayLarge.copy(fontSize = base.displayLarge.fontSize * 1.08f, lineHeight = base.displayLarge.lineHeight * 1.08f),
        displayMedium = base.displayMedium.copy(fontSize = base.displayMedium.fontSize * 1.06f, lineHeight = base.displayMedium.lineHeight * 1.06f),
        displaySmall = base.displaySmall.copy(fontSize = base.displaySmall.fontSize * 1.05f, lineHeight = base.displaySmall.lineHeight * 1.05f),
        headlineLarge = base.headlineLarge.copy(fontSize = base.headlineLarge.fontSize * 1.05f, lineHeight = base.headlineLarge.lineHeight * 1.05f),
        headlineMedium = base.headlineMedium.copy(fontSize = base.headlineMedium.fontSize * 1.04f, lineHeight = base.headlineMedium.lineHeight * 1.04f),
        headlineSmall = base.headlineSmall.copy(fontSize = base.headlineSmall.fontSize * 1.03f, lineHeight = base.headlineSmall.lineHeight * 1.03f),
        titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize * 1.03f, lineHeight = base.titleLarge.lineHeight * 1.03f),
        titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize * 1.02f, lineHeight = base.titleMedium.lineHeight * 1.02f),
        titleSmall = base.titleSmall.copy(fontSize = base.titleSmall.fontSize * 1.02f, lineHeight = base.titleSmall.lineHeight * 1.02f),
        bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize * 1.02f, lineHeight = base.bodyLarge.lineHeight * 1.02f),
        bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize * 1.02f, lineHeight = base.bodyMedium.lineHeight * 1.02f),
        bodySmall = base.bodySmall.copy(fontSize = base.bodySmall.fontSize * 1.02f, lineHeight = base.bodySmall.lineHeight * 1.02f),
        labelLarge = base.labelLarge.copy(fontSize = base.labelLarge.fontSize * 1.02f, lineHeight = base.labelLarge.lineHeight * 1.02f),
        labelMedium = base.labelMedium.copy(fontSize = base.labelMedium.fontSize * 1.02f, lineHeight = base.labelMedium.lineHeight * 1.02f),
        labelSmall = base.labelSmall.copy(fontSize = base.labelSmall.fontSize * 1.02f, lineHeight = base.labelSmall.lineHeight * 1.02f),
    )
}

val Typography = buildAppTypography(useFlipperFont = true, fontScale = 1.08f)

