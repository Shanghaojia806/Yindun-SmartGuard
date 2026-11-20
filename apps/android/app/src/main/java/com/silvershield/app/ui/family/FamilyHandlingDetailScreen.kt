package com.silvershield.app.ui.family

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.silvershield.app.family.FamilyEventUiState
import com.silvershield.app.family.HandlingRecord
import com.silvershield.app.family.LocalFamilyAccounts
import com.silvershield.app.ui.components.EmptyState
import com.silvershield.app.ui.components.FamilySection
import com.silvershield.app.ui.components.InfoRow
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.components.PrivacyNote
import com.silvershield.app.ui.components.RiskBadge
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.components.formatRiskTime
import com.silvershield.app.ui.components.toRiskLevel
import com.silvershield.app.ui.components.toneColors
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * F08 处理详情。
 *
 * 展示原始风险事件摘要（只读）、AI 风险等级、当前协同状态、完整操作时间线与历史备注。
 * 授权撤销后立即停止展示受限详情。
 */
@Composable
fun FamilyHandlingDetailScreen(
    uiState: FamilyEventUiState,
    onOpenCooperate: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors

    if (uiState.loading && uiState.share == null && uiState.accessDenied == null) {
        LoadingState(message = "正在读取处理历史…", modifier = modifier.fillMaxSize())
        return
    }

    val denied = uiState.accessDenied
    if (denied != null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.pageHorizontal, vertical = Spacing.pageVertical),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            StatusCard(
                title = "授权已失效，无法查看处理详情",
                description = denied.message,
                tone = StatusTone.Danger,
                statusLabel = "授权校验",
                actionLabel = "重新检查",
                onAction = onRetry,
            )
            PrivacyNote("历史处理记录仍保存在本机用于审计，但受限事件详情不再展示。")
        }
        return
    }

    val event = uiState.event ?: return
    val share = uiState.share ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.pageHorizontal, vertical = Spacing.pageVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        Text(
            text = "处理详情",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = tokens.textPrimary,
        )

        FamilySection(title = "原始风险事件摘要") {
            InfoRow(label = "事件标题", value = event.title)
            InfoRow(label = "风险类型", value = event.riskType.label)
            InfoRow(
                label = "AI 风险等级",
                value = "${event.riskLevel.label}（系统判断，不可修改）",
            )
            InfoRow(label = "发生时间", value = formatRiskTime(event.occurredAt))
            InfoRow(label = "分享时间", value = formatRiskTime(share.sharedAt))
        }
        RiskBadge(category = event.riskType.label, level = event.riskLevel.toRiskLevel())

        FamilySection(title = "当前处理状态") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = "协同处理状态",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                HandlingStatusChip(status = uiState.handlingStatus)
            }
            InfoRow(label = "当前处理人", value = uiState.currentHandler ?: "暂无处理人")
            InfoRow(
                label = "最近一次处理时间",
                value = uiState.lastHandledAt?.let(::formatRiskTime) ?: "暂无处理记录",
            )
        }

        FamilySection(
            title = "操作时间线",
            subtitle = "每次状态变化都会追加记录，不覆盖历史",
        ) {
            if (uiState.history.isEmpty()) {
                EmptyState(
                    title = "还没有协同处理记录",
                    description = "可以先联系老人，再记录处理状态与核实结果。",
                )
            } else {
                uiState.history.forEach { record ->
                    HandlingTimelineItem(record = record)
                }
            }
        }

        PrimaryButton(
            text = "继续更新处理状态",
            onClick = { onOpenCooperate(share.shareId) },
        )

        PrivacyNote(
            "家属只能追加自己的协同处理记录，不能修改 AI 原始判断，也不能删除老人的原始风险事件。",
        )
    }
}

@Composable
private fun HandlingTimelineItem(
    record: HandlingRecord,
) {
    val tokens = SilverShieldTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(top = Spacing.sm)
                .size(Sizes.iconXs)
                .background(
                    color = record.handlingStatus.tone().toneColors().accent,
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
        )
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = Spacing.xs),
            shape = RoundedCornerShape(Radius.md),
            color = tokens.surfaceSubtle,
            border = BorderStroke(Sizes.borderWidth, tokens.border),
        ) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    HandlingStatusChip(status = record.handlingStatus)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatRiskTime(record.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textTertiary,
                    )
                }
                Text(
                    text = "操作人：${
                        if (record.handlerUserId == LocalFamilyAccounts.FAMILY_USER_ID) {
                            "家属（本人）"
                        } else {
                            record.handlerUserId
                        }
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                Text(
                    text = "变更：${record.previousStatus?.label ?: "无"} → ${record.handlingStatus.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                if (record.note.isNotBlank()) {
                    Text(
                        text = "备注：${record.note}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary,
                    )
                }
                if (record.isMock) {
                    Text(
                        text = "模拟数据",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textTertiary,
                    )
                }
            }
        }
    }
}
