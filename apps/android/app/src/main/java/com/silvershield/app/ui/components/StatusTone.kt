package com.silvershield.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.silvershield.app.ui.theme.SilverShieldTokens

/**
 * 统一的语义状态色板。
 *
 * 老人端、家属端、管理端共用同一套语义：安全 / 警示 / 危险 / 中性 / 信息。
 * 所有状态类组件（StatusCard、RiskBadge、EmptyState…）都必须经由本枚举取色，
 * 不允许业务代码自行指定颜色或仅靠颜色表达状态。
 */
enum class StatusTone {
    Safe,
    Warning,
    Danger,
    Neutral,
    Info,
}

@Immutable
data class ToneColors(
    /** 主色：图标、边框、强调文字 */
    val accent: Color,
    /** 容器底色 */
    val container: Color,
    /** 容器内文字色（保证对比度） */
    val onContainer: Color,
)

/** 依据当前主题解析语义色，深色模式自动切换。 */
@Composable
fun StatusTone.toneColors(): ToneColors {
    val tokens = SilverShieldTokens.colors
    return when (this) {
        StatusTone.Safe -> ToneColors(tokens.safe, tokens.safeContainer, tokens.onSafeContainer)
        StatusTone.Warning -> ToneColors(tokens.warning, tokens.warningContainer, tokens.onWarningContainer)
        StatusTone.Danger -> ToneColors(tokens.danger, tokens.dangerContainer, tokens.onDangerContainer)
        StatusTone.Neutral -> ToneColors(tokens.neutral, tokens.neutralContainer, tokens.onNeutralContainer)
        StatusTone.Info -> ToneColors(
            accent = MaterialTheme.colorScheme.primary,
            container = tokens.accentLight,
            onContainer = MaterialTheme.colorScheme.onSurface,
        )
    }
}
