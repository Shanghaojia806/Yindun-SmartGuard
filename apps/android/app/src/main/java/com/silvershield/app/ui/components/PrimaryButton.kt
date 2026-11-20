package com.silvershield.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.silvershield.app.ui.theme.Elevation
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * 统一主要操作按钮。
 *
 * 适老化设计：
 * - 最小高度 56dp（V1.3 建议 56–64dp），触控区域远超 48dp 下限；
 * - 使用文字标签而非纯图标；
 * - 加载态保留文字并显示进度指示，避免老人误以为"没反应"；
 * - 高度采用 heightIn 而非固定 height，系统字体放大时文字不被裁切。
 *
 * @param loading 加载中：显示进度指示并阻断重复点击
 * @param enabled 禁用态：置灰且不可点击
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val tokens = SilverShieldTokens.colors
    val interactionEnabled = enabled && !loading

    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = Sizes.primaryButtonHeight)
            .then(
                if (loading) {
                    Modifier.semantics {
                        stateDescription = "正在处理，请稍候"
                        contentDescription = text
                    }
                } else {
                    Modifier
                }
            ),
        enabled = interactionEnabled,
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = if (loading) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
            } else {
                tokens.disabledContainer
            },
            disabledContentColor = if (loading) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                tokens.disabledContent
            },
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = Elevation.none,
            pressedElevation = Elevation.none,
            disabledElevation = Elevation.none,
            hoveredElevation = Elevation.none,
        ),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Sizes.iconSm),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
                Spacer(Modifier.width(Spacing.sm))
            } else if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconSm),
                )
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "主要按钮 · 正常", showBackground = true, backgroundColor = 0xFFF4F7FA)
@Composable
private fun PrimaryButtonPreview() {
    SilverShieldTheme {
        PrimaryButton(text = "开始守护", onClick = {})
    }
}

@Preview(name = "主要按钮 · 带图标", showBackground = true, backgroundColor = 0xFFF4F7FA)
@Composable
private fun PrimaryButtonWithIconPreview() {
    SilverShieldTheme {
        PrimaryButton(text = "重新检测", onClick = {}, leadingIcon = Icons.Filled.Refresh)
    }
}

@Preview(name = "主要按钮 · 加载中", showBackground = true, backgroundColor = 0xFFF4F7FA)
@Composable
private fun PrimaryButtonLoadingPreview() {
    SilverShieldTheme {
        PrimaryButton(text = "正在启动守护", onClick = {}, loading = true)
    }
}

@Preview(name = "主要按钮 · 禁用", showBackground = true, backgroundColor = 0xFFF4F7FA)
@Composable
private fun PrimaryButtonDisabledPreview() {
    SilverShieldTheme {
        PrimaryButton(text = "暂不可用", onClick = {}, enabled = false)
    }
}

@Preview(name = "主要按钮 · 字体放大 2.0x", fontScale = 2f, showBackground = true, backgroundColor = 0xFFF4F7FA)
@Composable
private fun PrimaryButtonLargeFontPreview() {
    SilverShieldTheme {
        PrimaryButton(text = "开始守护", onClick = {})
    }
}
