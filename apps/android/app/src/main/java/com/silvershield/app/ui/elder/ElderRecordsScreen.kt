package com.silvershield.app.ui.elder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.guard.risk.MockRiskEvents
import com.silvershield.app.guard.risk.RiskEvent
import com.silvershield.app.guard.risk.RiskLevelFilter
import com.silvershield.app.guard.risk.RiskType
import com.silvershield.app.ui.components.EmptyState
import com.silvershield.app.ui.components.ErrorState
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.RiskAlertCard
import com.silvershield.app.ui.components.RiskBadge
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.components.displayLabel
import com.silvershield.app.ui.components.formatRiskTime
import com.silvershield.app.ui.components.toRiskLevel
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * E08 风险记录（P06）。
 *
 * - 列表来自本地 Room，按发生时间倒序；
 * - 顶部展示 E06 最新提醒（受 E13「风险提醒」开关控制，不受筛选影响）；
 * - 支持按风险等级、风险类型双筛选；
 * - 删除单条与清空全部均真实作用于数据库，操作前二次确认；
 * - 读取失败展示 ErrorState，无数据展示 EmptyState；
 * - 模拟记录显著标记「模拟数据」。
 *
 * @param reminderEnabled E13「风险提醒」开关；关闭时不展示任何提醒卡
 * @param voiceEnabled E13「语音提醒」开关；关闭时不显示语音入口
 */
