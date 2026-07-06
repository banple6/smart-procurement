package com.smartprocurement.internal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ══════════════════════════════════════════════════════════════
// 景荣鲜配 (JRXP) 字体系统
//
// 三套字体角色：
//   JrxpText     → 正文、表单、列表、操作说明
//   JrxpDisplay  → 页面标题、模块标题、品牌名称
//   JrxpNumeric  → 金额、库存、数量、时间、版本号
//
// ⚠️ 当前使用 fallback 字体：
//   - JrxpText    → SansSerif（避免使用 Default/Roboto）
//   - JrxpDisplay → SansSerif Bold
//   - JrxpNumeric → Monospace（等宽数字对齐）
//
// 正式字体资源待客户确认后替换为项目内嵌 WOFF2/TTF。
// 字体加载失败时仍使用以上 fallback 保证页面可用。
// ══════════════════════════════════════════════════════════════

// --- 字体族定义 ---
// 后续替换为项目内合法字体时，仅需修改此处
val JrxpTextFamily = FontFamily.SansSerif
val JrxpDisplayFamily = FontFamily.SansSerif
val JrxpNumericFamily = FontFamily.Monospace

// --- Material Typography 全量覆盖 ---
val JrxpTypography = Typography(

    // ── Display ──────────────────────────────────────
    displayLarge = TextStyle(
        fontFamily = JrxpDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = JrxpDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    displaySmall = TextStyle(
        fontFamily = JrxpDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),

    // ── Headline ─────────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily = JrxpDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = JrxpDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = JrxpDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),

    // ── Title ────────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily = JrxpDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = JrxpTextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = JrxpTextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // ── Body ─────────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily = JrxpTextFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = JrxpTextFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = JrxpTextFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),

    // ── Label ────────────────────────────────────────
    labelLarge = TextStyle(
        fontFamily = JrxpTextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JrxpTextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JrxpTextFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    )
)

// --- 数字专用 TextStyle（台账金额对齐） ---
object JrxpNumericStyles {
    val amountLarge = TextStyle(
        fontFamily = JrxpNumericFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
    val amountMedium = TextStyle(
        fontFamily = JrxpNumericFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )
    val amountSmall = TextStyle(
        fontFamily = JrxpNumericFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )
    val quantity = TextStyle(
        fontFamily = JrxpNumericFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )
    val timestamp = TextStyle(
        fontFamily = JrxpNumericFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )
    val version = TextStyle(
        fontFamily = JrxpNumericFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    )
}
