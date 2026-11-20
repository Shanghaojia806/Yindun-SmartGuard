package com.silvershield.app.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.family.FamilyRelationStatus
import com.silvershield.app.family.FamilyUiState
import com.silvershield.app.family.InviteStatus
import com.silvershield.app.ui.components.ErrorState
import com.silvershield.app.ui.components.FamilySection
import com.silvershield.app.ui.components.InfoRow
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.components.PrivacyNote
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.components.formatRiskTime
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * F04 关联关系（P07）。
 *
 * 展示未绑定 / 等待确认 / 已绑定 / 已拒绝 / 已过期 / 已解除等状态。
 * 解除绑定必须二次确认，并明确说明对共享授权的影响。
 */
@Composable
fun FamilyRelationScreen(
    uiState: FamilyUiState,
    onOpenBind: () -> Unit,
    onCancelInvite: (String) -> Unit,
    onRevokeRelation: (String) -> Unit,
    onConsumeMessage: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val snackbarHostState = remember { SnackbarHostState() }
    var showRevokeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onConsumeMessage()
    }

    when {
        uiState.loading -> LoadingState(modifier = modifier.fillMaxSize(), message = "正在读取家庭关系…")

        uiState.loadFailed -> ErrorState(
            modifier = modifier.fillMaxSize(),
            title = "家庭关系读取失败",
            description = "本地数据暂时无法读取，请重试。",
            onRetry = onRetry,
        )

        else -> {
            val relation = uiState.relation
            val status = relation?.relationStatus
            LazyColumn(
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
                            text = "页面 F04",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "关联关系",
                            style = MaterialTheme.typography.headlineSmall,
                            color = tokens.textPrimary,
                        )
                        Text(
                            text = "这里显示你和家人的绑定状态。解除绑定会同时停止共享，但不会删除老人本机的风险记录。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.textSecondary,
                        )
                    }
                }

                when (status) {
                    FamilyRelationStatus.ACTIVE -> {
                        val active = checkNotNull(relation)
                        item(key = "bound") {
                            FamilySection(title = "已建立家庭关系") {
                                StatusCard(
                                    title = "已绑定 ${active.counterpartNameOf(active.initiatedBy)}",
                                    description = "关系状态：已绑定。共享授权仍然默认关闭，需老人单独设置。",
                                    tone = StatusTone.Safe,
                                    statusLabel = "已绑定",
                                )
                                InfoRow(label = "老人", value = active.elderDisplayName)
                                InfoRow(label = "家属", value = active.familyDisplayName)
                                InfoRow(
                                    label = "绑定时间",
                                    value = active.confirmedAt?.let(::formatRiskTime) ?: "—",
                                )
                                InfoRow(
                                    label = "绑定由谁发起",
                                    value = active.initiatedBy.label,
                                )
                            }
                        }

                        item(key = "revoke") {
                            FamilySection(title = "解除绑定") {
                                Text(
                                    text = "解除后，这段家庭关系立即失效，家属将无法再查看任何共享内容。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.textPrimary,
                                )
                                OutlinedButton(
                                    onClick = { showRevokeDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = Sizes.primaryButtonHeight),
                                ) {
                                    Text(text = "解除家庭关系", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    FamilyRelationStatus.PENDING -> {
                        val pendingRelation = checkNotNull(relation)
                        val pendingInvite = uiState.invites.firstOrNull {
                            it.status == InviteStatus.PENDING
                        }
                        item(key = "pending") {
                            FamilySection(title = "等待确认") {
                                StatusCard(
                                    title = "还没有完成绑定",
                                    description = "邀请已经发出，需要对方确认后才会建立家庭关系。",
                                    tone = StatusTone.Info,
                                    statusLabel = "等待对方确认",
                                )
                                InfoRow(label = "邀请发起方", value = pendingRelation.initiatedBy.label)
                                InfoRow(
                                    label = "等待对象",
                                    value = pendingRelation.counterpartNameOf(pendingRelation.initiatedBy),
                                )
                                InfoRow(
                                    label = "邀请有效期至",
                                    value = formatRiskTime(pendingRelation.expiresAt),
                                )
                                if (pendingInvite != null) {
                                    OutlinedButton(
                                        onClick = { onCancelInvite(pendingInvite.inviteId) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = Sizes.primaryButtonHeight),
                                    ) {
                                        Text(text = "取消邀请", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }

                    FamilyRelationStatus.REVOKED -> {
                        item(key = "revoked") {
                            StatusCard(
                                title = "家庭关系已解除",
                                description = "双方都不再看到这段关系，相关共享授权已同时失效。可以重新绑定。",
                                tone = StatusTone.Danger,
                                statusLabel = "已解除",
                            )
                        }
                        item(key = "rebind") {
                            PrimaryButton(
                                text = "重新绑定家人",
                                onClick = onOpenBind,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    FamilyRelationStatus.REJECTED, FamilyRelationStatus.EXPIRED -> {
                        item(key = "closed") {
                            StatusCard(
                                title = if (status == FamilyRelationStatus.REJECTED) {
                                    "上次邀请已被拒绝"
                                } else {
                                    "上次邀请已过期"
                                },
                                description = "没有建立家庭关系，也没有产生任何共享授权。可以重新发起邀请。",
                                tone = StatusTone.Warning,
                                statusLabel = status.label,
                            )
                        }
                        item(key = "retry-bind") {
                            PrimaryButton(
                                text = "重新邀请家人",
                                onClick = onOpenBind,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    else -> {
                        item(key = "empty") {
                            StatusCard(
                                title = "尚未建立家庭关系",
                                description = "你可以生成邀请码，也可以输入家人给你的邀请码。双方确认后才会绑定。",
                                tone = StatusTone.Neutral,
                                statusLabel = "未绑定",
                            )
                        }
                        item(key = "bind") {
                            PrimaryButton(
                                text = "去绑定家人",
                                onClick = onOpenBind,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                item(key = "notes") {
                    FamilySection(title = "关于绑定与共享") {
                        PrivacyNote("绑定不等于授权。绑定成功后，风险信息仍然默认不共享。")
                        PrivacyNote("解除绑定不会删除老人本机的风险记录，也不需要重新采集屏幕。")
                        androidx.compose.foundation.layout.Row {
                            SampleTag()
                        }
                    }
                }
            }
        }
    }

    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = tokens.danger,
                )
            },
            title = { Text(text = "确定解除家庭关系？", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "解除后：\n" +
                        "· 家属立即失去所有共享授权；\n" +
                        "· 已分享的风险事件不再显示；\n" +
                        "· 老人本机的风险记录不会被删除；\n" +
                        "· 之后可以重新邀请绑定。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeDialog = false
                        uiState.relation?.let { onRevokeRelation(it.relationId) }
                    },
                ) {
                    Text(text = "确认解除", color = tokens.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) {
                    Text(text = "再想想")
                }
            },
        )
    }

    SnackbarHost(hostState = snackbarHostState)
}

@Preview(name = "F04 关联关系 · 未绑定", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 900)
@Composable
private fun FamilyRelationScreenPreview() {
    SilverShieldTheme {
        FamilyRelationScreen(
            uiState = FamilyUiState(loading = false),
            onOpenBind = {},
            onCancelInvite = {},
            onRevokeRelation = {},
            onConsumeMessage = {},
            onRetry = {},
        )
    }
}
