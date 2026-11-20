package com.silvershield.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.R
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 空状态：无数据时使用。
 *
 * 除标题与说明外可提供**可选**操作入口（例如"去添加""重新加载"），
 * 不需要操作时不应传 actionLabel，避免出现无意义的装饰按钮。
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.state_empty_title),
    description: String = stringResource(R.string.state_empty_description),
    icon: ImageVector = Icons.AutoMirrored.Filled.List,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = SilverShieldTokens.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.textTertiary,
            modifier = Modifier.size(Sizes.iconXl),
        )
        Spacer(Modifier.heightIn(min = Spacing.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.heightIn(min = Spacing.xxs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textSecondary,
            textAlign = TextAlign.Center,
        )

        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.heightIn(min = Spacing.md))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(Radius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm),
                modifier = Modifier.heightIn(min = Sizes.primaryButtonHeight),
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Preview(name = "空状态 · 无操作", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun EmptyStatePreview() {
    SilverShieldTheme {
        EmptyState(title = "暂无风险记录", description = "开启守护后，识别到的风险事件会显示在这里。")
    }
}

@Preview(name = "空状态 · 带操作", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun EmptyStateWithActionPreview() {
    SilverShieldTheme {
        EmptyState(
            title = "还没有家庭成员",
            description = "邀请家人加入后，可以共同关注风险提醒。",
            actionLabel = "邀请家人",
            onAction = {},
        )
    }
}

@Preview(name = "空状态 · 字体放大 2.0x", fontScale = 2f, showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun EmptyStateLargeFontPreview() {
    SilverShieldTheme {
        EmptyState(
            title = "暂无风险记录",
            description = "开启守护后，识别到的风险事件会显示在这里。",
            actionLabel = "开启守护",
            onAction = {},
        )
    }
}
