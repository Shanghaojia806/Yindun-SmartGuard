package com.silvershield.app.ui.family

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.silvershield.app.family.FamilyEventItem
import com.silvershield.app.family.HandlingStatus
import com.silvershield.app.ui.components.RiskBadge
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.components.formatRiskTime
import com.silvershield.app.ui.components.toRiskLevel
import com.silvershield.app.ui.components.toneColors
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/** 协同处理状态 → 语义色。 */
fun HandlingStatus.tone(): StatusTone = when (this) {
    HandlingStatus.PendingView -> StatusTone.Neutral
    HandlingStatus.Viewed -> StatusTone.Info
    HandlingStatus.Contacted -> StatusTone.Info
    HandlingStatus.Verifying -> StatusTone.Warning
    HandlingStatus.Assisted -> StatusTone.Safe
    HandlingStatus.Unverifiable -> StatusTone.Warning
}

/** 协同处理状态标签：图标色块 + 文字，不只依赖颜色。 */
@Composable
fun HandlingStatusChip(
    status: HandlingStatus,
    modifier: Modifier = Modifier,
) {
    val toneColors = status.tone().toneColors()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.pill),
        color = toneColors.container,
        border = BorderStroke(Sizes.borderWidth, toneColors.accent.copy(alpha = 0.32f)),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = toneColors.accent,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )
    }
}

/** F05 列表事件卡片。 */
@Composable
fun FamilyRiskEventCard(
    item: FamilyEventItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = SilverShieldTokens.colors
    val event = item.event
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.borderWidth, tokens.border),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (event.isMock) {
                    Spacer(Modifier.width(Spacing.xs))
                    SampleTag()
                }
            }
            RiskBadge(
                category = event.riskType.label,
                level = event.riskLevel.toRiskLevel(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "发生时间",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textTertiary,
                    )
                    Text(
                        text = formatRiskTime(event.occurredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "分享时间",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textTertiary,
                    )
                    Text(
                        text = formatRiskTime(item.share.sharedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (item.share.isRead) "已查看" else "未查看",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.share.isRead) tokens.textSecondary else tokens.warning,
                )
                Spacer(Modifier.weight(1f))
                HandlingStatusChip(status = item.handlingStatus)
            }
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.primaryButtonHeight),
                ) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 首页统计卡。 */
@Composable
fun FamilyStatCard(
    label: String,
    value: Int,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val toneColors = tone.toneColors()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.md),
        color = toneColors.container,
        border = BorderStroke(Sizes.borderWidth, toneColors.accent.copy(alpha = 0.24f)),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = toneColors.accent,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
            )
        }
    }
}

/** 待处理事项入口行。 */
@Composable
fun PendingTodoRow(
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        color = tokens.surfaceSubtle,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$count 条",
                style = MaterialTheme.typography.labelLarge,
                color = if (count > 0) tokens.danger else tokens.textTertiary,
            )
        }
    }
}
