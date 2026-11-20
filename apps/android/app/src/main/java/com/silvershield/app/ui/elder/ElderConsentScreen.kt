package com.silvershield.app.ui.elder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.family.ConsentStatus
import com.silvershield.app.family.ConsentUiState
import com.silvershield.app.family.ConsentValidity
import com.silvershield.app.guard.risk.RiskType
import com.silvershield.app.ui.components.ConsentCard
import com.silvershield.app.ui.components.ErrorState
import com.silvershield.app.ui.components.FamilySection
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.components.PrivacyNote
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * 老人端 · 风险共享授权管理（P07）。
 *
 * 老人独占授权与撤销：
 * - 家庭绑定后默认全部关闭；
 * - 事先授权必须手动勾选风险类型；
 * - 授权可设置 7 天 / 30 天 / 长期，长期仍可随时撤销；
 * - 撤销立即生效，重启后不会恢复。
 */
@Composable
fun ElderConsentScreen(
    uiState: ConsentUiState,
    onGrantScope: (Set<RiskType>, ConsentValidity) -> Unit,
    onRevoke: (String) -> Unit,
    onRefresh: () -> Unit,
    onConsumeMessage: () -> Unit,
    onOpenRelation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTypes by rememberSaveable(stateSaver = riskTypeSetSaver) {
        mutableStateOf(emptySet<RiskType>())
    }
    var validity by rememberSaveable { mutableStateOf(ConsentValidity.SevenDays) }

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
                Column {
                    Text(
                        text = "风险共享授权",
                        style = MaterialTheme.typography.headlineSmall,
                        color = tokens.textPrimary,
                    )
                    Text(
                        text = "只有你本人可以决定共享什么。家属只能查看，不能替你开启。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.textSecondary,
                    )
                }
            }

            if (!uiState.isBound) {
                item(key = "unbound") {
                    StatusCard(
                        title = "请先绑定家人",
                        description = "没有家庭关系时不能共享风险事件。请先完成家庭绑定。",
                        tone = StatusTone.Neutral,
                        statusLabel = "未绑定",
                    )
                }
                item(key = "to-relation") {
                    PrimaryButton(
                        text = "去管理家庭关系",
                        onClick = onOpenRelation,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                item(key = "default-off") {
                    StatusCard(
                        title = "共享默认关闭",
                        description = "绑定成功后不会自动共享历史记录，也不会自动共享将来全部事件。",
                        tone = StatusTone.Info,
                        statusLabel = "默认不共享",
                    )
                }

                item(key = "grant") {
                    FamilySection(
                        title = "事先授权指定范围",
                        subtitle = "勾选你同意共享的风险类型。不勾选就不会共享。",
                    ) {
                        RiskType.entries
                            .filter { it != RiskType.Unknown }
                            .forEach { type ->
                                val checked = type in selectedTypes
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = Sizes.touchTargetMin)
                                        .clickable {
                                            selectedTypes = if (checked) {
                                                selectedTypes - type
                                            } else {
                                                selectedTypes + type
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = null)
                                    Text(
                                        text = type.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = tokens.textPrimary,
                                        modifier = Modifier.padding(start = Spacing.xs),
                                    )
                                }
                            }

                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "授权有效期",
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.textPrimary,
                        )
                        ConsentValidity.entries.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Sizes.touchTargetMin)
                                    .clickable { validity = option },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = validity == option,
                                    onClick = { validity = option },
                                )
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = tokens.textPrimary,
                                )
                            }
                        }

                        PrimaryButton(
                            text = "保存事先授权",
                            onClick = { onGrantScope(selectedTypes, validity) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedTypes.isNotEmpty(),
                        )
                        if (selectedTypes.isEmpty()) {
                            Text(
                                text = "请至少选择一种风险类型。",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textTertiary,
                            )
                        }
                    }
                }

                item(key = "active-title") {
                    Text(
                        text = "当前有效授权（${uiState.activeConsents.size} 项）",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary,
                    )
                }
                if (uiState.activeConsents.isEmpty()) {
                    item(key = "active-empty") {
                        StatusCard(
                            title = "目前没有共享授权",
                            description = "家属现在看不到任何风险信息。",
                            tone = StatusTone.Neutral,
                            statusLabel = "未授权",
                        )
                    }
                } else {
                    items(uiState.activeConsents, key = { it.consentId }) { record ->
                        ConsentCard(
                            record = record,
                            status = ConsentStatus.ACTIVE,
                            actionLabel = "撤销这条授权",
                            onAction = { onRevoke(record.consentId) },
                        )
                    }
                }

                if (uiState.historyConsents.isNotEmpty()) {
                    item(key = "history-title") {
                        Text(
                            text = "授权历史",
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
                    PrivacyNote("共享内容只有风险事件摘要，不包含原始屏幕录像、实时屏幕或完整浏览历史。")
                    PrivacyNote("你可以随时撤销授权；撤销后家属端立即显示为已撤销。")
                    PrivacyNote("本轮撤销为本机模拟，尚未接入服务端强制校验；后续真实业务必须由服务端执行对象级访问控制。")
                    Spacer(Modifier.height(Spacing.xxs))
                    SampleTag()
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

/** 用名称集合保存选中风险类型，避免 Saver 依赖枚举序号。 */
private val riskTypeSetSaver = androidx.compose.runtime.saveable.Saver<Set<RiskType>, List<String>>(
    save = { set -> ArrayList(set.map { it.name }) },
    restore = { names -> names.mapNotNull { name -> RiskType.entries.firstOrNull { it.name == name } }.toSet() },
)

@Preview(name = "老人端 · 风险共享授权 · 未绑定", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 900)
@Composable
private fun ElderConsentScreenPreview() {
    SilverShieldTheme {
        ElderConsentScreen(
            uiState = ConsentUiState(loading = false),
            onGrantScope = { _, _ -> },
            onRevoke = {},
            onRefresh = {},
            onConsumeMessage = {},
            onOpenRelation = {},
        )
    }
}
