package com.silvershield.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * 占位页面统一骨架（P03）。
 *
 * 本轮只建立导航与角色体系，业务功能尚未实现，因此所有一级页面都用本组件统一呈现：
 * - 页面编号与标题：明确当前所在页面；
 * - 功能说明：告诉使用者这个页面将来做什么；
 * - 未实现说明：明确标注"当前为占位页"，避免被误认为已完成功能；
 * - 后续能力清单：为下一轮开发预留清晰的功能边界。
 *
 * @param pageCode 页面编号（如 E03 / F05），便于与需求文档对照
 * @param title 页面标题
 * @param description 页面功能说明
 * @param upcomingFeatures 后续轮次计划实现的能力，仅作说明，不代表已实现
 */
@Composable
fun PlaceholderPage(
    pageCode: String,
    title: String,
    description: String,
    upcomingFeatures: List<String>,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Spacing.pageHorizontal,
            end = Spacing.pageHorizontal,
            top = Spacing.pageVertical,
            bottom = Spacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            Column {
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = tokens.accentLight,
                ) {
                    Text(
                        text = "页面 $pageCode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    )
                }

                Spacer(Modifier.height(Spacing.sm))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )

                Spacer(Modifier.height(Spacing.xs))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.textSecondary,
                )
            }
        }

        item {
            StatusCard(
                title = "本页业务功能尚未实现",
                description = "P03 阶段仅建立角色与导航体系，页面内容为占位说明，不包含真实守护、风险记录或家庭协同能力。",
                tone = StatusTone.Neutral,
                statusLabel = "当前状态",
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.lg),
                color = tokens.surfaceCard,
                border = androidx.compose.foundation.BorderStroke(Sizes.borderWidth, tokens.border),
            ) {
                Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                    Text(
                        text = "后续将支持",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textPrimary,
                    )
                    Spacer(Modifier.height(Spacing.sm))

                    upcomingFeatures.forEach { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = Sizes.touchTargetMin),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = tokens.textTertiary,
                                modifier = Modifier.size(Sizes.iconXs),
                            )
                            Spacer(Modifier.size(Spacing.xs))
                            Text(
                                text = feature,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "占位页面 · 老人端", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 720)
@Composable
private fun PlaceholderPagePreview() {
    SilverShieldTheme {
        PlaceholderPage(
            pageCode = "E03",
            title = "守护首页",
            description = "开启守护后，本机将识别短视频中的诈骗风险，并及时提醒您。",
            upcomingFeatures = listOf("开始 / 停止守护", "守护状态查看", "风险提醒入口"),
        )
    }
}
