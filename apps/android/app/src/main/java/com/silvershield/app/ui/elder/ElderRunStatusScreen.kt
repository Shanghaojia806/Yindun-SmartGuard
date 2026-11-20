package com.silvershield.app.ui.elder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.guard.GuardScenario
import com.silvershield.app.guard.GuardState
import com.silvershield.app.guard.GuardStatus
import com.silvershield.app.guard.GuardUiState
import com.silvershield.app.guard.ScreenAuthorizationState
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * E05 运行状态。
 *
 * 与 E03 共用同一份 [GuardUiState]，因此状态、音频能力与异常原因始终一致。
 * 页面底部的开发测试面板只在存在模拟场景控制器时展示。
 */
@Composable
fun ElderRunStatusScreen(
    uiState: GuardUiState,
    onStartGuard: () -> Unit,
    onStopGuard: () -> Unit,
    onSelectScenario: (GuardScenario) -> Unit,
    onOpenCapturePoc: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val status = uiState.status
    val presentation = status.toPresentation()
    val authorized = uiState.preferences.screenAuthorization == ScreenAuthorizationState.GRANTED
    // 尚未开始守护时不突出任何运行场景，避免与"未开启"状态冲突。
    val effectiveScenario = if (status.state == GuardState.OFF) null else uiState.scenario

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.pageHorizontal,
            end = Spacing.pageHorizontal,
            top = Spacing.pageVertical,
            bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            Column {
                Text(
                    text = "页面 E05",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = "运行状态",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "这里显示模拟守护的当前状态，不含真实采集；真实屏幕采集请进入下方 P05 验证页。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.textSecondary,
                )
            }
        }

        item {
            GuardStatusCard(status = status)
        }

        item {
            GuardSection(title = "运行详情") {
                GuardInfoRow(
                    label = "当前守护状态",
                    value = presentation.stateLabel,
                )
                GuardInfoRow(
                    label = "当前运行模式",
                    value = "模拟守护（FakeGuardService）",
                )
                GuardInfoRow(
                    label = "屏幕采集状态",
                    value = "模拟守护不采集屏幕；真实采集见 P05 验证页",
                )
                GuardInfoRow(
                    label = "音频状态",
                    value = status.audioState.displayLabel(),
                )
                GuardInfoRow(
                    label = "最近一次状态变化时间",
                    value = formatGuardTimestamp(status.lastChangedAt),
                )
                GuardInfoRow(
                    label = "异常或降级原因",
                    value = status.reason ?: "无",
                    showDivider = false,
                )
            }
        }

        item {
            if (status.state.isActive) {
                GuardStopButton(onClick = onStopGuard)
            } else {
                PrimaryButton(
                    text = if (status.state == GuardState.OFF) "开启守护" else "重新开启守护",
                    onClick = onStartGuard,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            GuardEntryCard(
                title = "真实屏幕采集验证（P05 PoC）",
                description = "独立调用 Android MediaProjection，仅在您主动授权后于本地验证采集帧；与上面的模拟守护互不冒充。",
                icon = Icons.Filled.PlayArrow,
                onClick = onOpenCapturePoc,
                pageCode = "P05",
            )
        }

        if (uiState.scenario != null) {
            item {
                GuardSection(title = "开发测试 · 模拟场景") {
                    Text(
                        text = "以下按钮仅切换模拟守护的界面状态，不会读取屏幕、录音或调用系统授权。",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                    )
                    Spacer(Modifier.height(Spacing.sm))

                    GuardScenario.entries.forEach { scenario ->
                        GuardScenarioChip(
                            label = scenario.label,
                            selected = effectiveScenario == scenario,
                            enabled = authorized || scenario == GuardScenario.USER_STOPPED,
                            onClick = { onSelectScenario(scenario) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xxs),
                        )
                        Spacer(Modifier.height(Spacing.xs))
                    }

                    if (!authorized) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "尚未完成模拟授权：只能演示「用户停止」。请先在授权说明页完成模拟授权。",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textTertiary,
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md),
                    color = tokens.warningContainer,
                    border = BorderStroke(Sizes.borderWidth, tokens.warning.copy(alpha = 0.4f)),
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = "模拟场景说明",
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.onWarningContainer,
                        )
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(
                            text = "这些状态由开发测试按钮触发，不代表真实设备检测结果。" +
                                "真实屏幕采集与音频能力验证将在 P05 进行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.onWarningContainer,
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "E05 运行状态 · 运行中", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 1100)
@Composable
private fun ElderRunStatusScreenPreview() {
    SilverShieldTheme {
        ElderRunStatusScreen(
            uiState = GuardUiState(
                loading = false,
                status = GuardStatus(
                    state = GuardState.RUNNING,
                    lastChangedAt = System.currentTimeMillis(),
                ),
                scenario = GuardScenario.NORMAL,
            ),
            onStartGuard = {},
            onStopGuard = {},
            onSelectScenario = {},
            onOpenCapturePoc = {},
        )
    }
}
