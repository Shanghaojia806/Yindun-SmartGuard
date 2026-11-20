package com.silvershield.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 银盾智鉴 · 扩展语义色。
 *
 * Material 3 的 ColorScheme 只描述通用角色（primary/error…），
 * 本项目还有"安全 / 警示 / 危险 / 中性"这类三端共用的业务语义色，
 * 因此在此集中声明，通过 [LocalSilverShieldColors] 注入，组件统一用
 * [SilverShieldTokens.colors] 读取，避免业务组件硬编码色值。
 */
@Immutable
data class SilverShieldColors(
    val safe: Color,
    val safeContainer: Color,
    val onSafeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
    val accentLight: Color,
    val border: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val surfaceCard: Color,
    val surfaceSubtle: Color,
    val disabledContainer: Color,
    val disabledContent: Color,
    val overlayHover: Color,
    val overlayPressed: Color,
)

internal val LightSilverShieldColors = SilverShieldColors(
    safe = SemanticSafe,
    safeContainer = SemanticSafeContainer,
    onSafeContainer = Color(0xFF17553D),
    warning = SemanticWarning,
    warningContainer = SemanticWarningContainer,
    onWarningContainer = Color(0xFF6B4A08),
    danger = SemanticDanger,
    dangerContainer = SemanticDangerContainer,
    onDangerContainer = Color(0xFF7A2020),
    neutral = SemanticNeutral,
    neutralContainer = SemanticNeutralContainer,
    onNeutralContainer = Color(0xFF3B4C58),
    accentLight = BrandAccentLight,
    border = BorderLight,
    divider = DividerLight,
    textPrimary = TextPrimaryLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    surfaceCard = SurfaceCardLight,
    surfaceSubtle = SurfaceVariantLight,
    disabledContainer = DisabledContainerLight,
    disabledContent = DisabledContentLight,
    overlayHover = HoverOverlayLight,
    overlayPressed = PressedOverlayLight,
)

internal val DarkSilverShieldColors = SilverShieldColors(
    safe = Color(0xFF5FBE92),
    safeContainer = Color(0xFF17382B),
    onSafeContainer = Color(0xFFB6E5CE),
    warning = Color(0xFFE3B65C),
    warningContainer = Color(0xFF3A2E13),
    onWarningContainer = Color(0xFFF6E2B8),
    danger = Color(0xFFE07A7A),
    dangerContainer = Color(0xFF3D1E1E),
    onDangerContainer = Color(0xFFF7C9C9),
    neutral = Color(0xFF93A7B4),
    neutralContainer = Color(0xFF24343F),
    onNeutralContainer = Color(0xFFD3E0E8),
    accentLight = Color(0xFF123247),
    border = BorderDark,
    divider = DividerDark,
    textPrimary = TextPrimaryDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    surfaceCard = SurfaceCardDark,
    surfaceSubtle = SurfaceVariantDark,
    disabledContainer = DisabledContainerDark,
    disabledContent = DisabledContentDark,
    overlayHover = HoverOverlayDark,
    overlayPressed = PressedOverlayDark,
)

internal val LocalSilverShieldColors = staticCompositionLocalOf { LightSilverShieldColors }

/** 语义色读取入口：`SilverShieldTokens.colors.safe` */
object SilverShieldTokens {
    val colors: SilverShieldColors
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalSilverShieldColors.current
}
