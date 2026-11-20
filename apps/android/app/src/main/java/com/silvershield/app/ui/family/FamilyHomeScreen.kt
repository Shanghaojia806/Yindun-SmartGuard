package com.silvershield.app.ui.family

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.silvershield.app.family.FamilyRelationStatus
import com.silvershield.app.family.FamilyRiskUiState
import com.silvershield.app.family.FamilyNetworkMode
import com.silvershield.app.ui.components.EmptyState
import com.silvershield.app.ui.components.ErrorState
import com.silvershield.app.ui.components.FamilySection
import com.silvershield.app.ui.components.InfoRow
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.components.PrivacyNote
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusInline
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.components.formatRiskTime
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * F03 家庭首页。
 *
 * 所有数字都来自 [FamilyRiskUiState.items]，而 items 已经过 Repository 三层授权校验，
 * 未授权事件不会出现在统计或待处理事项中。
 */
@Composable
fun FamilyHomeScreen(
    uiState: FamilyRiskUiState,
    onOpenBind: () -> Unit,
    onOpenRelation: () -> Unit,
    onOpenRisks: () -> Unit,
    onOpenCooperation: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onOpenCooperate: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors

    when {
        uiState.loadFailed -> {
            ErrorState(
                kind = com.silvershield.app.ui.components.ErrorKind.Load,
                title = "家庭数据加载失败",
                description = "暂时无法读取本机共享记录，请重试。",
                onRetry = onRetry,
                modifier = modifier.fillMaxSize(),
            )
            return
        }

        uiState.loading && uiState.relation == null && uiState.items.isEmpty() -> {
            LoadingState(
                message = "正在读取家庭数据…",
                modifier = modifier.fillMaxSize(),
            )
            return
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.pageHorizontal, vertical = Spacing.pageVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        if (uiState.networkMode != FamilyNetworkMode.Online) {
            StatusCard(
                title = "当前为${uiState.networkMode.label}状态（演示）",
                description = "页面只显示本机缓存内容；新消息不会被标记为已送达。",
                tone = StatusTone.Warning,
                statusLabel = "模拟网络",
            )
        }

        FamilyMemberSection(
            uiState = uiState,
            onOpenBind = onOpenBind,
            onOpenRelation = onOpenRelation,
        )

        when {
            !uiState.isBound -> {
                StatusCard(
                    title = "尚未绑定老人",
                    description = "完成双向确认的绑定后，老人才可以单独授权分享风险事件。",
                    tone = StatusTone.Neutral,
                    statusLabel = "家庭关系",
                    actionLabel = "去绑定老人",
                    onAction = onOpenBind,
                )
            }

            !uiState.hasConsent -> {
                if (uiState.revokedShareCount > 0) {
                    StatusCard(
                        title = "共享授权已撤销",
                        description = "老人已经撤销了风险事件共享。已撤销的事件不会继续显示，历史处理记录仍保留在本机。",
                        tone = StatusTone.Danger,
                        statusLabel = "授权状态",
                        actionLabel = "查看消息中心",
                        onAction = onOpenMessages,
                    )
                } else {
                    StatusCard(
                        title = "目前没有获得可查看的风险事件共享授权。",
                        description = "家庭绑定不代表自动授权。请由老人本人在老人端单独设置共享范围，你无法代替老人开启。",
                        tone = StatusTone.Neutral,
                        statusLabel = "授权状态",
                    )
                }
            }
        }

        if (uiState.isBound && uiState.hasConsent) {
            RiskOverviewSection(uiState = uiState)
            PendingSection(
                uiState = uiState,
                onOpenRisks = onOpenRisks,
                onOpenCooperation = onOpenCooperation,
            )
            RecentSharedSection(
                uiState = uiState,
                onOpenEvent = onOpenEvent,
                onOpenCooperate = onOpenCooperate,
                onOpenRisks = onOpenRisks,
            )
        }

        MessageEntry(
            unreadCount = uiState.unreadMessageCount,
            onClick = onOpenMessages,
        )

        if (uiState.isBound && uiState.hasConsent && uiState.items.isEmpty()) {
            EmptyState(
                title = "老人还没有分享风险事件",
                description = "授权已经生效，但老人尚未主动分享任何事件。这里不会使用虚构数据填充。",
                actionLabel = "查看共享授权说明",
                onAction = onOpenRelation,
            )
        }

        if (uiState.allHandled) {
            StatusCard(
                title = "所有共享事件都已协助处理",
                description = "你可以在风险列表中回看处理历史；新的分享到达后会在这里提醒。",
                tone = StatusTone.Safe,
                statusLabel = "协同进度",
                actionLabel = "查看风险列表",
                onAction = onOpenRisks,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "数据说明",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.textSecondary,
                modifier = Modifier.weight(1f),
            )
            SampleTag()
        }
        PrivacyNote(
            "共享内容仅限老人授权范围内的事件摘要与必要证据。家属不能查看原始屏幕录像、实时屏幕或完整浏览历史。",
        )
    }
}

@Composable
private fun FamilyMemberSection(
    uiState: FamilyRiskUiState,
    onOpenBind: () -> Unit,
    onOpenRelation: () -> Unit,
) {
    val tokens = SilverShieldTokens.colors
    val relation = uiState.relation
    FamilySection(
        title = "家庭成员",
        subtitle = "绑定关系与共享授权相互独立",
    ) {
        when {
            relation == null -> {
                InfoRow(label = "绑定状态", value = "尚未建立家庭关系")
                PrimaryButton(text = "去绑定老人", onClick = onOpenBind)
            }

            relation.relationStatus == FamilyRelationStatus.ACTIVE -> {
                InfoRow(label = "已绑定老人", value = relation.elderDisplayName)
                InfoRow(label = "家属", value = relation.familyDisplayName)
                StatusInline(text = "家庭关系：已建立", tone = StatusTone.Safe)
                Text(
                    text = "绑定时间：${formatRiskTime(relation.confirmedAt ?: relation.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                StatusInline(
                    text = "共享授权：${uiState.consentSummary}",
                    tone = if (uiState.hasConsent) StatusTone.Safe else StatusTone.Neutral,
                )
                OutlinedButton(
                    onClick = onOpenRelation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.primaryButtonHeight),
                ) {
                    Text(text = "关系管理", style = MaterialTheme.typography.labelLarge)
                }
            }

            relation.relationStatus == FamilyRelationStatus.PENDING -> {
                InfoRow(label = "绑定状态", value = "等待对方确认")
                InfoRow(
                    label = "邀请有效期",
                    value = "至 ${formatRiskTime(relation.expiresAt)}",
                )
                OutlinedButton(
                    onClick = onOpenRelation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.primaryButtonHeight),
                ) {
                    Text(text = "查看关联关系", style = MaterialTheme.typography.labelLarge)
                }
            }

            else -> {
                InfoRow(label = "家庭关系状态", value = relation.relationStatus.label)
                Text(
                    text = "关系已失效，相关共享访问已停止。可以重新发起绑定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
                PrimaryButton(text = "重新绑定老人", onClick = onOpenBind)
            }
        }
    }
}

