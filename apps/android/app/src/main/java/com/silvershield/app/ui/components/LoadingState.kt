package com.silvershield.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.silvershield.app.R
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * 加载状态：明确的加载反馈。
 *
 * 老人端不使用骨架屏（易被误认为内容已加载），统一使用"进度指示 + 文字说明"，
 * 并通过 liveRegion 让读屏用户获知当前正在等待。
 *
 * @param compact 紧凑模式用于卡片内或列表底部；默认整块居中用于整页加载
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.state_loading),
    compact: Boolean = false,
) {
    val tokens = SilverShieldTokens.colors
    val liveModifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }

    if (compact) {
        Row(
            modifier = liveModifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(Sizes.iconSm),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textSecondary,
            )
        }
    } else {
        Column(
            modifier = liveModifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(Sizes.iconLg),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.heightIn(min = Spacing.md))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "加载状态 · 整页", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun LoadingStatePreview() {
    SilverShieldTheme {
        LoadingState(message = "正在获取风险记录…")
    }
}

@Preview(name = "加载状态 · 紧凑", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun LoadingStateCompactPreview() {
    SilverShieldTheme {
        LoadingState(message = "正在加载更多…", compact = true)
    }
}

@Preview(name = "加载状态 · 字体放大 2.0x", fontScale = 2f, showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360)
@Composable
private fun LoadingStateLargeFontPreview() {
    SilverShieldTheme {
        LoadingState(message = "正在获取风险记录…")
    }
}
