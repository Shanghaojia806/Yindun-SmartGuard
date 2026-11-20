package com.silvershield.app.ui.elder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.family.ConsentStatus
import com.silvershield.app.guard.risk.MockRiskEvents
import com.silvershield.app.guard.risk.RiskEvent
import com.silvershield.app.guard.risk.RiskHandleStatus
import com.silvershield.app.guard.risk.RiskLevel
import com.silvershield.app.guard.risk.RiskModality
import com.silvershield.app.ui.components.EmptyState
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.RiskBadge
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.components.displayLabel
import com.silvershield.app.ui.components.formatRiskTime
import com.silvershield.app.ui.components.toRiskLevel
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * E07 风险详情（P06）。
 *
 * 展示基本信息、风险线索、证据摘要与处理建议，并提供本地处理状态与家庭分享前置检查。
 *
 * 合规约束：
 * - 信息不足时明确提示「当前信息不足…」，绝不显示"已确认安全"；
 * - 不展示模型分数，也不把分数解释为诈骗概率；
 * - 未获取的模态标注"未获取"，不编造证据；
 * - 家庭分享以真实 ConsentRecord 为准：未绑定不能分享，未授权不会显示分享成功，
 *   分享只记录事件摘要，不上传原始画面。
 *
 * @param shareStatus 本条事件对家属的共享状态（来自 ConsentRepository）
 */
@Composable
fun ElderRiskDetailScreen(
    uiState: RiskDetailUiState,
    onMarkHandled: () -> Unit,
    onShareEvent: () -> Unit,
    onRevokeShare: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val snackbarHostState = remember { SnackbarHostState() }
    var showShareDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeMessage()
    }

    Box(modifier = modifier.fillMaxSize()) {
        val event = uiState.event
        when {
            uiState.loading -> LoadingState(
                modifier = Modifier.fillMaxSize(),
                message = "正在读取风险详情…",
            )

            event == null -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                title = "记录不存在或已删除",
                description = "这条风险记录可能已被删除，请返回记录页查看其他记录。",
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Spacing.pageHorizontal,
                    vertical = Spacing.pageVertical,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                item(key = "header") {
                    DetailHeader(event = event)
                }

                item(key = "conclusion") {
                    ConclusionCard(event = event)
                }

                item(key = "clues") {
                    DetailSection(title = "识别到的线索") {
                        if (event.clues.isEmpty()) {
                            Text(
                                text = "暂未识别到明确线索。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textPrimary,
                            )
                        } else {
                            event.clues.forEachIndexed { index, clue ->
                                BulletLine(index = index + 1, text = clue)
                            }
                        }
                        Spacer(Modifier.heightIn(min = Spacing.xs))
                        Text(
                            text = "没有发现线索不代表内容安全，涉及转账时请先与家人核实。",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textTertiary,
                        )
                    }
                }

                item(key = "evidence") {
                    DetailSection(title = "证据摘要") {
                        EvidenceContent(event = event)
                    }
                }

                item(key = "suggestions") {
                    DetailSection(title = "处理建议") {
                        event.suggestedActions.forEachIndexed { index, action ->
                            BulletLine(index = index + 1, text = action)
                        }
                    }
                }

                item(key = "handle") {
                    HandleAction(event = event, onMarkHandled = onMarkHandled)
                }

                if (event.isMock) {
                    item(key = "mock-note") {
                        Text(
                            text = "此记录为模拟数据（来源：${event.sourceType.label}，模型版本 ${event.modelVersion}），" +
                                "仅用于验证功能，不代表真实识别结果。",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textTertiary,
                        )
                    }
                }

                item(key = "share") {
                    ShareSection(
                        uiState = uiState,
                        onOpenDialog = { showShareDialog = true },
                        onRevokeShare = onRevokeShare,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showShareDialog) {
        ShareConfirmDialog(
            onConfirm = {
                showShareDialog = false
                onShareEvent()
            },
            onDismiss = { showShareDialog = false },
        )
    }
}

@Composable
private fun DetailHeader(event: RiskEvent) {
    val tokens = SilverShieldTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (event.isMock) {
                SampleTag()
            }
            Text(
                text = "当前状态：${event.handleStatus.displayLabel()}",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.textSecondary,
            )
        }
        Text(
            text = event.title,
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.textPrimary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            RiskBadge(
                category = event.riskType.label,
                level = event.riskLevel.toRiskLevel(),
            )
        }
        Text(
            text = "发生时间：${formatRiskTime(event.occurredAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textTertiary,
        )
    }
}

@Composable
private fun ConclusionCard(event: RiskEvent) {
    when {
        event.isInformationInsufficient -> StatusCard(
            title = "信息不足，暂无法确认",
            description = "当前信息不足，暂时无法作出可靠判断，请进一步核实。",
            tone = StatusTone.Warning,
            statusLabel = "待核实",
        )

        event.riskLevel == RiskLevel.High -> StatusCard(
            title = "需要重点留意",
            description = event.description,
            tone = StatusTone.Danger,
            statusLabel = "重点提醒",
        )

        else -> StatusCard(
            title = "发现可疑线索",
            description = event.description,
            tone = StatusTone.Warning,
            statusLabel = "提醒",
        )
    }
}

