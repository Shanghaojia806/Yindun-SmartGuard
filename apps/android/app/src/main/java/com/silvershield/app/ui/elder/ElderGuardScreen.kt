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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.guard.GuardState
import com.silvershield.app.guard.GuardStatus
import com.silvershield.app.guard.GuardUiState
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * E03 守护首页。
 *
 * 页面只读取 [GuardUiState] 并发出用户意图，不执行任何守护业务逻辑。
 * 核心动作只有一个：开启守护或停止守护；其余功能作为二级入口。
 */
@Composable
fun ElderGuardScreen(
    uiState: GuardUiState,
    onStartGuard: () -> Unit,
    onStopGuard: () -> Unit,
    onOpenAuthorization: () -> Unit,
    onOpenRunStatus: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val state = uiState.status.state

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
                    text = "页面 E03",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = "守护首页",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "守护只在本机运行。您随时可以查看状态或停止守护。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.textSecondary,
                )
            }
        }

        item {
            GuardStatusCard(status = uiState.status)
        }

        item {
            if (state.isActive) {
                GuardStopButton(
                    onClick = onStopGuard,
                    text = if (state == GuardState.REQUESTING) "取消守护" else "停止守护",
                )
            } else {
                PrimaryButton(
                    text = if (state == GuardState.OFF) "开启守护" else "重新开启守护",
                    onClick = onStartGuard,
                    leadingIcon = Icons.Filled.Lock,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Text(
                text = "更多守护功能",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
            )
        }

        item {
            GuardEntryCard(
                title = "授权说明",
                description = "了解屏幕采集范围与本机处理方式",
                icon = Icons.Filled.Lock,
                pageCode = "E04",
                onClick = onOpenAuthorization,
            )
        }

        item {
            GuardEntryCard(
                title = "运行状态",
                description = "查看当前状态、音频能力与异常原因",
                icon = Icons.Filled.Info,
                pageCode = "E05",
                onClick = onOpenRunStatus,
            )
        }

        item {
            GuardEntryCard(
                title = "守护设置",
                description = "调整风险提醒、语音提醒与提醒偏好",
                icon = Icons.Filled.Settings,
                pageCode = "E13",
                onClick = onOpenSettings,
            )
        }

        item {
            GuardEntryCard(
                title = "隐私与授权",
                description = "管理屏幕授权、家庭共享与本地数据",
                icon = Icons.Filled.Lock,
                pageCode = "E14",
                onClick = onOpenPrivacy,
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                color = tokens.surfaceSubtle,
                border = BorderStroke(Sizes.borderWidth, tokens.border),
            ) {
                Text(
                    text = "当前为 P04 模拟守护：不会进行真实屏幕采集、录音或 AI 识别。" +
                        "真实采集能力验证将在 P05 独立进行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textTertiary,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
    }
}

@Preview(name = "E03 守护首页 · 未开启", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 900)
@Composable
private fun ElderGuardScreenOffPreview() {
    SilverShieldTheme {
        ElderGuardScreen(
            uiState = GuardUiState(loading = false),
            onStartGuard = {},
            onStopGuard = {},
            onOpenAuthorization = {},
            onOpenRunStatus = {},
            onOpenSettings = {},
            onOpenPrivacy = {},
        )
    }
}

@Preview(name = "E03 守护首页 · 运行中", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 900)
@Composable
private fun ElderGuardScreenRunningPreview() {
    SilverShieldTheme {
        ElderGuardScreen(
            uiState = GuardUiState(
                loading = false,
                status = GuardStatus(
                    state = GuardState.RUNNING,
                    lastChangedAt = System.currentTimeMillis(),
                ),
            ),
            onStartGuard = {},
            onStopGuard = {},
            onOpenAuthorization = {},
            onOpenRunStatus = {},
            onOpenSettings = {},
            onOpenPrivacy = {},
        )
    }
}
