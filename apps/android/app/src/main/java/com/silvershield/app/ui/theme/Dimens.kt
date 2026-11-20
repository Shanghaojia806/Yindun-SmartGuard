package com.silvershield.app.ui.theme

import androidx.compose.ui.unit.dp

/*
 * 银盾智鉴 · 尺寸与间距 Tokens
 *
 * 适老化约束（V1.3）：
 * - 最小触控区域 48dp × 48dp
 * - 主要操作按钮高度 56–64dp
 * - 正文 18sp 左右，页面主标题 24–28sp
 *
 * 业务组件禁止直接写裸数值间距，统一引用本文件。
 */

/** 间距阶梯，基于 4dp 栅格 */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp

    /** 页面左右安全边距（老人端偏宽松） */
    val pageHorizontal = 20.dp

    /** 页面上下留白 */
    val pageVertical = 24.dp

    /** 卡片内边距 */
    val cardPadding = 20.dp

    /** 卡片之间的竖直间距 */
    val cardGap = 16.dp
}

/** 圆角阶梯 */
object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val pill = 100.dp
}

/** 组件尺寸 */
object Sizes {
    /** 最小触控区域（无障碍下限，不得下调） */
    val touchTargetMin = 48.dp

    /** 主要操作按钮高度 */
    val primaryButtonHeight = 56.dp

    /** 次要按钮高度 */
    val secondaryButtonHeight = 48.dp

    val iconXs = 16.dp
    val iconSm = 20.dp
    val iconMd = 24.dp
    val iconLg = 32.dp
    val iconXl = 40.dp

    val borderWidth = 1.dp
    val statusCardIconContainer = 48.dp
}

/** 透明度阶梯（遮罩、禁用、提示底色） */
object Alphas {
    const val disabledContent = 0.38f
    const val disabledContainer = 0.12f
    const val pressedOverlay = 0.12f
    const val hoverOverlay = 0.08f
    const val badgeContainer = 0.12f
}
