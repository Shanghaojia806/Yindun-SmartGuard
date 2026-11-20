package com.silvershield.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryLight,
    onPrimaryContainer = BrandPrimaryDark,
    secondary = BrandAccent,
    onSecondary = Color.White,
    secondaryContainer = BrandAccentLight,
    onSecondaryContainer = BrandPrimaryDark,
    tertiary = SemanticSafe,
    onTertiary = Color.White,
    background = SurfaceLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceCardLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
    outlineVariant = DividerLight,
    error = SemanticDanger,
    onError = Color.White,
    errorContainer = SemanticDangerContainer,
    onErrorContainer = SemanticDangerContainer,
    surfaceTint = BrandPrimary,
)

private val DarkColors = darkColorScheme(
    primary = BrandAccent,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryDark,
    onPrimaryContainer = BrandPrimaryLight,
    secondary = BrandPrimaryLight,
    onSecondary = BrandPrimaryDark,
    secondaryContainer = Color(0xFF123247),
    onSecondaryContainer = BrandPrimaryLight,
    tertiary = Color(0xFF5FBE92),
    onTertiary = Color(0xFF08281B),
    background = SurfaceDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceCardDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    outlineVariant = DividerDark,
    error = Color(0xFFE07A7A),
    onError = Color(0xFF3D1E1E),
    errorContainer = Color(0xFF3D1E1E),
    onErrorContainer = Color(0xFFF7C9C9),
    surfaceTint = BrandAccent,
)

/**
 * 应用主题。
 *
 * 不启用动态取色，保证品牌色在三端一致（V1.3 视觉规范）；
 * 同时注入 [SilverShieldTokens.colors] 扩展语义色，供业务组件读取安全/警示/危险/中性等状态色。
 */
@Composable
fun SilverShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extendedColors = if (darkTheme) DarkSilverShieldColors else LightSilverShieldColors

    CompositionLocalProvider(LocalSilverShieldColors provides extendedColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = SilverShieldTypography,
            shapes = SilverShieldShapes,
            content = content,
        )
    }
}
