package com.silvershield.app.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silvershield.app.ui.components.RiskLevel
import com.silvershield.app.ui.components.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设计系统 Token 的纯 JVM 单元测试。
 *
 * 只校验可静态断言的约束，不涉及 Android 框架与 Compose 渲染：
 * 1. 适老化尺寸下限（触控 48dp、主按钮 56–64dp）；
 * 2. 字号阶梯（正文 18sp、页面主标题 24–28sp）；
 * 3. 间距与圆角阶梯单调递增；
 * 4. 风险等级到语义色的映射互不重复（保证四种等级在视觉上可区分）。
 */
class DesignTokensTest {

    @Test
    fun touchTarget_meetsAccessibilityMinimum() {
        assertTrue(
            "最小触控区域不得小于 48dp",
            Sizes.touchTargetMin >= 48.dp,
        )
    }

    @Test
    fun primaryButtonHeight_withinElderlyFriendlyRange() {
        assertTrue(
            "主要按钮高度应不低于 56dp",
            Sizes.primaryButtonHeight >= 56.dp,
        )
        assertTrue(
            "主要按钮高度应不高于 64dp",
            Sizes.primaryButtonHeight <= 64.dp,
        )
    }

    @Test
    fun bodyFontSize_matchesBaseline() {
        assertEquals(
            "正文基础字号应为 18sp",
            18.sp,
            SilverShieldTypography.bodyLarge!!.fontSize,
        )
    }

    @Test
    fun headlineFontSize_withinPageTitleRange() {
        val headline = SilverShieldTypography.headlineLarge!!.fontSize
        assertTrue(
            "页面主标题字号应在 24–28sp 之间，实际为 $headline",
            headline >= 24.sp && headline <= 28.sp,
        )
    }

    @Test
    fun spacingScale_isStrictlyIncreasing() {
        val scale = listOf(
            Spacing.xxs, Spacing.xs, Spacing.sm, Spacing.md, Spacing.lg,
            Spacing.xl, Spacing.xxl, Spacing.xxxl,
        )
        scale.zipWithNext().forEach { (small, large) ->
            assertTrue("间距阶梯必须单调递增：$small -> $large", small < large)
        }
    }

    @Test
    fun radiusScale_isStrictlyIncreasing() {
        val scale = listOf(Radius.sm, Radius.md, Radius.lg, Radius.xl)
        scale.zipWithNext().forEach { (small, large) ->
            assertTrue("圆角阶梯必须单调递增：$small -> $large", small < large)
        }
    }

    @Test
    fun riskLevels_mapToDistinctTones() {
        assertEquals(StatusTone.Safe, RiskLevel.Low.tone)
        assertEquals(StatusTone.Warning, RiskLevel.Medium.tone)
        assertEquals(StatusTone.Danger, RiskLevel.High.tone)
        assertEquals(StatusTone.Neutral, RiskLevel.Unknown.tone)

        val tones = RiskLevel.entries.map { it.tone }
        assertEquals("每个风险等级必须对应不同的语义色", tones.size, tones.toSet().size)
    }
}
