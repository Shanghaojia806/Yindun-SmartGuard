package com.silvershield.app.ui.family

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.silvershield.app.family.FamilyRelationStatus
import com.silvershield.app.family.FamilyRiskUiState
import com.silvershield.app.family.HandlingStatus
import com.silvershield.app.guard.risk.RiskLevel
import com.silvershield.app.guard.risk.RiskType
import com.silvershield.app.ui.components.EmptyState
import com.silvershield.app.ui.components.ErrorState
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Spacing

/**
 * F05 家属风险列表。
 *
 * 数据范围完全由 [FamilyRiskUiState.items] 决定，而 items 已在 Repository 层通过
 * 「关系 + 授权 + 分享记录」三层校验，UI 过滤只影响展示，不会扩大数据范围。
 */
@Composable
fun FamilyRisksScreen(
    uiState: FamilyRiskUiState,
    onOpenEvent: (String) -> Unit,
    onOpenBind: () -> Unit,
    onOpenRelation: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    var levelFilter by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf("") }
    var handlingFilter by rememberSaveable { mutableStateOf("") }
    var readFilter by rememberSaveable { mutableStateOf("") }

    when {
        uiState.loadFailed -> {
            ErrorState(
                kind = com.silvershield.app.ui.components.ErrorKind.Load,
                title = "风险事件加载失败",
                description = "暂时无法读取共享记录，请重试。",
                onRetry = onRetry,
                modifier = modifier.fillMaxSize(),
            )
            return
        }

        uiState.loading && uiState.relation == null -> {
            LoadingState(message = "正在加载授权共享事件…", modifier = modifier.fillMaxSize())
            return
        }
    }

    val relation = uiState.relation
    val relationshipEnded = relation != null &&
        relation.relationStatus != FamilyRelationStatus.ACTIVE &&
        relation.relationStatus != FamilyRelationStatus.PENDING

    if (!uiState.isBound) {
        EmptyState(
            modifier = modifier.fillMaxSize(),
            title = if (relationshipEnded) "家庭关系已解除" else "尚未绑定老人",
            description = if (relationshipEnded) {
                "家庭关系失效后，共享访问已经停止。可以重新发起绑定。"
            } else {
                "完成双方确认的绑定后，老人才可以单独授权分享风险事件。"
            },
            actionLabel = if (relationshipEnded) "重新绑定" else "去绑定老人",
            onAction = onOpenBind,
        )
        return
    }

    if (!uiState.hasConsent) {
        EmptyState(
            modifier = modifier.fillMaxSize(),
            title = if (uiState.revokedShareCount > 0) "共享授权已撤销" else "尚未获得共享授权",
            description = if (uiState.revokedShareCount > 0) {
                "老人已经撤销了风险事件共享，已撤销的事件不会继续显示。"
            } else {
                "目前没有获得可查看的风险事件共享授权。家庭绑定不代表自动授权。"
            },
            actionLabel = "查看关联关系",
            onAction = onOpenRelation,
        )
        return
    }

    if (uiState.items.isEmpty()) {
        EmptyState(
            modifier = modifier.fillMaxSize(),
            title = "老人还没有分享风险事件",
            description = "共享授权已经生效，但还没有事件被主动分享。这里不会使用虚构数据。",
            actionLabel = "刷新",
            onAction = onRetry,
        )
        return
    }

    val filtered = uiState.items.filter { item ->
        (levelFilter.isEmpty() || item.event.riskLevel.name == levelFilter) &&
            (typeFilter.isEmpty() || item.event.riskType.name == typeFilter) &&
            (handlingFilter.isEmpty() || item.handlingStatus.name == handlingFilter) &&
            when (readFilter) {
                "read" -> item.share.isRead
                "unread" -> !item.share.isRead
                else -> true
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
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "风险事件",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = "仅显示老人主动分享、授权仍然有效的事件",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                    )
                }
                SampleTag()
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                FilterRow(
                    label = "风险等级",
                    options = listOf("" to "全部") + RiskLevel.entries.map { it.name to it.label },
                    selected = levelFilter,
                    onSelect = { levelFilter = it },
                )
                FilterRow(
                    label = "风险类型",
                    options = listOf("" to "全部") + RiskType.entries.map { it.name to it.label },
                    selected = typeFilter,
                    onSelect = { typeFilter = it },
                )
                FilterRow(
                    label = "处理状态",
                    options = listOf("" to "全部") + HandlingStatus.entries.map { it.name to it.label },
                    selected = handlingFilter,
                    onSelect = { handlingFilter = it },
                )
                FilterRow(
                    label = "查看状态",
                    options = listOf("" to "全部", "unread" to "未查看", "read" to "已查看"),
                    selected = readFilter,
                    onSelect = { readFilter = it },
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = if (uiState.allHandled) {
                        "所有共享事件都已协助处理"
                    } else {
                        "当前筛选条件下没有事件"
                    },
                    description = if (uiState.allHandled) {
                        "没有需要继续跟进的事件，可以调整筛选回看处理历史。"
                    } else {
                        "请调整风险等级、类型、处理状态或查看状态。"
                    },
                )
            }
        }

        items(filtered, key = { it.share.shareId }) { item ->
            FamilyRiskEventCard(
                item = item,
                onClick = { onOpenEvent(item.share.shareId) },
                actionLabel = "查看详情",
                onAction = { onOpenEvent(item.share.shareId) },
            )
        }

        item {
            Text(
                text = "AI 风险等级、家属查看状态、协同处理状态分别保存，互不覆盖。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textTertiary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val tokens = SilverShieldTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textSecondary,
            modifier = Modifier.padding(end = Spacing.xxs),
        )
        options.forEach { (key, text) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(text = text, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}
