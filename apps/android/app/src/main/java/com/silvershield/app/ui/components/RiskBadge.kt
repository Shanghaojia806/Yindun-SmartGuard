package com.silvershield.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.silvershield.app.R
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * 风险等级。
 *
 * 【待与 V1.3 原文核对】V1.3 只定义了风险颜色（安全/警示/危险）与"轻提醒/重点提醒"的
 * 提示方式，未给出正式的风险等级枚举与判定阈值。此处按产品基线预留三级 + 待评估态，
 * 接入后端后以接口返回字段为准，不在 UI 层推导等级。
 */
enum class RiskLevel(
    @StringRes val labelRes: Int,
    val tone: StatusTone,
) {
    Low(R.string.risk_level_low, StatusTone.Safe),
    Medium(R.string.risk_level_medium, StatusTone.Warning),
    High(R.string.risk_level_high, StatusTone.Danger),
    Unknown(R.string.risk_level_unknown, StatusTone.Neutral),
}

/**
 * 风险标签：同时展示风险类别与风险等级。
 *
 * 无障碍要求：图标 + 文字双重表达，不单独依赖颜色；读屏会朗读"类别，等级"。
 *
 * @param category 风险类别文案（例如"冒充公检法""虚假投资理财"），由业务层提供
 * @param level 风险等级，颜色与图标由设计系统统一映射
 */
@Composable
fun RiskBadge(
    category: String,
    level: RiskLevel,
    modifier: Modifier = Modifier,
) {
    val toneColors = level.tone.toneColors()
    val levelLabel = stringResource(level.labelRes)

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = Sizes.touchTargetMin)
            .semantics { contentDescription = "$category，$levelLabel" },
        shape = RoundedCornerShape(Radius.pill),
        color = toneColors.container,
        border = BorderStroke(Sizes.borderWidth, toneColors.accent.copy(alpha = 0.36f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                imageVector = level.icon(),
                contentDescription = null,
                tint = toneColors.accent,
                modifier = Modifier.size(Sizes.iconXs),
            )
            Spacer(Modifier.width(Spacing.xxs + 2.dp))
            Text(
                text = category,
                style = MaterialTheme.typography.labelMedium,
                color = toneColors.onContainer,
            )
            Text(
                text = " · ",
                style = MaterialTheme.typography.labelMedium,
                color = toneColors.accent,
            )
            Text(
                text = levelLabel,
                style = MaterialTheme.typography.labelMedium,
                color = toneColors.accent,
            )
        }
    }
}

private fun RiskLevel.icon(): ImageVector = when (this) {
    RiskLevel.Low -> Icons.Filled.CheckCircle
    RiskLevel.Medium -> Icons.Filled.Warning
    RiskLevel.High -> Icons.Filled.Warning
    RiskLevel.Unknown -> Icons.Filled.Info
}

@Preview(name = "风险标签 · 全部等级", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun RiskBadgePreview() {
    SilverShieldTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            RiskBadge(category = "冒充公检法", level = RiskLevel.High)
            RiskBadge(category = "虚假投资理财", level = RiskLevel.Medium)
            RiskBadge(category = "正常内容", level = RiskLevel.Low)
            RiskBadge(category = "待识别内容", level = RiskLevel.Unknown)
        }
    }
}

@Preview(name = "风险标签 · 字体放大 2.0x", fontScale = 2f, showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun RiskBadgeLargeFontPreview() {
    SilverShieldTheme {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(Spacing.md)) {
            RiskBadge(category = "冒充公检法", level = RiskLevel.High)
        }
    }
}
