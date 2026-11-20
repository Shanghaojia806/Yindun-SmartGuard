package com.silvershield.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * 状态卡片：用于展示守护状态、系统状态与相关提示。
 *
 * 设计要点：
 * - 同时提供图标、状态文字与标题，不依赖单一颜色传达状态（色觉障碍友好）；
 * - 描述文字不设固定行高上限，系统字体放大时可自然换行；
 * - 可选操作区使用文字按钮，触控高度不低于 48dp。
 */
@Composable
fun StatusCard(
    title: String,
    description: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    statusLabel: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val toneColors = tone.toneColors()
    val resolvedIcon = icon ?: tone.defaultIcon()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append(statusLabel ?: title)
                    append("，")
                    append(title)
                    append("，")
                    append(description)
                }
            },
        shape = RoundedCornerShape(Radius.lg),
        color = toneColors.container,
        border = BorderStroke(Sizes.borderWidth, toneColors.accent.copy(alpha = 0.32f)),
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(Sizes.statusCardIconContainer)
                        .background(toneColors.accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = resolvedIcon,
                        contentDescription = null,
                        tint = toneColors.accent,
                        modifier = Modifier.size(Sizes.iconMd),
                    )
                }

                Spacer(Modifier.width(Spacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    if (!statusLabel.isNullOrBlank()) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = toneColors.accent,
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = toneColors.onContainer,
                    )
                    Spacer(Modifier.heightIn(min = Spacing.xxs))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.heightIn(min = Spacing.xs))
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Spacing.xs,
                    ),
                ) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun StatusTone.defaultIcon(): ImageVector = when (this) {
    StatusTone.Safe -> Icons.Filled.CheckCircle
    StatusTone.Warning -> Icons.Filled.Warning
    StatusTone.Danger -> Icons.Filled.Close
    StatusTone.Neutral -> Icons.Filled.Info
    StatusTone.Info -> Icons.Filled.Info
}

@Preview(name = "状态卡片 · 安全", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun StatusCardSafePreview() {
    SilverShieldTheme {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            StatusCard(
                title = "守护运行中",
                description = "正在本机识别短视频内容，原始画面不会离开手机。",
                tone = StatusTone.Safe,
                statusLabel = "当前状态",
            )
        }
    }
}

@Preview(name = "状态卡片 · 警示与危险", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun StatusCardRiskPreview() {
    SilverShieldTheme {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            StatusCard(
                title = "识别能力受限",
                description = "当前设备性能不足，风险识别速度可能变慢，建议关闭其他应用。",
                tone = StatusTone.Warning,
                statusLabel = "降级运行",
            )
            StatusCard(
                title = "发现高风险内容",
                description = "该视频疑似冒充公检法诈骗，请立即停止操作并联系家人。",
                tone = StatusTone.Danger,
                statusLabel = "高风险提醒",
                actionLabel = "查看详情",
                onAction = {},
            )
        }
    }
}

@Preview(name = "状态卡片 · 字体放大 2.0x", fontScale = 2f, showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun StatusCardLargeFontPreview() {
    SilverShieldTheme {
        Column(modifier = Modifier.padding(Spacing.md)) {
            StatusCard(
                title = "守护已停止",
                description = "点击下方按钮可重新开启守护，开启后需要允许屏幕共享。",
                tone = StatusTone.Neutral,
                statusLabel = "已停止",
            )
        }
    }
}
