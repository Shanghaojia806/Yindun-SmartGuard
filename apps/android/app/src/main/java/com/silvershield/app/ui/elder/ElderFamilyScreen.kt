package com.silvershield.app.ui.elder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.family.ConsentUiState
import com.silvershield.app.family.FamilyRelationStatus
import com.silvershield.app.family.FamilyUiState
import com.silvershield.app.family.InviteParty
import com.silvershield.app.ui.components.FamilySection
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.components.PrivacyNote
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.components.formatRiskTime
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Spacing

/**
 * E12 · 家庭关系管理首页（P07）。
 *
 * 家庭关系管理与风险共享授权管理分开呈现，便于老人理解
 * "先绑定、再单独授权"这两个独立步骤。
 */
@Composable
fun ElderFamilyScreen(
    familyUiState: FamilyUiState,
    consentUiState: ConsentUiState,
    onOpenRelation: () -> Unit,
    onOpenConsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val relation = familyUiState.relation
    val status = relation?.relationStatus

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = Spacing.pageHorizontal,
            vertical = Spacing.pageVertical,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            Column {
                Text(
                    text = "家庭关系管理",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "这里需要两步：先和家人互相确认绑定，再由你决定是否共享风险信息。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.textSecondary,
                )
            }
        }

        item {
            when (status) {
                FamilyRelationStatus.ACTIVE -> StatusCard(
                    title = "已和家人绑定",
                    description = "绑定时间：${relation.confirmedAt?.let(::formatRiskTime) ?: "—"}。" +
                        "绑定不会自动共享风险信息。",
                    tone = StatusTone.Safe,
                    statusLabel = "已绑定",
                )

                FamilyRelationStatus.PENDING -> if (relation.initiatedBy == InviteParty.Family) {
                    StatusCard(
                        title = "家人邀请你绑定",
                        description = "家人已经发出邀请，请进入「家庭关系管理」输入对方的邀请码并确认。",
                        tone = StatusTone.Info,
                        statusLabel = "待你确认",
                    )
                } else {
                    StatusCard(
                        title = "等待对方确认",
                        description = "邀请已经发出，对方接受后才会建立家庭关系。",
                        tone = StatusTone.Info,
                        statusLabel = "等待确认",
                    )
                }

                else -> StatusCard(
                    title = "还没有绑定家人",
                    description = "绑定需要邀请码并经过双方确认。没有绑定也可以正常使用风险提醒和记录。",
                    tone = StatusTone.Neutral,
                    statusLabel = "未绑定",
                )
            }
        }

        item {
            FamilySection(
                title = "第一步：家庭关系",
                subtitle = "生成邀请码或输入家人的邀请码，双方确认后建立关系。",
            ) {
                PrimaryButton(
                    text = "管理家庭关系",
                    onClick = onOpenRelation,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            FamilySection(
                title = "第二步：风险共享授权",
                subtitle = "由你单独决定共享哪些风险信息；不设置就不会共享。",
            ) {
                Text(
                    text = if (consentUiState.activeConsents.isEmpty()) {
                        "当前没有共享授权"
                    } else {
                        "当前有 ${consentUiState.activeConsents.size} 项共享授权"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.textPrimary,
                )
                PrimaryButton(
                    text = "管理风险共享授权",
                    onClick = onOpenConsent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            FamilySection(title = "请放心") {
                PrivacyNote("家庭绑定和共享授权是两件独立的事，绑定后不会自动共享任何风险信息。")
                PrivacyNote("你可以随时撤销授权。撤销后家人立即看不到对应内容。")
                PrivacyNote("不会共享原始屏幕录像、实时屏幕或完整浏览历史。")
                Spacer(Modifier.height(Spacing.xxs))
                SampleTag()
            }
        }
    }
}

@Preview(name = "E12 家庭关系管理", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 820)
@Composable
private fun ElderFamilyScreenPreview() {
    SilverShieldTheme {
        ElderFamilyScreen(
            familyUiState = FamilyUiState(loading = false),
            consentUiState = ConsentUiState(loading = false),
            onOpenRelation = {},
            onOpenConsent = {},
        )
    }
}
