package com.silvershield.app.ui.elder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.family.FamilyRelationStatus
import com.silvershield.app.family.FamilyUiState
import com.silvershield.app.family.InviteParty
import com.silvershield.app.family.InviteStatus
import com.silvershield.app.ui.components.ErrorState
import com.silvershield.app.ui.components.FamilySection
import com.silvershield.app.ui.components.InfoRow
import com.silvershield.app.ui.components.InviteCodeBox
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
 * 老人端 · 家庭关系管理（P07）。
 *
 * 与家属端同一套仓库规则：角色中立邀请、双向确认、邀请码单次有效、
 * 解除绑定必须二次确认。这里只管理"关系"，不管理风险共享授权。
 */
@Composable
fun ElderFamilyRelationScreen(
    uiState: FamilyUiState,
    onGenerateInvite: () -> Unit,
    onLookupInvite: (String) -> Unit,
    onAcceptInvite: (String) -> Unit,
    onRejectInvite: (String) -> Unit,
    onCancelInvite: (String) -> Unit,
    onRevokeRelation: (String) -> Unit,
    onConsumeMessage: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val snackbarHostState = remember { SnackbarHostState() }
    var codeInput by rememberSaveable { mutableStateOf("") }
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
            val pendingInvite = uiState.pendingInvite ?: uiState.generatedInvite?.takeIf {
                it.status == InviteStatus.PENDING
            }

            LazyColumn(
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
                            text = "家庭关系",
                            style = MaterialTheme.typography.headlineSmall,
                            color = tokens.textPrimary,
                        )
                        Text(
                            text = "先和家人互相确认，再决定是否共享风险信息。绑定本身不会共享任何内容。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.textSecondary,
                        )
                    }
                }

                item(key = "status") {
                    when (status) {
                        FamilyRelationStatus.ACTIVE -> StatusCard(
                            title = "已和${relation.counterpartNameOf(InviteParty.Elder)}绑定",
                            description = "绑定时间：${relation.confirmedAt?.let(::formatRiskTime) ?: "—"}。" +
                                "共享授权仍然默认关闭。",
                            tone = StatusTone.Safe,
                            statusLabel = "已绑定",
                        )

                        FamilyRelationStatus.PENDING -> if (relation.initiatedBy == InviteParty.Family) {
                            StatusCard(
                                title = "家人邀请你确认",
                                description = "家人已经发出邀请，输入对方的 6 位邀请码即可核对并确认。",
                                tone = StatusTone.Info,
                                statusLabel = "待你确认",
                            )
                        } else {
                            StatusCard(
                                title = "等待对方确认",
                                description = "对方接受邀请后，家庭关系才会建立。",
                                tone = StatusTone.Info,
                                statusLabel = "等待确认",
                            )
                        }

                        FamilyRelationStatus.REJECTED -> StatusCard(
                            title = "上次邀请被拒绝",
                            description = "没有建立家庭关系。可以重新生成邀请码。",
                            tone = StatusTone.Warning,
                            statusLabel = "已拒绝",
                        )

                        FamilyRelationStatus.EXPIRED -> StatusCard(
                            title = "上次邀请已过期",
                            description = "邀请码超过 24 小时会失效，可以重新生成。",
                            tone = StatusTone.Neutral,
                            statusLabel = "已过期",
                        )

                        FamilyRelationStatus.REVOKED -> StatusCard(
                            title = "家庭关系已解除",
                            description = "这段关系和对应的共享授权都已失效。可以重新绑定。",
                            tone = StatusTone.Danger,
                            statusLabel = "已解除",
                        )

                        else -> StatusCard(
                            title = "还没有绑定家人",
                            description = "生成邀请码交给家人，或输入家人给你的邀请码。",
                            tone = StatusTone.Neutral,
                            statusLabel = "未绑定",
                        )
                    }
                }

                if (relation?.relationStatus == FamilyRelationStatus.ACTIVE) {
                    val active = checkNotNull(relation)
                    item(key = "member") {
                        FamilySection(title = "已绑定的家属") {
                            InfoRow(label = "家属", value = active.familyDisplayName)
                            InfoRow(label = "老人", value = active.elderDisplayName)
                            InfoRow(
                                label = "绑定时间",
                                value = active.confirmedAt?.let(::formatRiskTime) ?: "—",
                            )
                            InfoRow(label = "发起方", value = active.initiatedBy.label)
                        }
                    }
                    item(key = "revoke") {
                        FamilySection(title = "解除绑定") {
                            Text(
                                text = "解除后，家属立即失去所有共享授权，但不会删除你本机的风险记录。",
                                style = MaterialTheme.typography.bodyLarge,
                                color = tokens.textPrimary,
                            )
                            OutlinedButton(
                                onClick = { showRevokeDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Sizes.primaryButtonHeight),
                            ) {
                                Text(
                                    text = "解除家庭关系",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                } else {
                    if (pendingInvite != null) {
                        item(key = "pending-invite") {
                            FamilySection(title = "等待对方确认") {
                                InviteCodeBox(code = pendingInvite.inviteCode)
                                InfoRow(label = "有效期至", value = formatRiskTime(pendingInvite.expiresAt))
                                InfoRow(
                                    label = "等待对象",
                                    value = "${pendingInvite.inviteeRole.label}（本机模拟身份）",
                                )
                                Text(
                                    text = "对方接受前不会显示绑定成功，邀请码只能使用一次。",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = tokens.textPrimary,
                                )
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
                    } else {
                        item(key = "generate") {
                            FamilySection(title = "生成邀请码") {
                                Text(
                                    text = "生成后把 6 位邀请码告诉家人。邀请码 24 小时内有效，只能使用一次。",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = tokens.textPrimary,
                                )
                                PrimaryButton(
                                    text = "生成邀请码",
                                    onClick = onGenerateInvite,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    item(key = "lookup") {
                        FamilySection(
                            title = "输入家人的邀请码",
                            subtitle = "先核对对方身份，确认无误再接受。",
                        ) {
                            OutlinedTextField(
                                value = codeInput,
                                onValueChange = { raw ->
                                    codeInput = raw.uppercase()
                                        .filter { it.isLetterOrDigit() }
                                        .take(6)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("6 位邀请码") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                ),
                            )
                            PrimaryButton(
                                text = "查询邀请码",
                                onClick = { onLookupInvite(codeInput) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = codeInput.length == 6,
                            )
                        }
                    }
                }

                uiState.preview?.let { invite ->
                    item(key = "preview") {
                        FamilySection(title = "核对邀请信息") {
                            InfoRow(
                                label = "邀请发起方",
                                value = "${invite.inviterRole.label}：${invite.inviterDisplayName}",
                            )
                            InfoRow(
                                label = "对方身份",
                                value = "${invite.inviteeRole.label}：${invite.inviteeDisplayName}",
                            )
                            InfoRow(label = "有效期至", value = formatRiskTime(invite.expiresAt))
                            Text(
                                text = "接受后会建立家庭关系，但不会自动共享风险信息。",
                                style = MaterialTheme.typography.bodyLarge,
                                color = tokens.textPrimary,
                            )
                            PrimaryButton(
                                text = "接受邀请并绑定",
                                onClick = { onAcceptInvite(invite.inviteCode) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedButton(
                                onClick = { onRejectInvite(invite.inviteCode) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Sizes.primaryButtonHeight),
                            ) {
                                Text(text = "拒绝这次邀请", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                item(key = "notes") {
                    FamilySection(title = "请放心") {
                        PrivacyNote("绑定不等于授权。绑定成功后，风险信息仍然默认不共享。")
                        PrivacyNote("解除绑定不会删除本机风险记录。")
                        PrivacyNote("本轮为本地模拟绑定，未接入真实账号与服务器。")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SampleTag()
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                text = "模拟数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textTertiary,
                            )
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
                        "· 本机风险记录不会被删除；\n" +
                        "· 之后可以重新邀请家人。",
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

@Preview(name = "老人端 · 家庭关系 · 未绑定", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 1100)
@Composable
private fun ElderFamilyRelationScreenPreview() {
    SilverShieldTheme {
        ElderFamilyRelationScreen(
            uiState = FamilyUiState(loading = false),
            onGenerateInvite = {},
            onLookupInvite = {},
            onAcceptInvite = {},
            onRejectInvite = {},
            onCancelInvite = {},
            onRevokeRelation = {},
            onConsumeMessage = {},
            onRetry = {},
        )
    }
}
