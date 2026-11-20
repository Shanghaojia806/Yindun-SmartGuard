package com.silvershield.app.ui.elder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTheme
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing

/** E04 授权说明中的一条说明。 */
private data class AuthorizationItem(val title: String, val content: String)

private val authorizationItems = listOf(
    AuthorizationItem(
        title = "为什么需要屏幕采集授权",
        content = "守护需要看到您正在浏览的短视频画面，才能识别其中的诈骗话术与可疑场景。",
    ),
    AuthorizationItem(
        title = "采集的内容范围",
        content = "仅用于识别短视频画面中的文字与场景线索，不采集聊天记录、支付信息或通讯录。",
    ),
    AuthorizationItem(
        title = "数据如何在手机本地处理",
        content = "画面只在您的手机本地分析，识别过程不依赖云端，也不调用商业识别接口。",
    ),
    AuthorizationItem(
        title = "默认不上传原始屏幕录像",
        content = "默认不上传、不保存原始屏幕录像。只有经您确认的风险事件摘要，才可能用于提醒。",
    ),
    AuthorizationItem(
        title = "家人不会自动看到实时屏幕",
        content = "只有在您主动共享风险事件后，家人才能看到事件摘要与处理状态。",
    ),
    AuthorizationItem(
        title = "您可以随时停止和撤销",
        content = "首页与「隐私与授权」页面都提供停止和撤销入口，撤销后立即生效。",
    ),
    AuthorizationItem(
        title = "部分第三方应用可能限制采集",
        content = "有些应用禁止屏幕内容被读取，此时守护可能无法识别，或只能降级运行。",
    ),
    AuthorizationItem(
        title = "音频可能因系统限制无法获取",
        content = "系统可能限制音频采集，此时守护会以「降级运行」状态明确提示您。",
    ),
)

/**
 * E04 授权说明。
 *
 * 本轮为**模拟授权说明流程**：
 * - 不调用 Android 系统 MediaProjection；
 * - 不申请无障碍权限；
 * - 点击「同意」仅记录本地模拟授权，用于 P04 界面验收。
 *
 * @param autoStart 同意后是否直接启动守护（从 E14 进入时为 false，仅完成授权）
 */
@Composable
fun ElderAuthorizationScreen(
    autoStart: Boolean,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Spacing.pageHorizontal,
            end = Spacing.pageHorizontal,
            top = Spacing.pageVertical,
            bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            Column {
                Text(
                    text = "页面 E04",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = "开启守护前，请您了解以下事项",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "这些说明会帮助您决定是否开启守护。您随时可以回来查看。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.textSecondary,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                color = tokens.warningContainer,
                border = BorderStroke(Sizes.borderWidth, tokens.warning.copy(alpha = 0.4f)),
            ) {
                Text(
                    text = "本页是模拟授权说明流程：点击同意不会弹出 Android 系统录屏授权窗口，" +
                        "也不会开始真实屏幕采集或申请无障碍权限。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.onWarningContainer,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.lg),
                color = tokens.surfaceCard,
                border = BorderStroke(Sizes.borderWidth, tokens.border),
            ) {
                Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                    authorizationItems.forEachIndexed { index, item ->
                        if (index > 0) {
                            Spacer(Modifier.height(Spacing.md))
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .size(Sizes.iconLg)
                                    .background(tokens.accentLight, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.width(Spacing.sm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = tokens.textPrimary,
                                )
                                Spacer(Modifier.height(Spacing.xxs))
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = tokens.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            PrimaryButton(
                text = if (autoStart) "同意并继续" else "同意授权",
                onClick = onAgree,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            GuardSecondaryButton(
                text = "暂不同意",
                onClick = onDecline,
            )
        }

        item {
            Text(
                text = if (autoStart) {
                    "同意后将进入模拟守护流程，不会读取或上传真实屏幕内容。"
                } else {
                    "同意后仅记录模拟授权状态，不会自动开始守护。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textTertiary,
            )
        }
    }
}

@Preview(name = "E04 授权说明", showBackground = true, backgroundColor = 0xFFF4F7FA, widthDp = 360, heightDp = 1100)
@Composable
private fun ElderAuthorizationScreenPreview() {
    SilverShieldTheme {
        ElderAuthorizationScreen(
            autoStart = true,
            onAgree = {},
            onDecline = {},
        )
    }
}
