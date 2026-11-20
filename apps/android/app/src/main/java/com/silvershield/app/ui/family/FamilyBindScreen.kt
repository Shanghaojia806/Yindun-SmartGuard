package com.silvershield.app.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.family.FamilyInvite
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
 * F02 绑定老人（P07）。
 *
 * 角色中立：家属与老人都可以生成邀请码，也都可以输入对方邀请码。
 * 邀请码被查询后只展示对方信息；必须由受邀方点击「接受」才建立关系。
 */
@Composable
fun FamilyBindScreen(
    uiState: FamilyUiState,
    onGenerateInvite: () -> Unit,
    onLookupInvite: (String) -> Unit,
    onAcceptInvite: (String) -> Unit,
    onRejectInvite: (String) -> Unit,
    onCancelInvite: (String) -> Unit,
    onOpenRelation: () -> Unit,
    onConsumeMessage: () -> Unit,
    onClearPreview: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val snackbarHostState = remember { SnackbarHostState() }
    var codeInput by rememberSaveable { mutableStateOf("") }

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

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = Spacing.pageHorizontal,
                vertical = Spacing.pageVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            item(key = "header") {
                ColumnHeader(
                    pageCode = "F02",
                    title = "绑定老人",
                    description = "和老人互相确认后，才能建立家庭关系。绑定本身不会看到任何风险信息。",
                )
            }

            item(key = "status") {
                BindStatusCard(
                    uiState = uiState,
                    onOpenRelation = onOpenRelation,
                )
            }

            val pending = uiState.pendingInvite ?: uiState.generatedInvite?.takeIf {
                it.status == InviteStatus.PENDING
            }
            if (pending != null && !uiState.isBound) {
                item(key = "pending-invite") {
                    PendingInviteCard(
                        invite = pending,
                        onCancel = { onCancelInvite(pending.inviteId) },
                    )
                }
            }

            if (!uiState.isBound && pending == null) {
                item(key = "generate") {
                    FamilySection(title = "生成邀请码") {
                        Text(
                            text = "点击生成后，把 6 位邀请码告诉对方。邀请码 24 小时内有效，只能使用一次。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textPrimary,
                        )
                        PrimaryButton(
                            text = "生成邀请码",
                            onClick = onGenerateInvite,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item(key = "lookup") {
                    FamilySection(
                        title = "输入对方的邀请码",
                        subtitle = "输入后先核对对方身份，确认无误再接受。",
                    ) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { raw ->
                                codeInput = raw.uppercase()
                                    .filter { it.isLetterOrDigit() }
                                    .take(6)
                                // 邀请码变化后旧预览不再对应当前输入，避免误接受
                                onClearPreview()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("6 位邀请码") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Text,
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
                    InvitePreviewCard(
                        invite = invite,
                        onAccept = { onAcceptInvite(invite.inviteCode) },
                        onReject = { onRejectInvite(invite.inviteCode) },
                    )
                }
            }

            item(key = "privacy") {
                FamilySection(title = "关于家庭绑定") {
                    PrivacyNote("绑定前和绑定后都不会自动共享风险信息；只有老人单独授权后，家属才能查看授权范围内的内容。")
                    PrivacyNote("不会共享原始屏幕录像、实时屏幕或完整浏览历史。")
                    PrivacyNote("本轮为本地模拟绑定，未接入真实账号与服务器，断网也可操作。")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SampleTag()
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = "本页邀请码和关系均为模拟数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textTertiary,
                        )
                    }
                }
            }
        }
    }

    SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(top = Spacing.md))
}

@Composable
private fun ColumnHeader(pageCode: String, title: String, description: String) {
    val tokens = SilverShieldTokens.colors
    androidx.compose.foundation.layout.Column {
        Text(
            text = "页面 $pageCode",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.textPrimary,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.textSecondary,
        )
    }
}