@Composable
private fun EvidenceContent(event: RiskEvent) {
    val tokens = SilverShieldTokens.colors
    val available = event.availableModalities
    val missing = event.missingModalities

    Text(
        text = "已获取的证据",
        style = MaterialTheme.typography.labelLarge,
        color = tokens.textPrimary,
    )
    if (available.isEmpty()) {
        Text(
            text = "尚未获取任何证据模态。",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textSecondary,
        )
    } else {
        available.forEach { modality ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                    modifier = Modifier.width(Spacing.md),
                )
                Text(
                    text = modality.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textPrimary,
                )
            }
        }
    }

    event.evidenceSummary?.let { summary ->
        Spacer(Modifier.heightIn(min = Spacing.xs))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textPrimary,
        )
    }

    if (missing.isNotEmpty()) {
        Spacer(Modifier.heightIn(min = Spacing.sm))
        Text(
            text = "尚未获取的证据",
            style = MaterialTheme.typography.labelLarge,
            color = tokens.textPrimary,
        )
        missing.forEach { modality ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textTertiary,
                    modifier = Modifier.width(Spacing.md),
                )
                Text(
                    text = "${modality.label}：未获取",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                )
            }
        }
    } else if (available.isEmpty()) {
        Spacer(Modifier.heightIn(min = Spacing.sm))
        Text(
            text = "未列出的证据均为未获取，请勿据此认为内容安全。",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textTertiary,
        )
    }

    Spacer(Modifier.heightIn(min = Spacing.sm))
    Text(
        text = "本页只展示线索摘要，不保存截图，也不上传原始画面。",
        style = MaterialTheme.typography.bodySmall,
        color = tokens.textTertiary,
    )
}

@Composable
private fun HandleAction(event: RiskEvent, onMarkHandled: () -> Unit) {
    val tokens = SilverShieldTokens.colors
    if (event.handleStatus == RiskHandleStatus.Handled) {
        Text(
            text = "这条记录已标记为处理完成。",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textSecondary,
        )
    } else {
        Button(
            onClick = onMarkHandled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.primaryButtonHeight),
            shape = RoundedCornerShape(Radius.md),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(text = "标记为已处理", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ShareSection(
    uiState: RiskDetailUiState,
    onOpenDialog: () -> Unit,
    onRevokeShare: () -> Unit,
) {
    val tokens = SilverShieldTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        when {
            uiState.shareStatus == ConsentStatus.ACTIVE -> {
                StatusCard(
                    title = "已分享给家人（仅摘要）",
                    description = "只分享标题、风险等级与处理建议，不含原始屏幕录像。授权 7 天后到期，可随时撤销。",
                    tone = StatusTone.Safe,
                    statusLabel = "已分享",
                )
                OutlinedButton(
                    onClick = onRevokeShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.primaryButtonHeight),
                ) {
                    Text(text = "撤销本次分享", style = MaterialTheme.typography.labelLarge)
                }
            }

            !uiState.canShare -> StatusCard(
                title = "还没有绑定家人",
                description = "绑定家人后才能分享这条事件。绑定之后，也需要你在这里主动确认，不会自动共享。",
                tone = StatusTone.Neutral,
                statusLabel = "未绑定",
            )

            else -> {
                if (uiState.shareStatus == ConsentStatus.REVOKED) {
                    StatusCard(
                        title = "这次分享已撤销",
                        description = "家属不再看到这条事件的共享信息。如需重新分享，请再次确认。",
                        tone = StatusTone.Danger,
                        statusLabel = "已撤销",
                    )
                } else if (uiState.shareStatus == ConsentStatus.EXPIRED) {
                    StatusCard(
                        title = "这次分享已过期",
                        description = "授权到期后家属不再看到这条事件。可以重新分享。",
                        tone = StatusTone.Neutral,
                        statusLabel = "已过期",
                    )
                }
                OutlinedButton(
                    onClick = onOpenDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.primaryButtonHeight),
                ) {
                    Text(text = "分享给家人", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    text = "分享前需要你确认，默认不分享，也不会发送屏幕录像。",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun ShareConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = SilverShieldTokens.colors.neutral,
            )
        },
        title = { Text(text = "确认分享这条风险事件？", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = "本次只会分享这条风险事件的文字摘要（标题、等级、处理建议），" +
                    "不会上传屏幕录像或原始画面。授权 7 天后自动失效，也可以随时撤销。" +
                    "当前为本地模拟记录，尚未真正跨设备发送。",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "确认分享", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.borderWidth, tokens.border),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
            )
            content()
        }
    }
}

@Composable
private fun BulletLine(index: Int, text: String) {
    val tokens = SilverShieldTokens.colors
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textTertiary,
            modifier = Modifier.width(Spacing.lg),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(name = "E07 风险详情 · 高风险", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 1200)
@Composable
private fun ElderRiskDetailHighPreview() {
    val event = MockRiskEvents.initial(nowMillis = 1_700_000_000_000L)[0]
    SilverShieldTheme {
        ElderRiskDetailScreen(
            uiState = RiskDetailUiState(event = event, loading = false),
            onMarkHandled = {},
            onShareEvent = {},
            onRevokeShare = {},
            onConsumeMessage = {},
        )
    }
}

@Preview(name = "E07 风险详情 · 信息不足", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 1000)
@Composable
private fun ElderRiskDetailInsufficientPreview() {
    val event = MockRiskEvents.initial(nowMillis = 1_700_000_000_000L)[1]
    SilverShieldTheme {
        ElderRiskDetailScreen(
            uiState = RiskDetailUiState(event = event, loading = false),
            onMarkHandled = {},
            onShareEvent = {},
            onRevokeShare = {},
            onConsumeMessage = {},
        )
    }
}
