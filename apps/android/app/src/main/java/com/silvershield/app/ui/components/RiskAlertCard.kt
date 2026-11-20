package com.silvershield.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.guard.risk.MockRiskEvents
import com.silvershield.app.guard.risk.RiskEvent
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * E06 风险提醒组件（应用内触达）。
 *
 * 两档强度，由事件的 AI 判断等级决定（与用户处理状态无关）：
 * - 重点提醒：[RiskEvent.isUrgent] 为 true 时使用，危险色 + 图标 + 明确文字，附语音播报入口；
 * - 轻度提醒：其余情况使用，警示色 + 图标，布局紧凑、低干扰。
 *
 * 无障碍与合规约束：
 * - 等级同时使用图标与文字，不依赖颜色单独传达；
 * - 不使用恐吓性措辞，不夸大 AI 判断；
 * - [onSpeak] 为 null 时不显示语音入口（对应 E13「语音提醒」关闭）；
 * - 模拟事件显著标记「模拟数据」。
 *
 * @param onSpeak 语音播报回调；null 表示不提供语音入口
 */
@Composable
fun RiskAlertCard(
    event: RiskEvent,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
    onSpeak: (() -> Unit)? = null,
) {
    if (event.isUrgent) {
        UrgentRiskAlert(
            event = event,
            onOpenDetail = onOpenDetail,
            onSpeak = onSpeak,
            modifier = modifier,
        )
    } else {
        LightRiskAlert(
            event = event,
            onOpenDetail = onOpenDetail,
            modifier = modifier,
        )
    }
}

/** 重点提醒：老人需要及时关注的风险。 */
@Composable
private fun UrgentRiskAlert(
    event: RiskEvent,
    onOpenDetail: () -> Unit,
    onSpeak: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val toneColors = StatusTone.Danger.toneColors()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append("重点提醒，")
                    append(event.riskType.label)
                    append("，")
                    append(event.title)
                    append("。")
                    append(event.description)
                    if (event.isMock) append("，模拟数据")
                }
            },
        shape = RoundedCornerShape(Radius.lg),
        color = toneColors.container,
        border = BorderStroke(Sizes.borderWidth, toneColors.accent.copy(alpha = 0.40f)),
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = toneColors.accent,
                    modifier = Modifier.size(Sizes.iconSm),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "重点提醒 · ${event.riskType.label} · ${event.riskLevel.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = toneColors.accent,
                )
                if (event.isMock) {
                    Spacer(Modifier.width(Spacing.xs))
                    SampleTag()
                }
            }

            Spacer(Modifier.heightIn(min = Spacing.xxs))
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleLarge,
                color = toneColors.onContainer,
            )
            Spacer(Modifier.heightIn(min = Spacing.xxs))
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                TextButton(
                    onClick = onOpenDetail,
                    modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
                ) {
                    Text(text = "查看详情", style = MaterialTheme.typography.labelLarge)
                }
                if (onSpeak != null) {
                    TextButton(
                        onClick = onSpeak,
                        modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
                    ) {
                        Text(text = "语音提醒", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** 轻度提醒：存在可疑线索，简洁、低干扰。 */
@Composable
private fun LightRiskAlert(
    event: RiskEvent,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val toneColors = StatusTone.Warning.toneColors()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append("提醒，")
                    append(event.riskType.label)
                    append("，")
                    append(event.title)
                    if (event.isMock) append("，模拟数据")
                }
            },
        shape = RoundedCornerShape(Radius.md),
        color = tokens.surfaceSubtle,
        border = BorderStroke(Sizes.borderWidth, tokens.border),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = toneColors.accent,
                modifier = Modifier.size(Sizes.iconSm),
            )
            Spacer(Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary,
                    )
                    if (event.isMock) {
                        Spacer(Modifier.width(Spacing.xs))
                        SampleTag()
                    }
                }
                Text(
                    text = "${event.riskLevel.label} · ${event.riskType.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
            }
            TextButton(
                onClick = onOpenDetail,
                modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
            ) {
                Text(text = "查看详情", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Preview(name = "风险提醒 · 重点提醒", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun RiskAlertCardUrgentPreview() {
    SilverShieldTheme {
        Column(modifier = Modifier.padding(Spacing.md)) {
            RiskAlertCard(
                event = MockRiskEvents.initial(nowMillis = 1_700_000_000_000L)[0],
                onOpenDetail = {},
                onSpeak = {},
            )
        }
    }
}

@Preview(name = "风险提醒 · 轻度提醒", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun RiskAlertCardLightPreview() {
    SilverShieldTheme {
        Column(modifier = Modifier.padding(Spacing.md)) {
            RiskAlertCard(
                event = MockRiskEvents.initial(nowMillis = 1_700_000_000_000L)[2],
                onOpenDetail = {},
            )
        }
    }
}

@Preview(name = "风险提醒 · 字体放大 2.0x", fontScale = 2f, showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun RiskAlertCardLargeFontPreview() {
    SilverShieldTheme {
        Column(modifier = Modifier.padding(Spacing.md)) {
            RiskAlertCard(
                event = MockRiskEvents.initial(nowMillis = 1_700_000_000_000L)[0],
                onOpenDetail = {},
                onSpeak = {},
            )
        }
    }
}
