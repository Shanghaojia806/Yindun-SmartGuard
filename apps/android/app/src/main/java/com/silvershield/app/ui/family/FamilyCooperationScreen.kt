package com.silvershield.app.ui.family

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.silvershield.app.family.FamilyRiskUiState
import com.silvershield.app.ui.components.EmptyState
import com.silvershield.app.ui.components.ErrorState
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.PrivacyNote
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Spacing

/**
 * F07 协同处理（待协同清单）。
 *
 * 具体的联系老人、更新状态与备注操作在 [FamilyCooperateScreen] 中完成。
 * 家属只能更新自己参与的协同处理记录，不能修改 AI 原始判断或删除老人事件。
 */
@Composable
fun FamilyCooperationScreen(
    uiState: FamilyRiskUiState,
    onOpenCooperate: (String) -> Unit,
    onOpenBind: () -> Unit,
    onOpenRisks: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors

    when {
        uiState.loadFailed -> {
            ErrorState(
                kind = com.silvershield.app.ui.components.ErrorKind.Load,
                title = "协同事项加载失败",
                description = "暂时无法读取本机处理记录，请重试。",
                onRetry = onRetry,
                modifier = modifier.fillMaxSize(),
            )
            return
        }

        uiState.loading && uiState.relation == null -> {
            LoadingState(message = "正在加载待协同事项…", modifier = modifier.fillMaxSize())
            return
        }
    }

    if (!uiState.isBound) {
        EmptyState(
            modifier = modifier.fillMaxSize(),
            title = "尚未绑定老人",
            description = "完成绑定并取得共享授权后，才能参与风险事件的协同处理。",
            actionLabel = "去绑定老人",
            onAction = onOpenBind,
        )
        return
    }

    if (!uiState.hasConsent) {
        EmptyState(
            modifier = modifier.fillMaxSize(),
            title = "尚未获得共享授权",
            description = "目前没有获得可查看的风险事件共享授权，无法参与协同处理。",
            actionLabel = "查看风险列表",
            onAction = onOpenRisks,
        )
        return
    }

    val openItems = uiState.items.filter { it.handlingStatus.isOpen }

    if (uiState.items.isEmpty()) {
        EmptyState(
            modifier = modifier.fillMaxSize(),
            title = "老人还没有分享风险事件",
            description = "没有可协同处理的事件。新的分享到达后会出现在这里。",
            actionLabel = "刷新",
            onAction = onRetry,
        )
        return
    }

    if (openItems.isEmpty()) {
        EmptyState(
            modifier = modifier.fillMaxSize(),
            title = "所有共享事件都已协助处理",
            description = "当前没有待协同的事件，可以在风险列表中回看历史处理记录。",
            actionLabel = "查看风险列表",
            onAction = onOpenRisks,
        )
        return
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
                        text = "待协同事件",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = tokens.textPrimary,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = "共 ${openItems.size} 条需要继续跟进",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                    )
                }
                SampleTag()
            }
        }

        items(openItems, key = { it.share.shareId }) { item ->
            FamilyRiskEventCard(
                item = item,
                onClick = { onOpenCooperate(item.share.shareId) },
                actionLabel = "去协同处理",
                onAction = { onOpenCooperate(item.share.shareId) },
            )
        }

        item {
            PrivacyNote(
                "家属只能更新自己参与的协同处理状态与备注，不能把事件改为「AI 已确认安全」，也不能删除老人的原始风险事件。",
            )
        }
    }
}
