package com.silvershield.app.ui.family

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.silvershield.app.family.FamilyMessage
import com.silvershield.app.family.FamilyMessageType
import com.silvershield.app.family.FamilyNetworkMode
import com.silvershield.app.family.FamilyRiskUiState
import com.silvershield.app.family.MessageDeliveryState
import com.silvershield.app.ui.components.EmptyState
import com.silvershield.app.ui.components.ErrorState
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.PrivacyNote
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.components.formatRiskTime
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * F11 家属端消息中心。
 *
 * 消息全部来自本地 Mock Repository，并不代表真实推送；网络状态开关只用于演示
 * 延迟与离线，离线时不会把未同步消息显示为「已送达」。
 */
@Composable
fun FamilyMessagesScreen(
    uiState: FamilyRiskUiState,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onSetNetworkMode: (FamilyNetworkMode) -> Unit,
    onOpenEvent: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors

    when {
        uiState.loadFailed -> {
            ErrorState(
                kind = com.silvershield.app.ui.components.ErrorKind.Load,
                title = "消息加载失败",
                description = "暂时无法读取本地消息，请重试。",
                onRetry = onRetry,
                modifier = modifier.fillMaxSize(),
            )
            return
        }

        uiState.loading && uiState.messages.isEmpty() && uiState.relation == null -> {
            LoadingState(message = "正在加载本地消息…", modifier = modifier.fillMaxSize())
            return
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Spacing.pageHorizontal,
            end = Spacing.pageHorizontal,
            top = Spacing.pageVertical,
            bottom = Spacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "消息中心",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = if (uiState.unreadMessageCount > 0) {
                            "${uiState.unreadMessageCount} 条未读消息"
                        } else {
                            "没有未读消息"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                    )
                }
                TextButton(
                    onClick = onMarkAllRead,
                    enabled = uiState.unreadMessageCount > 0,
                ) {
                    Text(text = "全部标记已读")
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = "模拟网络状态",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.textSecondary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    FamilyNetworkMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.networkMode == mode,
                            onClick = { onSetNetworkMode(mode) },
                            label = {
                                Text(text = mode.label, style = MaterialTheme.typography.labelMedium)
                            },
                        )
                    }
                }
            }
        }

        if (uiState.networkMode != FamilyNetworkMode.Online) {
            item {
                StatusCard(
                    title = "当前为${uiState.networkMode.label}状态（演示）",
                    description = if (uiState.networkMode == FamilyNetworkMode.Offline) {
                        "离线期间的新消息会标记为「离线未送达」，切回正常同步后才会变为已送达。"
                    } else {
                        "弱网期间的新消息会显示为「延迟中」，不代表已经送达。"
                    },
                    tone = StatusTone.Warning,
                    statusLabel = "消息投递",
                )
            }
        }

        if (uiState.messages.isEmpty()) {
            item {
                EmptyState(
                    title = "暂无消息",
                    description = "新分享、撤销、关系与授权变化、协同处理更新都会在这里生成本地模拟消息。",
                    actionLabel = "刷新",
                    onAction = onRetry,
                )
            }
        }

        items(uiState.messages, key = { it.messageId }) { message ->
            FamilyMessageCard(
                message = message,
                relatedShareRevoked = message.relatedShareId != null &&
                    message.relatedShareId in uiState.revokedShareIds,
                onClick = {
                    if (!message.isRead) onMarkRead(message.messageId)
                    message.relatedShareId?.let(onOpenEvent)
                },
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "消息说明",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                SampleTag()
            }
            Spacer(Modifier.height(Spacing.xs))
            PrivacyNote(
                "本轮消息通过本地 Mock Repository 生成，未接入真实推送服务；消息状态只用于演示延迟与离线。",
            )
        }
    }
}

@Composable
private fun FamilyMessageCard(
    message: FamilyMessage,
    relatedShareRevoked: Boolean,
    onClick: () -> Unit,
) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.lg),
        color = if (message.isRead) MaterialTheme.colorScheme.surface else tokens.surfaceSubtle,
        border = BorderStroke(Sizes.borderWidth, tokens.border),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (message.isRead) FontWeight.Medium else FontWeight.Bold,
                    color = tokens.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (!message.isRead) {
                    Text(
                        text = "未读",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.danger,
                    )
                }
            }
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.messageType.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textTertiary,
                )
                Text(
                    text = formatRiskTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textTertiary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = message.deliveryState.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (message.deliveryState) {
                        MessageDeliveryState.Delivered -> tokens.safe
                        MessageDeliveryState.Delayed -> tokens.warning
                        MessageDeliveryState.Undelivered -> tokens.danger
                    },
                )
            }
            if (relatedShareRevoked || message.messageType == FamilyMessageType.RiskRevoked) {
                Text(
                    text = "相关事件已撤销或已失效，详情不再可见。",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.danger,
                )
            }
            if (message.isMock) {
                Text(
                    text = "本地模拟消息",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textTertiary,
                )
            }
        }
    }
}
