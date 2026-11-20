package com.silvershield.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * 银盾智鉴 · 形状 Tokens。
 *
 * 统一圆角语言：小控件克制（8dp），卡片中等（16dp），不追求大圆角与胶囊化，
 * 保持"专业、可信"的视觉印象。
 */
val SilverShieldShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl),
)
