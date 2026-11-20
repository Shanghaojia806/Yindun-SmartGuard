package com.silvershield.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.silvershield.app.family.ConsentRecord
import com.silvershield.app.family.ConsentStatus
import com.silvershield.app.family.FamilyRelationStatus
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * 家庭关系与授权页面共用的基础展示组件。
 *
 * 所有状态同时提供图标与文字，不使用"只有颜色"的表达方式。
 */

/** 带边框的内容分区卡片。 */
@Composable
fun FamilySection(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.borderWidth, tokens.border),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
            }
            content()
        }
    }
}

/** 键值信息行；换行时值单独占一行，便于大字号阅读。 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.textPrimary,
        )
    }
}

/** 邀请码放大展示；数字与字母分组后仍保持可读。 */
@Composable
fun InviteCodeBox(
    code: String,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        color = tokens.accentLight,
        border = BorderStroke(Sizes.borderWidth, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = "邀请码",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.textSecondary,
            )
            Text(
                text = code,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "本地模拟邀请码，不是真实账号凭证",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textTertiary,
            )
        }
    }
}

/** 图标 + 文字的状态行，确保不依赖颜色也能识别状态。 */
@Composable
fun StatusInline(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val toneColors = tone.toneColors()
    val resolvedIcon = icon ?: when (tone) {
        StatusTone.Safe -> Icons.Filled.CheckCircle
        StatusTone.Warning -> Icons.Filled.Warning
        StatusTone.Danger -> Icons.Filled.Close
        StatusTone.Neutral -> Icons.Filled.Info
        StatusTone.Info -> Icons.Filled.Info
    }
    Row(
        modifier = modifier.semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = resolvedIcon,
            contentDescription = null,
            tint = toneColors.accent,
            modifier = Modifier.width(Sizes.iconSm),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = toneColors.accent,
        )
    }
}

/** 共用说明文字（隐私边界、模拟声明）。 */
@Composable
fun PrivacyNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = tokens.textTertiary,
            modifier = Modifier.width(Sizes.iconXs),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textTertiary,
        )
    }
}

/** 家庭关系状态 → 语义色。 */
fun FamilyRelationStatus.tone(): StatusTone = when (this) {
    FamilyRelationStatus.ACTIVE -> StatusTone.Safe
    FamilyRelationStatus.PENDING -> StatusTone.Info
    FamilyRelationStatus.REJECTED -> StatusTone.Warning
    FamilyRelationStatus.EXPIRED -> StatusTone.Neutral
    FamilyRelationStatus.REVOKED -> StatusTone.Danger
}

/**
 * 授权记录卡片。
 *
 * 老人端可带「撤销」操作；家属端只读，不传 actionLabel。
 */
@Composable
fun ConsentCard(
    record: ConsentRecord,
    status: ConsentStatus,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Sizes.borderWidth, tokens.border),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            StatusInline(text = "授权状态：${status.label}", tone = status.tone())
            Text(
                text = record.authorizationType.label,
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textPrimary,
            )
            Text(
                text = record.scopeDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary,
            )
            Text(
                text = "授权时间：${formatRiskTime(record.grantedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
            )
            Text(
                text = record.expiresAt?.let { "有效至：${formatRiskTime(it)}" } ?: "有效期限：长期有效（仍可撤销）",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
            )
            record.revokedAt?.let { revokedAt ->
                Text(
                    text = "撤销时间：${formatRiskTime(revokedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
            }
            record.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textTertiary,
                )
            }
            if (actionLabel != null && onAction != null) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.primaryButtonHeight),
                ) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 授权状态 → 语义色。 */
fun ConsentStatus.tone(): StatusTone = when (this) {
    ConsentStatus.ACTIVE -> StatusTone.Safe
    ConsentStatus.NOT_GRANTED -> StatusTone.Neutral
    ConsentStatus.EXPIRED -> StatusTone.Neutral
    ConsentStatus.REVOKED -> StatusTone.Danger
}