@Composable
private fun BindStatusCard(uiState: FamilyUiState, onOpenRelation: () -> Unit) {
    val relation = uiState.relation
    val status = relation?.relationStatus
    when (status) {
        FamilyRelationStatus.ACTIVE -> StatusCard(
            title = "已绑定：${relation.counterpartNameOf(InviteParty.Family)}",
            description = "绑定时间：${relation.confirmedAt?.let(::formatRiskTime) ?: "—"}。" +
                "家属目前仍看不到风险信息，需要老人单独授权。",
            tone = StatusTone.Safe,
            statusLabel = "已绑定",
            actionLabel = "查看关联关系",
            onAction = onOpenRelation,
        )

        FamilyRelationStatus.PENDING -> StatusCard(
            title = "等待对方确认",
            description = "邀请由${relation.initiatedBy.label}发起，等待${relation.initiatedBy.other().label}确认。" +
                "对方接受前不会建立正式关系。",
            tone = StatusTone.Info,
            statusLabel = "等待确认",
            actionLabel = "查看关联关系",
            onAction = onOpenRelation,
        )

        FamilyRelationStatus.REJECTED -> StatusCard(
            title = "上次邀请已被拒绝",
            description = "对方拒绝后不会建立家庭关系。可以重新生成邀请码再试一次。",
            tone = StatusTone.Warning,
            statusLabel = "已拒绝",
        )

        FamilyRelationStatus.EXPIRED -> StatusCard(
            title = "上次邀请已过期",
            description = "邀请码超过 24 小时后失效，可以重新生成一条邀请。",
            tone = StatusTone.Neutral,
            statusLabel = "已过期",
        )

        else -> StatusCard(
            title = "还没有绑定家人",
            description = "绑定需要通过邀请码双向确认。绑定成功后，还需要老人主动授权才能查看风险信息。",
            tone = StatusTone.Neutral,
            statusLabel = "未绑定",
        )
    }
}

@Composable
private fun PendingInviteCard(invite: FamilyInvite, onCancel: () -> Unit) {
    val tokens = SilverShieldTokens.colors
    FamilySection(title = "等待对方确认") {
        InviteCodeBox(code = invite.inviteCode)
        InfoRow(label = "有效期至", value = formatRiskTime(invite.expiresAt))
        InfoRow(label = "等待", value = LocalWaitText(invite))
        Text(
            text = "对方接受之前不会显示绑定成功。邀请码只能使用一次。",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textPrimary,
        )
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.primaryButtonHeight),
        ) {
            Text(text = "取消邀请", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun LocalWaitText(invite: FamilyInvite): String =
    "${invite.inviteeRole.label}（本机模拟身份）确认"

@Composable
private fun InvitePreviewCard(
    invite: FamilyInvite,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val tokens = SilverShieldTokens.colors
    FamilySection(title = "核对邀请信息") {
        InfoRow(label = "邀请发起方", value = "${invite.inviterRole.label}：${invite.inviterDisplayName}")
        InfoRow(label = "对方身份", value = "${invite.inviteeRole.label}：${invite.inviteeDisplayName}")
        InfoRow(label = "邀请有效期至", value = formatRiskTime(invite.expiresAt))
        Text(
            text = "接受后会建立家庭关系，但不会自动共享风险信息。",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textPrimary,
        )
        PrimaryButton(
            text = "接受邀请并绑定",
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = onReject,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.primaryButtonHeight),
        ) {
            Text(text = "拒绝这次邀请", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview(name = "F02 绑定老人 · 未绑定", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 1100)
@Composable
private fun FamilyBindScreenPreview() {
    SilverShieldTheme {
        FamilyBindScreen(
            uiState = FamilyUiState(loading = false),
            onGenerateInvite = {},
            onLookupInvite = {},
            onAcceptInvite = {},
            onRejectInvite = {},
            onCancelInvite = {},
            onOpenRelation = {},
            onConsumeMessage = {},
            onClearPreview = {},
            onRetry = {},
        )
    }
}
