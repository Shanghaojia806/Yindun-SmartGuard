package com.silvershield.app.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.silvershield.app.R
import com.silvershield.app.ui.components.EmptyState
import com.silvershield.app.ui.components.ErrorKind
import com.silvershield.app.ui.components.ErrorState
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.components.RiskBadge
import com.silvershield.app.ui.components.RiskLevel
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * 设计系统组件预览页（仅开发构建可见）。
 *
 * 用途：集中检查 P02 建立的色彩、字体、间距与通用组件在各状态下的呈现，
 * 方便设计与开发在真机 / 模拟器上统一验收。
 *
 * 说明：本页不接入任何业务数据，示例文案均为占位内容，不代表真实识别结果。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignSystemGalleryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val tokens = SilverShieldTokens.colors

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.dev_gallery_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(Sizes.touchTargetMin)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.pageHorizontal,
                end = Spacing.pageHorizontal,
                top = Spacing.md,
                bottom = Spacing.xxxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(Radius.md),
                    color = tokens.accentLight,
                ) {
                    Text(
                        text = stringResource(R.string.dev_gallery_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }

            item { GallerySection(title = "品牌色板") { BrandPalette() } }
            item { GallerySection(title = "字体层级") { TypographyScale() } }
            item { GallerySection(title = "主要按钮状态") { ButtonStates() } }
            item { GallerySection(title = "状态卡片") { StatusCards() } }
            item { GallerySection(title = "风险标签") { RiskBadges() } }
            item { GallerySection(title = "空状态") { EmptySample() } }
            item { GallerySection(title = "错误状态") { ErrorSample() } }
            item { GallerySection(title = "加载状态") { LoadingSample() } }
        }
    }
}

// ── 分区容器 ──────────────────────────────────────────────────

@Composable
private fun GallerySection(
    title: String,
    content: @Composable () -> Unit,
) {
    val tokens = SilverShieldTokens.colors
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.divider, RoundedCornerShape(Radius.sm))
                .height(Sizes.borderWidth),
        )
        Spacer(Modifier.height(Spacing.md))
        content()
    }
}

// ── 品牌色板 ──────────────────────────────────────────────────

@Composable
private fun BrandPalette() {
    val tokens = SilverShieldTokens.colors
    val swatches = listOf(
        "品牌主色" to MaterialTheme.colorScheme.primary,
        "交互主色" to MaterialTheme.colorScheme.secondary,
        "安全状态" to tokens.safe,
        "警示状态" to tokens.warning,
        "危险状态" to tokens.danger,
        "中性状态" to tokens.neutral,
        "页面背景" to MaterialTheme.colorScheme.background,
        "卡片背景" to MaterialTheme.colorScheme.surface,
        "一级文字" to tokens.textPrimary,
        "二级文字" to tokens.textSecondary,
        "边框" to tokens.border,
        "分割线" to tokens.divider,
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        swatches.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                rowItems.forEach { (label, color) ->
                    ColorSwatch(label = label, color = color, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    Row(
        modifier = modifier
            .background(tokens.surfaceCard, RoundedCornerShape(Radius.sm))
            .border(Sizes.borderWidth, tokens.border, RoundedCornerShape(Radius.sm))
            .padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.iconLg)
                .background(color, CircleShape)
                .border(Sizes.borderWidth, tokens.border, CircleShape),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textPrimary,
        )
    }
}

// ── 字体层级 ──────────────────────────────────────────────────

@Composable
private fun TypographyScale() {
    val tokens = SilverShieldTokens.colors
    val samples = listOf(
        "页面主标题 · 28sp" to MaterialTheme.typography.headlineLarge,
        "区块标题 · 24sp" to MaterialTheme.typography.headlineSmall,
        "卡片标题 · 22sp" to MaterialTheme.typography.titleLarge,
        "正文 · 18sp" to MaterialTheme.typography.bodyLarge,
        "次要正文 · 16sp" to MaterialTheme.typography.bodyMedium,
        "辅助说明 · 15sp" to MaterialTheme.typography.bodySmall,
        "按钮文字 · 17sp" to MaterialTheme.typography.labelLarge,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        samples.forEach { (label, style) ->
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textTertiary,
                )
                Text(
                    text = "银盾智鉴 · 守护安心",
                    style = style,
                    color = tokens.textPrimary,
                )
            }
        }
    }
}

// ── 按钮状态 ──────────────────────────────────────────────────

@Composable
private fun ButtonStates() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PrimaryButton(text = "开始守护", onClick = {}, modifier = Modifier.fillMaxWidth())
        PrimaryButton(
            text = "重新检测",
            onClick = {},
            leadingIcon = Icons.Filled.Refresh,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            text = "正在启动守护",
            onClick = {},
            loading = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            text = "暂不可用",
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── 状态卡片 ──────────────────────────────────────────────────

@Composable
private fun StatusCards() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StatusCard(
            title = "守护运行中",
            description = "正在本机识别短视频内容，原始画面不会离开手机。",
            tone = StatusTone.Safe,
            statusLabel = "当前状态",
        )
        StatusCard(
            title = "识别能力受限",
            description = "当前设备性能不足，识别速度可能变慢，建议关闭其他应用。",
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

// ── 风险标签 ──────────────────────────────────────────────────

@Composable
private fun RiskBadges() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        RiskBadge(category = "冒充公检法", level = RiskLevel.High)
        RiskBadge(category = "虚假投资理财", level = RiskLevel.Medium)
        RiskBadge(category = "正常内容", level = RiskLevel.Low)
        RiskBadge(category = "待识别内容", level = RiskLevel.Unknown)
    }
}

// ── 空 / 错误 / 加载 ──────────────────────────────────────────

@Composable
private fun EmptySample() {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = SilverShieldTokens.colors.surfaceCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        EmptyState(
            title = "暂无风险记录",
            description = "开启守护后，识别到的风险事件会显示在这里。",
            actionLabel = "开启守护",
            onAction = {},
        )
    }
}

@Composable
private fun ErrorSample() {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = SilverShieldTokens.colors.surfaceCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ErrorState(kind = ErrorKind.Load, onRetry = {})
    }
}

@Composable
private fun LoadingSample() {
    Surface(
        shape = RoundedCornerShape(Radius.md),
        color = SilverShieldTokens.colors.surfaceCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            LoadingState(message = "正在获取风险记录…")
            LoadingState(message = "正在加载更多…", compact = true)
        }
    }
}

// ── 预览 ──────────────────────────────────────────────────────

@Preview(name = "设计系统预览 · 标准字号", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun DesignSystemGalleryPreview() {
    SilverShieldTheme {
        DesignSystemGalleryScreen()
    }
}

@Preview(name = "设计系统预览 · 字体放大 2.0x", fontScale = 2f, showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun DesignSystemGalleryLargeFontPreview() {
    SilverShieldTheme {
        DesignSystemGalleryScreen()
    }
}
