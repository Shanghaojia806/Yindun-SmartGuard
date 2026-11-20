package com.silvershield.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.silvershield.app.R
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.components.StatusCard
import com.silvershield.app.ui.components.StatusTone
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/**
 * E01 首次引导页。
 *
 * 内容范围（本轮）：
 * - 产品价值说明；
 * - 隐私与数据说明（本机处理、不远程开启采集）；
 * - 进入角色选择。
 *
 * 本页不申请任何系统权限，也不开启屏幕采集。
 */
@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGallery: (() -> Unit)? = null,
) {
    val tokens = SilverShieldTokens.colors

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.pageHorizontal, vertical = Spacing.pageVertical),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(Radius.xl)),
            )

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = tokens.textPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xs))

            Text(
                text = "帮您看清短视频里的诈骗风险",
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xl))

            StatusCard(
                title = "识别在手机本机完成",
                description = "短视频画面只在您的手机上分析，原始画面不会上传到服务器，家人也无法查看您的屏幕内容。",
                tone = StatusTone.Info,
                statusLabel = "隐私说明",
            )

            Spacer(Modifier.height(Spacing.cardGap))

            StatusCard(
                title = "是否开启守护由您决定",
                description = "只有您本人可以在本机开启或停止守护，家人和管理人员都无法远程开启您的屏幕采集。",
                tone = StatusTone.Neutral,
                statusLabel = "授权说明",
            )

            Spacer(Modifier.height(Spacing.cardGap))

            StatusCard(
                title = "风险提醒会说明判断依据",
                description = "遇到疑似诈骗内容时，我们会给出线索与建议；证据不足时不会宣称内容安全。",
                tone = StatusTone.Safe,
                statusLabel = "提醒方式",
            )

            Spacer(Modifier.height(Spacing.xxl))

            PrimaryButton(
                text = "开始使用",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = "下一步选择您的使用身份",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textTertiary,
                textAlign = TextAlign.Center,
            )

            if (onOpenGallery != null) {
                Spacer(Modifier.height(Spacing.md))
                TextButton(
                    onClick = onOpenGallery,
                    modifier = Modifier.heightIn(min = Sizes.touchTargetMin),
                ) {
                    Text(
                        text = stringResource(R.string.dev_gallery_entry),
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textTertiary,
                    )
                }
            }
        }
    }
}

@Preview(name = "E01 首次引导", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun OnboardingScreenPreview() {
    SilverShieldTheme {
        OnboardingScreen(onContinue = {})
    }
}

@Preview(name = "E01 首次引导 · 字体放大 1.8x", fontScale = 1.8f, showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun OnboardingScreenLargeFontPreview() {
    SilverShieldTheme {
        OnboardingScreen(onContinue = {})
    }
}
