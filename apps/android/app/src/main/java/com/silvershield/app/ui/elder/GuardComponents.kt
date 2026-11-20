package com.silvershield.app.ui.elder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.silvershield.app.guard.GuardState
import com.silvershield.app.guard.GuardStatus
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.components.toneColors
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * 守护模块通用组件。
 *
 * 统一复用 P02 Design Tokens：正文 18sp、主按钮 ≥56dp、触控区 ≥48dp，
 * 状态同时用文字、图标与颜色表达。
 */

/** 模拟守护标识，所有守护页面都必须能看到。 */
@Composable
fun GuardSimulationBadge(modifier: Modifier = Modifier) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.pill),
        color = tokens.accentLight,
    ) {
        Text(
            text = "模拟守护",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

/**
 * 守护状态卡片：首页与运行状态页共用。
 *
 * 使用 bodyLarge（18sp）展示说明，系统字体放大时可自然换行。
 */
@Composable
fun GuardStatusCard(
    status: GuardStatus,
    modifier: Modifier = Modifier,
) {
    val presentation = status.toPresentation()
    val toneColors = presentation.tone.toneColors()
    val tokens = SilverShieldTokens.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = toneColors.container,
        border = BorderStroke(Sizes.borderWidth, toneColors.accent.copy(alpha = 0.32f)),
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(Sizes.statusCardIconContainer)
                        .background(toneColors.accent.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = presentation.icon,
                        contentDescription = null,
                        tint = toneColors.accent,
                        modifier = Modifier.size(Sizes.iconMd),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前状态：${presentation.stateLabel}",
                        style = MaterialTheme.typography.labelLarge,
                        color = toneColors.accent,
                    )
                    Text(
                        text = presentation.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = toneColors.onContainer,
                    )
                }
                Spacer(Modifier.width(Spacing.xs))
                GuardSimulationBadge()
            }

            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = presentation.description,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.textSecondary,
            )

            if (status.state == GuardState.REQUESTING) {
                Spacer(Modifier.height(Spacing.md))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** 卡片式入口行：图标 + 标题 + 说明 + 箭头，整行可点击。 */
@Composable
fun GuardEntryCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pageCode: String? = null,
) {
    val tokens = SilverShieldTokens.colors

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = tokens.surfaceCard,
        border = BorderStroke(Sizes.borderWidth, tokens.border),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(Sizes.statusCardIconContainer)
                    .background(tokens.accentLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.iconMd),
                )
            }

            Spacer(Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary,
                    )
                    if (pageCode != null) {
                        Spacer(Modifier.width(Spacing.xs))
                        Surface(
                            shape = RoundedCornerShape(Radius.pill),
                            color = tokens.surfaceSubtle,
                        ) {
                            Text(
                                text = pageCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textTertiary,
                                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 1.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
            }

            Spacer(Modifier.width(Spacing.xs))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = tokens.textTertiary,
                modifier = Modifier.size(Sizes.iconSm),
            )
        }
    }
}

/** 带标题的设置分组卡片。 */
@Composable
fun GuardSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = SilverShieldTokens.colors

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary,
        )
        Spacer(Modifier.height(Spacing.xs))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.lg),
            color = tokens.surfaceCard,
            border = BorderStroke(Sizes.borderWidth, tokens.border),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.cardPadding, vertical = Spacing.xs),
                content = content,
            )
        }
    }
}

/** 键值信息行。 */
@Composable
fun GuardInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    showDivider: Boolean = true,
) {
    val tokens = SilverShieldTokens.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textTertiary,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor ?: tokens.textPrimary,
        )
    }
    if (showDivider) {
        HorizontalDivider(thickness = Sizes.borderWidth, color = tokens.divider)
    }
}

/** 开关设置行：整行高度 ≥64dp，开关可独立点击。 */
@Composable
fun GuardSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = SilverShieldTokens.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) tokens.textPrimary else tokens.disabledContent,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** 单选行：用于提醒音量偏好。 */
@Composable
fun GuardRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTargetMin)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.textPrimary,
        )
    }
}

/** 停止守护按钮：首页与运行状态页共用，始终清晰可见。 */
@Composable
fun GuardStopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "停止守护",
) {
    val tokens = SilverShieldTokens.colors

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.primaryButtonHeight),
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(Sizes.borderWidth, tokens.danger),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.danger),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            modifier = Modifier.size(Sizes.iconSm),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 中性次要按钮（如「暂不同意」）。 */
@Composable
fun GuardSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.primaryButtonHeight),
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(Sizes.borderWidth, tokens.border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.textPrimary),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 场景按钮（开发测试面板专用）。 */
@Composable
fun GuardScenarioChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.touchTargetMin),
        shape = RoundedCornerShape(Radius.md),
        color = if (selected) tokens.accentLight else tokens.surfaceSubtle,
        border = BorderStroke(
            Sizes.borderWidth,
            if (selected) MaterialTheme.colorScheme.primary else tokens.border,
        ),
        enabled = enabled,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                !enabled -> tokens.disabledContent
                selected -> MaterialTheme.colorScheme.primary
                else -> tokens.textSecondary
            },
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

/** 状态色调转文字色，供隐私页等场景使用。 */
@Composable
fun toneAccent(tone: StatusTone): Color = tone.toneColors().accent
