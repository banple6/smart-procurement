package com.smartprocurement.internal.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════
// 景荣鲜配 (JRXP) 专属色彩体系
// 设计理念：公安蓝 × 温和纸张色，台账单据式视觉
// ══════════════════════════════════════════════════════════════

object JrxpColors {

    // ── 主色：公安蓝系列 ──────────────────────────────
    val CommandNavy      = Color(0xFF0A2957)   // 导航、核心身份、少数主操作
    val DutyBlue         = Color(0xFF174F8A)   // 选中、焦点、可点击文本
    val DispatchBlue     = Color(0xFF2C6DA4)   // 次级交互、链接
    val PaleBlue         = Color(0xFFE9F0F7)   // 选中行底色、轻量高亮

    // ── 底色：纸张与阅读面 ──────────────────────────────
    val LedgerPaper      = Color(0xFFF7F5EF)   // 大面积阅读背景
    val ReadingSurface   = Color(0xFFFCFBF7)   // 表单、详情、文档区域
    val PureSurface      = Color(0xFFFFFFFF)   // 纯白卡片（克制使用）
    val MutedSurface     = Color(0xFFF1F2F0)   // 次级区块底色

    // ── 文字墨色 ──────────────────────────────────────
    val InkPrimary       = Color(0xFF20252B)   // 主要文本
    val InkSecondary     = Color(0xFF606973)   // 次要说明
    val InkTertiary      = Color(0xFF858D95)   // 辅助提示

    // ── 分割线与边框 ─────────────────────────────────
    val RuleLine         = Color(0xFFD9DEE2)   // 标准分割线
    val SoftDivider      = Color(0xFFE8EAE9)   // 轻分割
    val FocusedBorder    = Color(0xFF7E9AB8)   // 输入框焦点

    // ── 业务状态色 ──────────────────────────────────
    val SupplyGreen      = Color(0xFF347251)   // 充足、已完成、可用
    val WarningAmber     = Color(0xFFA66817)   // 预警、紧张、待处理
    val CriticalRed      = Color(0xFFB93636)   // 异常、缺货、错误
    val CancelledGray    = Color(0xFF737980)   // 已取消、已过期

    // ── 点缀色 ──────────────────────────────────────
    val LedgerGold       = Color(0xFFAF8543)   // 极少量分隔、编号、重点标识

    // ── 容器色（用于状态背景） ─────────────────────────
    val SupplyGreenBg    = Color(0xFFEDF5F0)
    val WarningAmberBg   = Color(0xFFFDF5EB)
    val CriticalRedBg    = Color(0xFFFBEEEE)
    val PaleBlueBg       = Color(0xFFF0F5FB)

    // ── 暗色模式 ────────────────────────────────────
    val DarkBackground   = Color(0xFF161A1E)
    val DarkSurface      = Color(0xFF1E2328)
    val DarkSurfaceHigh  = Color(0xFF272C32)
    val DarkInkPrimary   = Color(0xFFE3E5E8)
    val DarkInkSecondary = Color(0xFFB0B8C1)
}
