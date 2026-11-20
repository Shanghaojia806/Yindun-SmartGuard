package com.silvershield.app.ui.family

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.silvershield.app.family.FamilyEventUiState
import com.silvershield.app.family.HandlingStatus
import com.silvershield.app.ui.components.FamilySection
import com.silvershield.app.ui.components.InfoRow
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.components.PrivacyNote
import com.silvershield.app.ui.components.RiskBadge
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.components.formatRiskTime
import com.silvershield.app.ui.components.toRiskLevel
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * F07 协同处理动作页。
 *
 * - 联系老人：演示环境没有真实号码，使用明确标记的模拟联系入口，不虚构电话号码；
 * - 状态更新与备注通过 ViewModel → Repository 执行，并追加历史记录；
 * - 家属不能修改 AI 原始判断，也不能删除原始事件。
 */
@Composable
fun FamilyCooperateScreen(
    uiState: FamilyEventUiState,
    onUpdateStatus: (HandlingStatus, String) -> Unit,
    onOpenHandling: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val context = LocalContext.current

    if (uiState.loading && uiState.share == null && uiState.accessDenied == null) {
        LoadingState(message = "正在准备协同处理…", modifier = modifier.fillMaxSize())
        return
    }

    val denied = uiState.accessDenied
    if (denied != null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.pageHorizontal, vertical = Spacing.pageVertical),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            StatusCard(
                title = "共享已失效，不能继续处理",
                description = denied.message,
                tone = StatusTone.Danger,
                statusLabel = "授权校验",
                actionLabel = "重新检查",
                onAction = onRetry,
            )
            PrivacyNote("授权撤销或关系解除后，家属端立即停止写入，历史处理记录保留。")
        }
        return
    }

    val event = uiState.event ?: return
    val share = uiState.share ?: return

    var selectedStatusName by rememberSaveable(share.shareId, uiState.handlingStatus) {
        mutableStateOf(uiState.handlingStatus.name)
    }
    var note by rememberSaveable(share.shareId) { mutableStateOf("") }
    var showContactDialog by rememberSaveable { mutableStateOf(false) }
    val selectedStatus = HandlingStatus.entries.firstOrNull { it.name == selectedStatusName }
        ?: HandlingStatus.PendingView

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.pageHorizontal, vertical = Spacing.pageVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = tokens.textPrimary,
        )
        RiskBadge(category = event.riskType.label, level = event.riskLevel.toRiskLevel())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = "当前协同状态",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
                modifier = Modifier.weight(1f),
            )
            HandlingStatusChip(status = uiState.handlingStatus)
        }

        FamilySection(
            title = "联系老人",
            subtitle = "先确认老人当前是否安全，再记录核实情况",
        ) {
            InfoRow(
                label = "可用联系方式",
                value = "演示环境没有老人的真实电话号码，系统不会虚构号码。",
            )
            PrimaryButton(
                text = "模拟联系老人（演示）",
                onClick = { showContactDialog = true },
            )
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:110"))
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.primaryButtonHeight),
            ) {
                Text(text = "紧急情况：拨打 110", style = MaterialTheme.typography.labelLarge)
            }
        }

        FamilySection(
            title = "更新处理状态",
            subtitle = "状态变化会追加到处理历史，不会覆盖之前记录",
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                HandlingStatus.entries.forEach { status ->
                    FilterChip(
                        selected = selectedStatus == status,
                        onClick = { selectedStatusName = status.name },
                        label = {
                            Text(text = status.label, style = MaterialTheme.typography.labelMedium)
                        },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("处理备注") },
                placeholder = {
                    Text("可记录联系情况、核实结果、提供的帮助与后续处理建议")
                },
                minLines = 3,
                maxLines = 6,
                enabled = !uiState.saving,
            )
            Text(
                text = "AI 原始等级：${event.riskLevel.label}。该判断由系统保存，家属不能修改。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textTertiary,
            )
            PrimaryButton(
                text = if (uiState.saving) "正在保存…" else "保存处理记录",
                onClick = { onUpdateStatus(selectedStatus, note) },
                enabled = !uiState.saving,
            )
            if (uiState.message != null) {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.message.contains("已保存")) tokens.safe else tokens.danger,
                )
            }
        }

        if (uiState.history.isNotEmpty()) {
            FamilySection(
                title = "最近处理记录",
                subtitle = "仅展示最近 3 条，完整时间线在 F08",
            ) {
                uiState.history.takeLast(3).reversed().forEach { record ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            HandlingStatusChip(status = record.handlingStatus)
                            Text(
                                text = formatRiskTime(record.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textTertiary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (record.note.isNotBlank()) {
                            Text(
                                text = record.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textPrimary,
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { onOpenHandling(share.shareId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.primaryButtonHeight),
                ) {
                    Text(text = "查看完整处理详情", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            OutlinedButton(
                onClick = { onOpenHandling(share.shareId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.primaryButtonHeight),
            ) {
                Text(text = "查看处理详情", style = MaterialTheme.typography.labelLarge)
            }
        }

        PrivacyNote(
            "家属不能把事件改为「AI 已确认安全」，不能删除老人的原始风险事件；只能追加自己的协同处理记录。",
        )
    }

    if (showContactDialog) {
        AlertDialog(
            onDismissRequest = { showContactDialog = false },
            title = { Text("模拟联系（演示）") },
            text = {
                Text(
                    "这是本地模拟入口，未接入真实电话、通讯录或跨设备消息。请通过你平时的方式联系老人；紧急情况请直接拨打 110。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showContactDialog = false }) {
                    Text("知道了")
                }
            },
        )
    }
}
