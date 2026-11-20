package com.silvershield.app.ui.family

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.family.FamilyRelationStatus
import com.silvershield.app.family.FamilyUiState
import com.silvershield.app.family.InviteParty
import com.silvershield.app.navigation.FamilyTab
import com.silvershield.app.role.UserRole
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.components.SampleTag
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * F12 个人中心。
 *
 * 本轮新增 P07 入口：
 * - F02 绑定老人；
 * - F04 关联关系；
 * - F09 共享授权（只读）。
 *
 * 家庭关系管理与授权管理分开呈现，不合并为一个开关。
 */
@Composable
fun FamilyProfileScreen(
    uiState: FamilyUiState,
    onOpenBind: () -> Unit,
    onOpenRelation: () -> Unit,
    onOpenConsent: () -> Unit,
    onSwitchRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val tab = FamilyTab.Profile
    val relation = uiState.relation
    val status = relation?.relationStatus

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.pageHorizontal,
            end = Spacing.pageHorizontal,
            top = Spacing.pageVertical,
            bottom = Spacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            Column {
                Text(
                    text = "页面 ${tab.pageCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = tab.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.textSecondary,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.lg),
                color = tokens.surfaceCard,
                border = BorderStroke(Sizes.borderWidth, tokens.border),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.cardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = tokens.accentLight,
                        modifier = Modifier.size(Sizes.statusCardIconContainer),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(Spacing.sm),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Spacing.md),
                    ) {
                        Text(
                            text = "当前身份：${UserRole.Family.label}",
                            style = MaterialTheme.typography.titleMedium,
                            color = tokens.textPrimary,
                        )
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(
                            text = "本地模拟登录，未接入真实账号",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                        )
                    }
                }
            }
        }

        item {
            when (status) {
                FamilyRelationStatus.ACTIVE -> StatusCard(
                    title = "已绑定老人",
                    description = "绑定关系已建立，但风险信息默认不共享，需要老人主动授权。",
                    tone = StatusTone.Safe,
                    statusLabel = "已绑定",
                    actionLabel = "查看关联关系",
                    onAction = onOpenRelation,
                )

                FamilyRelationStatus.PENDING -> if (relation.initiatedBy == InviteParty.Family) {
                    StatusCard(
                        title = "等待老人确认",
                        description = "邀请已发出，老人确认后才会建立家庭关系。",
                        tone = StatusTone.Info,
                        statusLabel = "等待确认",
                        actionLabel = "查看关联关系",
                        onAction = onOpenRelation,
                    )
                } else {
                    StatusCard(
                        title = "老人邀请你绑定",
                        description = "老人已经发出邀请，进入「绑定老人」输入对方的邀请码即可确认。",
                        tone = StatusTone.Info,
                        statusLabel = "待你确认",
                        actionLabel = "查看关联关系",
                        onAction = onOpenRelation,
                    )
                }

                else -> StatusCard(
                    title = "尚未绑定老人",
                    description = "绑定需要通过邀请码双向确认。未绑定前无法查看任何风险信息。",
                    tone = StatusTone.Neutral,
                    statusLabel = "未绑定",
                    actionLabel = "查看关联关系",
                    onAction = onOpenRelation,
                )
            }
        }

        item {
            PrimaryButton(
                text = "绑定老人（F02）",
                onClick = onOpenBind,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            OutlinedButton(
                onClick = onOpenConsent,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.primaryButtonHeight),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconSm),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "共享授权（F09 · 只读）",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SampleTag()
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = "家庭关系与授权均为本地模拟数据，未接入真实服务端",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        item {
            PrimaryButton(
                text = "切换使用身份",
                onClick = onSwitchRole,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                text = "切换后会回到身份选择页，不会保留家属端之前的页面。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "F12 个人中心 · 未绑定", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 900)
@Composable
private fun FamilyProfileScreenPreview() {
    SilverShieldTheme {
        FamilyProfileScreen(
            uiState = FamilyUiState(loading = false),
            onOpenBind = {},
            onOpenRelation = {},
            onOpenConsent = {},
            onSwitchRole = {},
        )
    }
}
