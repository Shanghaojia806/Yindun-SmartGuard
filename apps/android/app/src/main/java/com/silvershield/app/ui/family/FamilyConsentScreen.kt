package com.silvershield.app.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.family.ConsentStatus
import com.silvershield.app.family.ConsentUiState
import com.silvershield.app.ui.components.ConsentCard
import com.silvershield.app.ui.components.ErrorState
import com.silvershield.app.ui.components.FamilySection
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.PrivacyNote
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * F09 共享授权（P07，家属端只读）。
 *
 * 家属只能查看老人授予的有效授权；页面不提供任何开启/修改授权入口。
 */
@Composable
fun FamilyConsentScreen(
    uiState: ConsentUiState,
    onRefresh: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeMessage()
    }

    when {
        uiState.loading -> LoadingState(modifier = modifier.fillMaxSize(), message = "正在读取共享授权…")

        uiState.loadFailed -> ErrorState(
            modifier = modifier.fillMaxSize(),
            title = "共享授权读取失败",
            description = "本地数据暂时无法读取，请重试。",
            onRetry = onRefresh,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = Spacing.pageHorizontal,
                vertical = Spacing.pageVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            item(key = "header") {
                androidx.compose.foundation.layout.Column {
                    Text(
                        text = "页面 F09",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "共享授权",
                        style = MaterialTheme.typography.headlineSmall,
                        color = tokens.textPrimary,
                    )
                    Text(
                        text = "这里只读展示老人主动授予你的内容。家属不能自行开启或修改授权。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.textSecondary,
                    )
                }
            }

            if (!uiState.isBound) {
                item(key = "unbound") {
                    StatusCard(
                        title = "尚未绑定家人",
                        description = "绑定关系建立后，也需要老人单独授权，家属才能看到风险信息。",
                        tone = StatusTone.Neutral,
                        statusLabel = "未绑定",
                    )
                }
            } else {
                item(key = "relation") {
                    StatusCard(
                        title = "当前共享状态：以老人授权为准",
                        description = "家庭绑定不代表自动授权。下面仅显示仍有效的授权范围。",
                        tone = StatusTone.Info,
                        statusLabel = "只读",
                    )
                }

                if (uiState.activeConsents.isEmpty()) {
                    item(key = "empty-consent") {
                        StatusCard(
                            title = "目前没有共享授权",
                            description = "老人还没有授权任何风险事件。你可以提醒老人，但不能代替老人开启。",
                            tone = StatusTone.Neutral,
                            statusLabel = "未授权",
                        )
                    }
                } else {
                    item(key = "active-title") {
                        Text(
                            text = "有效的共享授权（${uiState.activeConsents.size} 项）",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary,
                        )
                    }
                    items(uiState.activeConsents, key = { it.consentId }) { record ->
                        ConsentCard(record = record, status = ConsentStatus.ACTIVE)
                    }
                }

                if (uiState.historyConsents.isNotEmpty()) {
                    item(key = "history-title") {
                        Text(
                            text = "已失效的授权",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary,
                        )
                    }
                    items(uiState.historyConsents, key = { it.consentId }) { record ->
                        ConsentCard(record = record, status = record.consentStatus)
                    }
                }
            }

            item(key = "notes") {
                FamilySection(title = "隐私说明") {
                    PrivacyNote("家属只能查看有效授权范围内的风险事件摘要，不能查看原始屏幕录像或实时屏幕。")
                    PrivacyNote("老人撤销授权或解除绑定后，这里会立即显示为已撤销，不能继续查看。")
                    PrivacyNote("本轮授权与撤销均为本机模拟，尚未接入服务端强制校验。")
                    androidx.compose.foundation.layout.Row {
                        SampleTag()
                    }
                }
            }

            item(key = "refresh") {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.primaryButtonHeight),
                ) {
                    Text(text = "刷新授权状态", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    SnackbarHost(hostState = snackbarHostState)
}

@Preview(name = "F09 共享授权 · 未绑定", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 820)
@Composable
private fun FamilyConsentScreenPreview() {
    SilverShieldTheme {
        FamilyConsentScreen(
            uiState = ConsentUiState(loading = false),
            onRefresh = {},
            onConsumeMessage = {},
        )
    }
}
