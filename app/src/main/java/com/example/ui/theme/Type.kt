package com.smartprocurement.internal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ══════════════════════════════════════════════════════════════
// 三公鲜配字体系统 - 严谨语义化重构版
//
// 核心原则：
// 1. 只使用 400 (Regular), 500 (Medium), 600 (SemiBold)。禁止使用 700+。
// 2. 最低业务字号不小于 13sp，行高控制在 150% 左右。
// 3. 数字启用 tnum (tabular-nums) 进行无裁切对齐。
// ══════════════════════════════════════════════════════════════

// 暂用系统默认，后续可直接替换为 Noto Sans SC 等
val JrxpFontFamily = FontFamily.SansSerif

// 独立提取的 11 层语义化 Typography Tokens
object JrxpSemanticTypography {
    val DisplayTitle = TextStyle(
        fontFamily = JrxpFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    )
    val SectionTitle = TextStyle(
        fontFamily = JrxpFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    )
    val ListTitle = TextStyle(
        fontFamily = JrxpFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )
    val BodyPrimary = TextStyle(
        fontFamily = JrxpFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )
    val BodySecondary = TextStyle(
        fontFamily = JrxpFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    )
    val FieldLabel = TextStyle(
        fontFamily = JrxpFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )
    val SupportingText = TextStyle(
        fontFamily = JrxpFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp
    )
    val ButtonText = TextStyle(
        fontFamily = JrxpFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )
    val NavigationLabel = TextStyle(
        fontFamily = JrxpFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
    val MetricLarge = TextStyle(
        fontFamily = JrxpFontFamily, // 复用正文字体
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum" // 开启 Tabular Numbers
    )
    val MetricMedium = TextStyle(
        fontFamily = JrxpFontFamily, // 复用正文字体
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum" // 开启 Tabular Numbers
    )
}

// 映射到 Material3 的 Typography，以保证旧代码兼容性
val JrxpTypography = Typography(
    displayLarge = JrxpSemanticTypography.DisplayTitle,
    displayMedium = JrxpSemanticTypography.SectionTitle,
    displaySmall = JrxpSemanticTypography.SectionTitle,

    headlineLarge = JrxpSemanticTypography.DisplayTitle,
    headlineMedium = JrxpSemanticTypography.SectionTitle,
    headlineSmall = JrxpSemanticTypography.ListTitle,

    titleLarge = JrxpSemanticTypography.SectionTitle,
    titleMedium = JrxpSemanticTypography.ListTitle,
    titleSmall = JrxpSemanticTypography.FieldLabel,

    bodyLarge = JrxpSemanticTypography.BodyPrimary,
    bodyMedium = JrxpSemanticTypography.BodySecondary,
    bodySmall = JrxpSemanticTypography.SupportingText,

    labelLarge = JrxpSemanticTypography.ButtonText,
    labelMedium = JrxpSemanticTypography.FieldLabel,
    labelSmall = JrxpSemanticTypography.NavigationLabel
)

// 为了兼容老代码中的 JrxpNumericStyles 调用，做向下映射，并确保应用了 tnum
object JrxpNumericStyles {
    val amountLarge = JrxpSemanticTypography.MetricLarge
    val amountMedium = JrxpSemanticTypography.MetricMedium
    val amountSmall = JrxpSemanticTypography.MetricMedium.copy(fontSize = 16.sp, lineHeight = 24.sp)
    val quantity = JrxpSemanticTypography.MetricMedium.copy(fontSize = 15.sp, lineHeight = 22.sp)
    val timestamp = JrxpSemanticTypography.SupportingText.copy(fontFeatureSettings = "tnum")
    val version = JrxpSemanticTypography.SupportingText.copy(fontFeatureSettings = "tnum")
}
