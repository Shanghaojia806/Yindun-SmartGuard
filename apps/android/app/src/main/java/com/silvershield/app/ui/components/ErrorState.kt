package com.silvershield.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
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

/** 错误类型：决定默认图标与默认文案。 */
enum class ErrorKind(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    Load(R.string.error_load_title, R.string.error_load_description),
    Network(R.string.error_network_title, R.string.error_network_description),
    Permission(R.string.error_permission_title, R.string.error_permission_description),
    Unknown(R.string.error_unknown_title, R.string.error_unknown_description),
}

/**
 * 错误状态：加载失败、网络异常、权限不足等。
 *
 * 文案直接说明"发生了什么 + 该怎么办"，不使用"出错了"之类的含糊表达；
 * 重试按钮为可选，权限不足等无法通过重试解决的场景不应展示重试。
 *
 * @param detail 可选的技术细节（错误码），仅在需要排查时展示
 */
@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    kind: ErrorKind = ErrorKind.Load,
    title: String = stringResource(kind.titleRes),
    description: String = stringResource(kind.descriptionRes),
    detail: String? = null,
    retryLabel: String = stringResource(R.string.state_retry),
    onRetry: (() -> Unit)? = null,
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
            imageVector = kind.icon(),
            contentDescription = null,
            tint = tokens.danger,
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

        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.heightIn(min = Spacing.xs))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textTertiary,
                textAlign = TextAlign.Center,
            )
        }

        if (onRetry != null) {
            Spacer(Modifier.heightIn(min = Spacing.md))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(Radius.md),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm),
                modifier = Modifier.heightIn(min = Sizes.primaryButtonHeight),
            ) {
                Text(text = retryLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun ErrorKind.icon(): ImageVector = when (this) {
    ErrorKind.Permission -> Icons.Filled.Lock
    else -> Icons.Filled.Warning
}

@Preview(name = "错误状态 · 加载失败", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun ErrorStateLoadPreview() {
    SilverShieldTheme {
        ErrorState(onRetry = {})
    }
}

@Preview(name = "错误状态 · 权限不足与网络异常", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun ErrorStateVariantsPreview() {
    SilverShieldTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ErrorState(kind = ErrorKind.Permission)
            ErrorState(kind = ErrorKind.Network, onRetry = {})
        }
    }
}

@Preview(name = "错误状态 · 字体放大 2.0x", fontScale = 2f, showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun ErrorStateLargeFontPreview() {
    SilverShieldTheme {
        ErrorState(kind = ErrorKind.Network, onRetry = {})
    }
}