@Composable
private fun RiskOverviewSection(uiState: FamilyRiskUiState) {
    FamilySection(
        title = "风险概况",
        subtitle = "仅统计当前授权范围内、关系有效的事件",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FamilyStatCard(
                label = "已授权共享",
                value = uiState.sharedCount,
                tone = StatusTone.Info,
                modifier = Modifier.weight(1f),
            )
            FamilyStatCard(
                label = "待查看",
                value = uiState.unreadEventCount,
                tone = StatusTone.Warning,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FamilyStatCard(
                label = "待处理",
                value = uiState.pendingHandlingCount,
                tone = StatusTone.Warning,
                modifier = Modifier.weight(1f),
            )
            FamilyStatCard(
                label = "已协助处理",
                value = uiState.assistedCount,
                tone = StatusTone.Safe,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PendingSection(
    uiState: FamilyRiskUiState,
    onOpenRisks: () -> Unit,
    onOpenCooperation: () -> Unit,
) {
    val total = uiState.newSharedCount + uiState.viewedPendingCount +
        uiState.verifyingCount + uiState.contactedCount
    FamilySection(
        title = "待处理事项",
        subtitle = if (total == 0) "当前没有需要跟进的事项" else "按处理阶段分组，点击进入对应页面",
    ) {
        PendingTodoRow(
            label = "新共享且未查看",
            count = uiState.newSharedCount,
            onClick = onOpenRisks,
        )
        PendingTodoRow(
            label = "已查看待处理",
            count = uiState.viewedPendingCount,
            onClick = onOpenRisks,
        )
        PendingTodoRow(
            label = "正在核实",
            count = uiState.verifyingCount,
            onClick = onOpenCooperation,
        )
        PendingTodoRow(
            label = "等待进一步协助",
            count = uiState.contactedCount,
            onClick = onOpenCooperation,
        )
        if (total == 0) {
            StatusInline(text = "暂无待处理事项", tone = StatusTone.Safe)
        }
    }
}

@Composable
private fun RecentSharedSection(
    uiState: FamilyRiskUiState,
    onOpenEvent: (String) -> Unit,
    onOpenCooperate: (String) -> Unit,
    onOpenRisks: () -> Unit,
) {
    if (uiState.items.isEmpty()) return
    FamilySection(
        title = "最近共享的风险事件",
        subtitle = "仅显示老人主动分享且授权仍有效的事件",
    ) {
        uiState.items.take(3).forEach { item ->
            FamilyRiskEventCard(
                item = item,
                onClick = { onOpenEvent(item.share.shareId) },
                actionLabel = "协同处理",
                onAction = { onOpenCooperate(item.share.shareId) },
            )
        }
        OutlinedButton(
            onClick = { onOpenRisks() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.primaryButtonHeight),
        ) {
            Text(text = "查看全部风险事件", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MessageEntry(
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.borderWidth, tokens.border),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "消息中心",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = "分享、撤销、关系与授权变化、协同处理更新",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
            }
            Text(
                text = if (unreadCount > 0) "$unreadCount 条未读" else "暂无未读",
                style = MaterialTheme.typography.labelLarge,
                color = if (unreadCount > 0) tokens.danger else tokens.textTertiary,
            )
        }
    }
}
