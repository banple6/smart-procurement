package com.smartprocurement.internal.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// ══════════════════════════════════════════════════════════════
// 景荣鲜配 (JRXP) 专属业务图标
//
// 设计规范：
//   - 统一 2px 线宽（strokeWidth = 2f）
//   - 圆角线帽 (StrokeCap.Round)
//   - 24dp 基础网格
//   - 仅线性图标，不混用填充
//   - 不使用抽象 AI 星光、无意义盾牌、卡通图标
//
// 图标清单：
//   OrderDocument    — 订单单据
//   SupplyBox        — 食材箱
//   StorageShelf     — 仓储架
//   DeliveryTruck    — 配送箱
//   UnitBuilding     — 单位建筑
//   StockAlert       — 库存警示
//   DeliveryReceipt  — 发货凭证
//   DataBackup       — 数据备份
//   SystemStatus     — 系统运行
//   VersionUpdate    — 版本更新
//   InviteCode       — 邀请码
//   ScanCode         — 扫码
// ══════════════════════════════════════════════════════════════

object JrxpIcons {

    val OrderDocument: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrderDocument",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 文档轮廓
                moveTo(6f, 3f)
                lineTo(18f, 3f)
                lineTo(18f, 21f)
                lineTo(6f, 21f)
                close()
                // 折角
                moveTo(14f, 3f)
                lineTo(18f, 7f)
                // 横线 - 表示内容
                moveTo(9f, 10f)
                lineTo(15f, 10f)
                moveTo(9f, 13f)
                lineTo(15f, 13f)
                moveTo(9f, 16f)
                lineTo(12f, 16f)
            }
        }.build()
    }

    val SupplyBox: ImageVector by lazy {
        ImageVector.Builder(
            name = "SupplyBox",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 箱体
                moveTo(3f, 8f)
                lineTo(3f, 20f)
                lineTo(21f, 20f)
                lineTo(21f, 8f)
                // 盖子
                moveTo(2f, 8f)
                lineTo(22f, 8f)
                lineTo(22f, 5f)
                lineTo(2f, 5f)
                close()
                // 中间标识线
                moveTo(10f, 8f)
                lineTo(10f, 12f)
                lineTo(14f, 12f)
                lineTo(14f, 8f)
            }
        }.build()
    }

    val StorageShelf: ImageVector by lazy {
        ImageVector.Builder(
            name = "StorageShelf",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 左右立柱
                moveTo(4f, 3f)
                lineTo(4f, 21f)
                moveTo(20f, 3f)
                lineTo(20f, 21f)
                // 横梁
                moveTo(4f, 3f)
                lineTo(20f, 3f)
                moveTo(4f, 9f)
                lineTo(20f, 9f)
                moveTo(4f, 15f)
                lineTo(20f, 15f)
                moveTo(4f, 21f)
                lineTo(20f, 21f)
                // 物品示意
                moveTo(7f, 6f)
                lineTo(11f, 6f)
                lineTo(11f, 9f)
                moveTo(14f, 12f)
                lineTo(18f, 12f)
                lineTo(18f, 15f)
            }
        }.build()
    }

    val DeliveryTruck: ImageVector by lazy {
        ImageVector.Builder(
            name = "DeliveryTruck",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 车厢
                moveTo(2f, 6f)
                lineTo(15f, 6f)
                lineTo(15f, 17f)
                lineTo(2f, 17f)
                close()
                // 驾驶室
                moveTo(15f, 10f)
                lineTo(20f, 10f)
                lineTo(22f, 14f)
                lineTo(22f, 17f)
                lineTo(15f, 17f)
                // 车轮
                moveTo(8.5f, 17f)
                arcTo(1.5f, 1.5f, 0f, false, true, 5.5f, 17f)
                arcTo(1.5f, 1.5f, 0f, false, true, 8.5f, 17f)
                moveTo(20f, 17f)
                arcTo(1.5f, 1.5f, 0f, false, true, 17f, 17f)
                arcTo(1.5f, 1.5f, 0f, false, true, 20f, 17f)
            }
        }.build()
    }

    val UnitBuilding: ImageVector by lazy {
        ImageVector.Builder(
            name = "UnitBuilding",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 建筑主体
                moveTo(4f, 21f)
                lineTo(4f, 6f)
                lineTo(14f, 6f)
                lineTo(14f, 21f)
                // 侧翼
                moveTo(14f, 10f)
                lineTo(20f, 10f)
                lineTo(20f, 21f)
                // 底线
                moveTo(2f, 21f)
                lineTo(22f, 21f)
                // 窗户
                moveTo(7f, 9f)
                lineTo(7f, 11f)
                moveTo(11f, 9f)
                lineTo(11f, 11f)
                moveTo(7f, 14f)
                lineTo(7f, 16f)
                moveTo(11f, 14f)
                lineTo(11f, 16f)
                // 门
                moveTo(9f, 21f)
                lineTo(9f, 18f)
            }
        }.build()
    }

    val StockAlert: ImageVector by lazy {
        ImageVector.Builder(
            name = "StockAlert",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 箱子轮廓
                moveTo(3f, 7f)
                lineTo(3f, 19f)
                lineTo(16f, 19f)
                lineTo(16f, 7f)
                lineTo(3f, 7f)
                // 盖子
                moveTo(2f, 7f)
                lineTo(17f, 7f)
                lineTo(17f, 4f)
                lineTo(2f, 4f)
                close()
                // 警告三角
                moveTo(19f, 9f)
                lineTo(22f, 15f)
                lineTo(16f, 15f)
                close()
                // 感叹号
                moveTo(19f, 11f)
                lineTo(19f, 13f)
                moveTo(19f, 14f)
                lineTo(19f, 14.5f)
            }
        }.build()
    }

    val DeliveryReceipt: ImageVector by lazy {
        ImageVector.Builder(
            name = "DeliveryReceipt",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 凭证文档
                moveTo(5f, 2f)
                lineTo(19f, 2f)
                lineTo(19f, 22f)
                lineTo(5f, 22f)
                close()
                // 勾选框
                moveTo(8f, 7f)
                lineTo(10f, 7f)
                lineTo(10f, 9f)
                lineTo(8f, 9f)
                close()
                moveTo(8f, 12f)
                lineTo(10f, 12f)
                lineTo(10f, 14f)
                lineTo(8f, 14f)
                close()
                // 勾选标记
                moveTo(8.5f, 8f)
                lineTo(9f, 8.5f)
                lineTo(10f, 7.5f)
                // 文本行
                moveTo(12f, 8f)
                lineTo(16f, 8f)
                moveTo(12f, 13f)
                lineTo(16f, 13f)
                // 签名线
                moveTo(8f, 18f)
                lineTo(16f, 18f)
            }
        }.build()
    }

    val DataBackup: ImageVector by lazy {
        ImageVector.Builder(
            name = "DataBackup",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 磁盘 / 服务器
                moveTo(4f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 9f)
                lineTo(4f, 9f)
                close()
                moveTo(4f, 9f)
                lineTo(20f, 9f)
                lineTo(20f, 14f)
                lineTo(4f, 14f)
                close()
                // 状态灯
                moveTo(7f, 6.5f)
                arcTo(0.5f, 0.5f, 0f, true, true, 7f, 6.5f)
                moveTo(7f, 11.5f)
                arcTo(0.5f, 0.5f, 0f, true, true, 7f, 11.5f)
                // 箭头向下（备份）
                moveTo(12f, 16f)
                lineTo(12f, 21f)
                moveTo(9f, 19f)
                lineTo(12f, 21f)
                lineTo(15f, 19f)
            }
        }.build()
    }

    val SystemStatus: ImageVector by lazy {
        ImageVector.Builder(
            name = "SystemStatus",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 仪表盘外圈
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, true, true, 12f, 21f)
                arcTo(9f, 9f, 0f, true, true, 12f, 3f)
                // 指针
                moveTo(12f, 12f)
                lineTo(16f, 8f)
                // 中心点
                moveTo(12.5f, 12f)
                arcTo(0.5f, 0.5f, 0f, true, true, 11.5f, 12f)
                arcTo(0.5f, 0.5f, 0f, true, true, 12.5f, 12f)
                // 底部刻度
                moveTo(7f, 17f)
                lineTo(7.5f, 16f)
                moveTo(17f, 17f)
                lineTo(16.5f, 16f)
            }
        }.build()
    }

    val VersionUpdate: ImageVector by lazy {
        ImageVector.Builder(
            name = "VersionUpdate",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 手机轮廓
                moveTo(7f, 2f)
                lineTo(17f, 2f)
                arcTo(1f, 1f, 0f, false, true, 18f, 3f)
                lineTo(18f, 21f)
                arcTo(1f, 1f, 0f, false, true, 17f, 22f)
                lineTo(7f, 22f)
                arcTo(1f, 1f, 0f, false, true, 6f, 21f)
                lineTo(6f, 3f)
                arcTo(1f, 1f, 0f, false, true, 7f, 2f)
                // 上箭头（更新）
                moveTo(12f, 8f)
                lineTo(12f, 16f)
                moveTo(9f, 11f)
                lineTo(12f, 8f)
                lineTo(15f, 11f)
            }
        }.build()
    }

    val InviteCode: ImageVector by lazy {
        ImageVector.Builder(
            name = "InviteCode",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 信封
                moveTo(3f, 5f)
                lineTo(21f, 5f)
                lineTo(21f, 19f)
                lineTo(3f, 19f)
                close()
                // 信封盖
                moveTo(3f, 5f)
                lineTo(12f, 12f)
                lineTo(21f, 5f)
                // 内部密码线
                moveTo(8f, 15f)
                lineTo(8f, 15.5f)
                moveTo(10.5f, 15f)
                lineTo(10.5f, 15.5f)
                moveTo(13f, 15f)
                lineTo(13f, 15.5f)
                moveTo(15.5f, 15f)
                lineTo(15.5f, 15.5f)
            }
        }.build()
    }

    val ScanCode: ImageVector by lazy {
        ImageVector.Builder(
            name = "ScanCode",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 四个角框
                moveTo(3f, 8f)
                lineTo(3f, 3f)
                lineTo(8f, 3f)
                moveTo(16f, 3f)
                lineTo(21f, 3f)
                lineTo(21f, 8f)
                moveTo(21f, 16f)
                lineTo(21f, 21f)
                lineTo(16f, 21f)
                moveTo(8f, 21f)
                lineTo(3f, 21f)
                lineTo(3f, 16f)
                // 扫描线
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }.build()
    }
}
