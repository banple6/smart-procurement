package com.smartprocurement.internal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// ══════════════════════════════════════════════════════════════
// 三公鲜配设计系统 Token
// 间距、圆角、分割线、动效时长
// ══════════════════════════════════════════════════════════════

object JrxpDimensions {

    // ── 圆角 — 克制使用，台账不需要大圆角 ─────────────
    val cornerNone   = 0.dp
    val cornerXs     = 2.dp      // 状态印记
    val cornerSm     = 4.dp      // 状态标签、小标签
    val cornerMd     = 6.dp      // 输入框、小按钮
    val cornerLg     = 8.dp      // 少量独立区块
    val cornerXl     = 10.dp     // 对话框（仅此处允许较大圆角）

    // Shape 快捷
    val shapeNone    = RoundedCornerShape(cornerNone)
    val shapeXs      = RoundedCornerShape(cornerXs)
    val shapeSm      = RoundedCornerShape(cornerSm)
    val shapeMd      = RoundedCornerShape(cornerMd)
    val shapeLg      = RoundedCornerShape(cornerLg)
    val shapeXl      = RoundedCornerShape(cornerXl)

    // ── 间距 ─────────────────────────────────────────
    val spacingXxs   = 2.dp
    val spacingXs    = 4.dp
    val spacingSm    = 8.dp
    val spacingMd    = 12.dp
    val spacingLg    = 16.dp
    val spacingXl    = 24.dp
    val spacingXxl   = 32.dp
    val spacingHuge  = 48.dp

    // ── 分割线 ───────────────────────────────────────
    val ruleLineWidth = 1.dp
    val sectionIndicatorWidth = 3.dp    // 左侧短蓝线
    val sectionIndicatorHeight = 20.dp  // 短蓝线高度

    // ── 触控区域 ─────────────────────────────────────
    val touchTargetMin = 48.dp          // Android 最小触控
    val stepperButtonSize = 48.dp       // 数量 +/- 按钮

    // ── 图标尺寸 ─────────────────────────────────────
    val iconXs = 14.dp
    val iconSm = 18.dp
    val iconMd = 24.dp
    val iconLg = 32.dp
    val iconXl = 48.dp

    // ── 列表行高 ─────────────────────────────────────
    val listItemMinHeight = 52.dp
    val listItemCompactHeight = 44.dp

    // ── 顶栏高度 ─────────────────────────────────────
    val topBarHeight = 56.dp
    val bottomBarHeight = 64.dp

    // ── 台账表格 ─────────────────────────────────────
    val ledgerNumberColumnWidth = 40.dp
    val ledgerRowPadding = 12.dp
}

object JrxpMotion {

    // ── 微交互时长（毫秒） ───────────────────────────
    val durationFast     = 120     // 选中背景过渡
    val durationNormal   = 160     // 状态标签更新
    val durationMedium   = 180     // 面板淡入
    val durationSlow     = 250     // 页面转场

    // ── 位移 ─────────────────────────────────────────
    val slideOffsetSmall = 12      // dp，工作区横移
    val slideOffsetMedium = 24     // dp，BottomSheet 短距离

    // ── 缩放 ─────────────────────────────────────────
    val scalePress     = 0.96f    // 数量增减按压
    val scaleNormal    = 1.0f
}
