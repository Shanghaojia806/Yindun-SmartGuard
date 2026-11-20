package com.silvershield.app.ui.elder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.silvershield.app.family.FamilyRelationStatus
import com.silvershield.app.family.FamilyUiState
import com.silvershield.app.navigation.ElderTab
import com.silvershield.app.role.UserRole
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * E12 我的（占位）。
 *
 * 本轮可用：查看当前使用身份、切换身份。
 * 尚未实现：真实账号信息、家人共享管理、字号与语音设置、隐私与数据管理。
 */
@Composable
fun ElderProfileScreen(
    familyUiState: FamilyUiState,
    onOpenFamily: () -> Unit,
    onSwitchRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    val tab = ElderTab.Profile

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
                            imageVector = Icons.Filled.Person,
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
                            text = "当前身份：${UserRole.Elder.label}",
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
            val relation = familyUiState.relation
            when (relation?.relationStatus) {
                FamilyRelationStatus.ACTIVE -> StatusCard(
                    title = "已绑定家人",
                    description = "绑定后也不会自动共享风险信息；需要你单独授权后，家人才能看到授权范围内的内容。",
                    tone = StatusTone.Safe,
                    statusLabel = "已绑定",
                )

                FamilyRelationStatus.PENDING -> StatusCard(
                    title = "家庭邀请等待确认",
                    description = "对方确认后才会建立家庭关系，绑定本身仍然不会共享任何风险信息。",
                    tone = StatusTone.Info,
                    statusLabel = "等待确认",
                )

                else -> StatusCard(
                    title = "尚未绑定家人",
                    description = "你可以绑定家人，并单独决定是否共享风险信息。不绑定也不影响本机的风险提醒和记录。",
                    tone = StatusTone.Neutral,
                    statusLabel = "未绑定",
                )
            }
        }

        item {
            PrimaryButton(
                text = "家庭关系与共享授权",
                onClick = onOpenFamily,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                text = "家庭关系管理和风险共享授权分开设置，避免误把绑定当成共享。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
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
                text = "切换后会回到身份选择页，不会保留老人端之前的页面。",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "E12 我的占位", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 780)
@Composable
private fun ElderProfileScreenPreview() {
    SilverShieldTheme {
        ElderProfileScreen(
            familyUiState = FamilyUiState(loading = false),
            onOpenFamily = {},
            onSwitchRole = {},
        )
    }
}
