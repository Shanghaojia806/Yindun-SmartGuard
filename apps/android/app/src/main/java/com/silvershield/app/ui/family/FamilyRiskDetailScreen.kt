package com.silvershield.app.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.silvershield.app.family.FamilyEventUiState
import com.silvershield.app.ui.components.FamilySection
import com.silvershield.app.ui.components.InfoRow
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.components.PrivacyNote
import com.silvershield.app.ui.components.RiskBadge
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.components.formatRiskTime
import com.silvershield.app.ui.components.toRiskLevel
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * F06 家属风险详情。
 *
 * 展示的是系统风险辅助判断，不等于已确认诈骗事实。
 * 授权失效时由 [FamilyEventUiState.accessDenied] 直接拦截，不渲染任何受限详情。
 */
@Composable
fun FamilyRiskDetailScreen(
    uiState: FamilyEventUiState,
    onOpenCooperate: (String) -> Unit,
    onOpenHandling: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors

    if (uiState.loading && uiState.share == null && uiState.accessDenied == null) {
        LoadingState(message = "正在读取共享事件…", modifier = modifier.fillMaxSize())
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
                title = "无法查看这条共享事件",
                description = denied.message,
                tone = StatusTone.Danger,
                statusLabel = "授权校验",
                actionLabel = "重新检查",
                onAction = onRetry,
            )
            PrivacyNote(
                "授权撤销或家庭关系解除后，家属端会立即停止展示受限事件。已保存的历史处理记录仍可在本机审计。",
            )
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = "老人主动分享的风险事件摘要",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
            }
            if (event.isMock) SampleTag()
        }

        RiskBadge(category = event.riskType.label, level = event.riskLevel.toRiskLevel())

        FamilySection(title = "事件信息") {
            InfoRow(label = "风险标题", value = event.title)
            InfoRow(label = "风险类型", value = event.riskType.label)
            InfoRow(
                label = "AI 原始风险等级",
                value = "${event.riskLevel.label}（系统判断，家属不可修改）",
            )
            InfoRow(label = "发生时间", value = formatRiskTime(event.occurredAt))
            InfoRow(label = "老人分享时间", value = formatRiskTime(share.sharedAt))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "当前协同处理状态",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                HandlingStatusChip(status = uiState.handlingStatus)
            }
            InfoRow(
                label = "家属查看状态",
                value = if (share.isRead) "已查看" else "未查看",
            )
            if (share.isRead && share.readAt != null) {
                InfoRow(label = "首次查看时间", value = formatRiskTime(share.readAt))
            }
        }

        FamilySection(title = "风险线索") {
            if (event.clues.isEmpty()) {
                Text(
                    text = "未获取到可用线索。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                )
            } else {
                event.clues.forEach { clue ->
                    Text(
                        text = "• $clue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary,
                    )
                }
            }
        }

        FamilySection(title = "证据摘要") {
            Text(
                text = event.evidenceSummary ?: "未获取证据摘要。",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "可用数据模态：${
                    event.availableModalities.joinToString("、") { it.label }.ifEmpty { "无" }
                }",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
            )
            Text(
                text = "未获取：${
                    event.missingModalities.joinToString("、") { it.label }.ifEmpty { "无" }
                }",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
            )
        }

        StatusCard(
            title = if (event.hasEnoughEvidence) "当前信息相对充分" else "当前信息不足，建议进一步核实。",
            description = if (event.hasEnoughEvidence) {
                "可以结合风险线索与老人沟通核实，但最终仍以实际情况为准。"
            } else {
                "线索或证据模态不足，系统没有足够依据判断诈骗事实。请先联系老人了解情况。"
            },
            tone = if (event.hasEnoughEvidence) StatusTone.Info else StatusTone.Warning,
            statusLabel = "信息充分性",
        )

        FamilySection(title = "处理建议") {
            if (event.suggestedActions.isEmpty()) {
                Text(
                    text = "暂无处理建议。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                )
            } else {
                event.suggestedActions.forEach { action ->
                    Text(
                        text = "• $action",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary,
                    )
                }
            }
        }

        StatusCard(
            title = "这是风险辅助判断，不是诈骗结论",
            description = "系统提示只用于帮助你和老人进一步核实，不等于已经确认诈骗事实。",
            tone = StatusTone.Info,
            statusLabel = "判断说明",
        )

        PrivacyNote(
            "家属不能查看原始屏幕录像或老人实时屏幕；共享内容仅限授权范围内的事件摘要与必要证据。",
        )

        PrimaryButton(
            text = "去协同处理",
            onClick = { onOpenCooperate(share.shareId) },
        )
        OutlinedButton(
            onClick = { onOpenHandling(share.shareId) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.primaryButtonHeight),
        ) {
            Text(text = "查看处理详情与时间线", style = MaterialTheme.typography.labelLarge)
        }

        if (uiState.message != null) {
            Text(
                text = uiState.message,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
            )
        }
    }
}