@Composable
fun ElderRecordsScreen(
    uiState: RiskRecordsUiState,
    reminderEnabled: Boolean,
    voiceEnabled: Boolean,
    onLevelFilterChange: (RiskLevelFilter) -> Unit,
    onTypeFilterChange: (RiskType?) -> Unit,
    onOpenDetail: (String) -> Unit,
    onAddMockEvent: () -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onSpeak: () -> Unit,
    onConsumeMessage: () -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<RiskEvent?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeMessage()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.loading -> LoadingState(
                modifier = Modifier.fillMaxSize(),
                message = "正在读取本地风险记录…",
            )

            uiState.loadFailed -> ErrorState(
                modifier = Modifier.fillMaxSize(),
                title = "风险记录读取失败",
                description = "本机记录暂时无法读取，已有数据不会丢失，请重试。",
                onRetry = onRetryLoad,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = Spacing.pageHorizontal,
                    vertical = Spacing.pageVertical,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) {
                item(key = "privacy-note") {
                    PrivacyNote()
                }

                if (reminderEnabled) {
                    uiState.latestAlertEvent?.let { latest ->
                        item(key = "latest-alert") {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text(
                                    text = "最新提醒",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tokens.textPrimary,
                                )
                                RiskAlertCard(
                                    event = latest,
                                    onOpenDetail = { onOpenDetail(latest.eventId) },
                                    onSpeak = if (voiceEnabled) onSpeak else null,
                                )
                            }
                        }
                    }
                }

                item(key = "level-filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = "按风险等级筛选",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.textSecondary,
                        )
                        FilterRow {
                            RiskLevelFilter.entries.forEach { filter ->
                                FilterChip(
                                    label = filter.label,
                                    selected = filter == uiState.levelFilter,
                                    onClick = { onLevelFilterChange(filter) },
                                )
                            }
                        }
                    }
                }

                item(key = "type-filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = "按风险类型筛选",
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.textSecondary,
                        )
                        FilterRow {
                            FilterChip(
                                label = "全部类型",
                                selected = uiState.typeFilter == null,
                                onClick = { onTypeFilterChange(null) },
                            )
                            RiskType.entries.forEach { type ->
                                FilterChip(
                                    label = type.label,
                                    selected = uiState.typeFilter == type,
                                    onClick = { onTypeFilterChange(type) },
                                )
                            }
                        }
                    }
                }

                if (uiState.events.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = if (uiState.totalCount == 0) "暂无风险记录" else "当前筛选下暂无记录",
                            description = if (uiState.totalCount == 0) {
                                "识别到的风险事件会保存在本机并显示在这里。"
                            } else {
                                "可以切换筛选条件查看其他记录。"
                            },
                        )
                    }
                } else {
                    items(items = uiState.events, key = { it.eventId }) { event ->
                        RiskRecordRow(
                            event = event,
                            onOpen = { onOpenDetail(event.eventId) },
                            onDelete = { pendingDelete = event },
                        )
                    }
                }

                item(key = "actions") {
                    RecordActions(
                        onAddMockEvent = onAddMockEvent,
                        onClearAll = { showClearConfirm = true },
                        clearEnabled = uiState.totalCount > 0,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    pendingDelete?.let { event ->
        ConfirmDialog(
            title = "删除这条记录？",
            description = "「${event.title}」删除后无法恢复，本机不再保留。",
            confirmLabel = "删除",
            onConfirm = {
                pendingDelete = null
                onDelete(event.eventId)
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "清空本地记录？",
            description = "将删除本机保存的全部风险记录，删除后无法恢复。",
            confirmLabel = "清空",
            onConfirm = {
                showClearConfirm = false
                onClearAll()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun PrivacyNote() {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = tokens.surfaceSubtle,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = tokens.neutral,
                modifier = Modifier.size(Sizes.iconSm),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = "风险记录只保存在这台手机上，不保存截图、不上传。标记为「模拟数据」的记录仅用于功能验证。",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
            )
        }
    }
}

@Composable
private fun FilterRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        content = content,
    )
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = modifier
            .heightIn(min = Sizes.touchTargetMin)
            .semantics { contentDescription = "$label 筛选" }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.pill),
        color = if (selected) tokens.accentLight else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            Sizes.borderWidth,
            if (selected) MaterialTheme.colorScheme.primary else tokens.border,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else tokens.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun RiskRecordRow(
    event: RiskEvent,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.borderWidth, tokens.border),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.isMock) {
                        SampleTag()
                        Spacer(Modifier.width(Spacing.xs))
                    }
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary,
                    )
                }
                RiskBadge(
                    category = event.riskType.label,
                    level = event.riskLevel.toRiskLevel(),
                )
                Text(
                    text = "${formatRiskTime(event.occurredAt)} · ${event.handleStatus.displayLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(Sizes.touchTargetMin),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除「${event.title}」",
                    tint = tokens.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun RecordActions(
    onAddMockEvent: () -> Unit,
    onClearAll: () -> Unit,
    clearEnabled: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedButton(
            onClick = onAddMockEvent,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.primaryButtonHeight),
        ) {
            Text(text = "模拟识别到一条风险（测试）", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = onClearAll,
            enabled = clearEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.touchTargetMin),
        ) {
            Text(text = "清空本地记录", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    description: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text = description, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Preview(name = "E08 风险记录 · 列表", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 1200)
@Composable
private fun ElderRecordsPreview() {
    val events = MockRiskEvents.initial(nowMillis = 1_700_000_000_000L)
    SilverShieldTheme {
        ElderRecordsScreen(
            uiState = RiskRecordsUiState(
                events = events,
                totalCount = events.size,
                latestAlertEvent = events.first(),
                loading = false,
            ),
            reminderEnabled = true,
            voiceEnabled = true,
            onLevelFilterChange = {},
            onTypeFilterChange = {},
            onOpenDetail = {},
            onAddMockEvent = {},
            onDelete = {},
            onClearAll = {},
            onSpeak = {},
            onConsumeMessage = {},
            onRetryLoad = {},
        )
    }
}

@Preview(name = "E08 风险记录 · 空态", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 800)
@Composable
private fun ElderRecordsEmptyPreview() {
    SilverShieldTheme {
        ElderRecordsScreen(
            uiState = RiskRecordsUiState(loading = false),
            reminderEnabled = true,
            voiceEnabled = true,
            onLevelFilterChange = {},
            onTypeFilterChange = {},
            onOpenDetail = {},
            onAddMockEvent = {},
            onDelete = {},
            onClearAll = {},
            onSpeak = {},
            onConsumeMessage = {},
            onRetryLoad = {},
        )
    }
}
