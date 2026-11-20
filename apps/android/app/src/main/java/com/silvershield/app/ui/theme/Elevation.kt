package com.silvershield.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 银盾智鉴 · 阴影 Token。
 *
 * 克制的层级语言：卡片默认平面 + 1dp 边框，仅浮层使用阴影，
 * 避免"卡片堆阴影"的廉价感，也避免深色模式下阴影失效。
 */
object Elevation {
    /** 无阴影（默认卡片，靠边框区分层次） */
    val none = 0.dp

    /** 轻微抬升（可点击卡片、悬停态） */
    val low = 1.dp

    /** 卡片默认 */
    val card = 2.dp

    /** 浮层（下拉、弹窗、底部操作条） */
    val overlay = 8.dp

    /** 强浮层（模态对话框） */
    val modal = 16.dp
}
