package com.silvershield.app.ui.elder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.guard.GuardPreferences
import com.silvershield.app.guard.ScreenAuthorizationState
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Spacing

/**
 * E14 隐私与授权。
 *
 * 只展示与修改本地模拟授权状态；真实服务端授权、家庭共享与数据删除尚未接入，
 * 未实现的操作均在页面上明确标注，不伪装为已生效。
 */
@Composable
fun ElderPrivacyScreen(
    preferences: GuardPreferences,
    onOpenAuthorization: () -> Unit,
    onRevokeAuthorization: () -> Unit,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val authorization = preferences.screenAuthorization
    var showRevokeDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val authorizationColor = when (authorization) {
        ScreenAuthorizationState.GRANTED -> tokens.safe
        ScreenAuthorizationState.REVOKED -> tokens.danger
        ScreenAuthorizationState.NOT_GRANTED -> tokens.textSecondary
    }

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
                    text = "页面 E14",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = "隐私与授权",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "您的屏幕内容默认留在本机。这里可以查看和调整授权状态。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.textSecondary,
                )
            }
        }

        item {
            GuardSection(title = "屏幕采集权限") {
                GuardInfoRow(
                    label = "当前授权状态",
                    value = authorization.displayLabel(),
                    valueColor = authorizationColor,
                )
                Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                    Text(
                        text = "授权后，守护才可能在本机分析短视频画面。P04 使用模拟授权，" +
                            "不会调用 Android 系统屏幕采集，也不会申请无障碍权限。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.textSecondary,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    GuardEntryCard(
                        title = "查看授权说明",
                        description = "重新了解采集范围、本机处理与撤销方式",
                        icon = Icons.Filled.Lock,
                        pageCode = "E04",
                        onClick = onOpenAuthorization,
                    )
                    if (authorization == ScreenAuthorizationState.GRANTED) {
                        Spacer(Modifier.height(Spacing.md))
                        GuardStopButton(
                            text = "撤销屏幕采集授权",
                            onClick = { showRevokeDialog = true },
                        )
                    } else {
                        Spacer(Modifier.height(Spacing.md))
                        PrimaryButton(
                            text = "前往授权说明",
                            onClick = onOpenAuthorization,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item {
            GuardSection(title = "家庭共享权限") {
                GuardInfoRow(
                    label = "家属共享状态",
                    value = if (preferences.familySharingEnabled) "已开启" else "未开启（默认关闭）",
                )
                Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                    Text(
                        text = "默认关闭。只有您主动授权后，家人才可以查看您选择共享的风险事件及处理状态。" +
                            "家人不能查看您的实时屏幕，也不能查看完整浏览历史。",
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.textSecondary,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "本轮不提供家庭共享的开启或撤销能力，相关功能将在 P07 家庭绑定阶段实现。",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textTertiary,
                    )
                }
            }
        }

        item {
            GuardSection(title = "数据管理") {
                GuardEntryCard(
                    title = "本地风险记录",
                    description = "查看与管理本机风险事件（P06 实现）",
                    icon = Icons.AutoMirrored.Filled.List,
                    pageCode = "E08",
                    onClick = onOpenRecords,
                )
                Spacer(Modifier.height(Spacing.md))
                GuardInfoRow(
                    label = "删除本地数据",
                    value = "当前尚未产生本地风险记录。删除与清空能力将在 P06 记录模块提供。",
                )
                GuardInfoRow(
                    label = "共享授权撤销",
                    value = "当前没有家庭共享授权，暂无可撤销项。",
                )
                GuardEntryCard(
                    title = "隐私政策说明",
                    description = "查看本机处理、最小共享与撤销原则",
                    icon = Icons.Filled.Info,
                    onClick = { showPrivacyDialog = true },
                )
            }
        }

        item {
            Text(
                text = "以上为当前版本（P04 模拟守护）的隐私说明。真实屏幕采集是否可行，以 P05 验证结果为准。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textTertiary,
            )
        }
    }

    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = {
                Text(
                    text = "撤销屏幕采集授权？",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = "撤销后守护会立即停止，模拟守护状态不会再显示为运行中。需要时可以重新授权。",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeDialog = false
                        onRevokeAuthorization()
                    },
                ) {
                    Text(text = "确认撤销", color = tokens.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) {
                    Text(text = "取消")
                }
            },
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Text(
                    text = "隐私政策说明",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column {
                    Text(
                        text = "1. 默认不采集：只有您主动开启守护并授权后，才可能进行分析。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "2. 本机处理：识别在手机本地完成，原始屏幕录像默认不上传。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "3. 最小共享：只有您主动共享的风险事件摘要，家人才可以看到。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "4. 可随时撤销：您可以停止守护并撤销屏幕采集授权。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "5. 当前为模拟守护：P04 不会真实采集屏幕、录音或上传数据。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(text = "我知道了")
                }
            },
        )
    }
}

@Preview(name = "E14 隐私与授权 · 未授权", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 1000)
@Composable
private fun ElderPrivacyScreenPreview() {
    SilverShieldTheme {
        ElderPrivacyScreen(
            preferences = GuardPreferences(),
            onOpenAuthorization = {},
            onRevokeAuthorization = {},
            onOpenRecords = {},
        )
    }
}
