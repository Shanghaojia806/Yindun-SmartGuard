package com.silvershield.app.ui.elder

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.silvershield.app.guard.capture.CapturePhase
import com.silvershield.app.guard.capture.DEFAULT_CAPTURE_FPS
import com.silvershield.app.guard.capture.ScreenCaptureStatus
import com.silvershield.app.guard.capture.ScreenCaptureUiState
import com.silvershield.app.guard.capture.ScreenCaptureViewModel
import com.silvershield.app.guard.capture.TEST_CAPTURE_FPS
import com.silvershield.app.ui.components.PrimaryButton
import com.silvershield.app.ui.theme.Radius
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Sizes
import com.silvershield.app.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * P05 · 真实屏幕采集可行性验证页。
 *
 * 与 E05（模拟守护）严格区分：
 * - 本页所有"运行中"状态都来自真实 MediaProjection 会话；
 * - 页面常驻"真实采集 · PoC"标识，绝不使用"模拟守护"文案；
 * - 帧数据只在内存中统计，不落盘、不上传、不录像。
 */
@Composable
fun ScreenCapturePocScreen(
    viewModel: ScreenCaptureViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tokens = SilverShieldTokens.colors

    var notificationsGranted by remember { mutableStateOf(!viewModel.needsNotificationPermission()) }

    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            viewModel.onPermissionGranted(result.resultCode, data, notificationsGranted)
        } else {
            viewModel.onPermissionDenied()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
        // 即使拒绝通知权限也继续请求屏幕采集授权；拒绝通知不视为故障。
        projectionLauncher.launch(viewModel.createPermissionIntent())
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenDisposed() }
    }

    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            delay(4_000)
            viewModel.consumeMessage()
        }
    }

    val status = uiState.status
    val presentation = status.toCapturePresentation()
    val active = status.isActive

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "页面 P05 · 技术验证",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.padding(horizontal = Spacing.xxs))
                    RealCaptureBadge()
                }
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = "真实屏幕采集验证",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "本页调用 Android 官方 MediaProjection 接口，仅在您主动点击并同意系统授权后采集屏幕。" +
                        "采集帧只在内存中统计，不上传、不保存、不录像。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.textSecondary,
                )
            }
        }

        item {
            CaptureStatusCard(status = status, presentation = presentation)
        }

        item {
            GuardSection(title = "本地采集验证数据") {
                GuardInfoRow(
                    label = "原始帧（系统产生）",
                    value = buildString {
                        append("${status.stats.rawFrameCount} 帧")
                        status.stats.averageRawFps?.let { append(" · 平均 %.1f FPS".format(it)) }
                    },
                )
                GuardInfoRow(
                    label = "已处理帧（本地统计）",
                    value = buildString {
                        append("${status.stats.frameCount} 帧")
                        status.stats.averageProcessedFps?.let {
                            append(" · 平均 %.1f FPS".format(it))
                        }
                    },
                )
                GuardInfoRow(
                    label = "节流丢弃帧",
                    value = "${status.stats.droppedFrameCount} 帧（已即时释放缓冲区）",
                )
                GuardInfoRow(
                    label = "处理频率上限",
                    value = "${status.targetFps} FPS（UI 统计每秒最多刷新 1 次）",
                )
                GuardInfoRow(
                    label = "采集分辨率",
                    value = if (status.stats.widthPx > 0) {
                        "${status.stats.widthPx} × ${status.stats.heightPx} 像素"
                    } else {
                        "尚未开始采集"
                    },
                )
                GuardInfoRow(
                    label = "首帧时间",
                    value = formatGuardTimestamp(status.stats.firstFrameAtMillis),
                )
                GuardInfoRow(
                    label = "最新帧时间",
                    value = formatGuardTimestamp(status.stats.lastFrameAtMillis),
                )
                GuardInfoRow(
                    label = "最近两帧间隔",
                    value = status.stats.lastFrameIntervalMillis?.let { "$it 毫秒" } ?: "暂无",
                )
                GuardInfoRow(
                    label = "通知栏状态",
                    value = when {
                        status.phase == CapturePhase.IDLE ->
                            "开始采集后显示（采集期间常驻通知，可一键停止）"

                        status.notificationPermissionGranted ->
                            "已授予通知权限，采集期间显示常驻通知"

                        else ->
                            "未授予通知权限：可能看不到通知栏停止入口，请在本页停止"
                    },
                )
                GuardInfoRow(
                    label = "音频采集",
                    value = "本轮未实现（AudioPlaybackCapture 需播放音频的应用显式允许）",
                )
                GuardInfoRow(
                    label = "会话开始时间",
                    value = formatGuardTimestamp(status.startedAtMillis),
                    showDivider = false,
                )
            }
        }

        if (!active) {
            item {
                GuardSection(title = "处理频率（性能测试）") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FpsOption(
                            label = "5 FPS（推荐）",
                            selected = uiState.targetFps == DEFAULT_CAPTURE_FPS,
                            onClick = { viewModel.setTargetFps(DEFAULT_CAPTURE_FPS) },
                            modifier = Modifier.weight(1f),
                        )
                        FpsOption(
                            label = "10 FPS（测试）",
                            selected = uiState.targetFps == TEST_CAPTURE_FPS,
                            onClick = { viewModel.setTargetFps(TEST_CAPTURE_FPS) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "数值越低越省电、越不影响手机流畅度。系统原始帧率与业务处理帧率分开统计，" +
                            "被跳过的帧会立即释放，不会堆积在内存中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                    )
                }
            }
        }

        item {
            if (active) {
                GuardStopButton(
                    onClick = viewModel::stop,
                    text = "停止真实采集",
                )
            } else {
                PrimaryButton(
                    text = if (status.phase == CapturePhase.IDLE) {
                        "开始真实采集（系统授权）"
                    } else {
                        "重新开始真实采集"
                    },
                    onClick = {
                        if (!viewModel.beginPermissionRequest()) return@PrimaryButton
                        if (viewModel.needsNotificationPermission()) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationsGranted = true
                            projectionLauncher.launch(viewModel.createPermissionIntent())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                color = if (uiState.message != null) tokens.warningContainer else tokens.surfaceSubtle,
                border = BorderStroke(Sizes.borderWidth, tokens.border),
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(
                        text = uiState.message ?: "采集期间可随时返回本页停止，或使用通知栏中的「停止采集」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.message != null) tokens.onWarningContainer else tokens.textSecondary,
                    )
                }
            }
        }

        item {
            GuardSection(title = "系统限制与合规说明") {
                Text(
                    text = "· 每次采集都需要您在系统窗口中重新确认，不存在「一次授权永久后台录屏」。\n" +
                        "· 受 FLAG_SECURE 保护的画面（如部分支付、密码页面）系统会屏蔽，本轮不尝试绕过。\n" +
                        "· 锁屏、切换到其他应用时，采集是否继续由 Android 系统决定，本页会如实显示服务状态。\n" +
                        "· 采集帧不落盘、不上传、不发送给任何 AI 接口，验证后立即释放图像缓冲区。\n" +
                        "· E03 / E05 的模拟守护由 FakeGuardService 独立运行，与本页真实采集状态互不冒充。",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                )
            }
        }
    }
}

/** "真实采集"标识，与模拟守护徽标明确区分。 */
@Composable
private fun RealCaptureBadge(modifier: Modifier = Modifier) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.pill),
        color = tokens.safeContainer,
    ) {
        Text(
            text = "真实采集 · PoC",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.safe,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

/** 处理频率选项（5 / 10 FPS）。 */
@Composable
private fun FpsOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        color = if (selected) tokens.accentLight else tokens.surfaceSubtle,
        border = BorderStroke(
            Sizes.borderWidth,
            if (selected) MaterialTheme.colorScheme.primary else tokens.border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else tokens.textPrimary,
            )
        }
    }
}

private data class CapturePresentation(
    val stateLabel: String,
    val title: String,
    val description: String,
    val container: Color,
    val accent: Color,
)

@Composable
private fun ScreenCaptureStatus.toCapturePresentation(): CapturePresentation {
    val tokens = SilverShieldTokens.colors
    return when (phase) {
        CapturePhase.IDLE -> CapturePresentation(
            stateLabel = "未开始",
            title = "真实采集尚未开始",
            description = reason ?: "点击下方按钮后，Android 会弹出系统屏幕共享授权窗口，由您决定是否允许。",
            container = tokens.surfaceSubtle,
            accent = tokens.textSecondary,
        )

        CapturePhase.REQUESTING_PERMISSION -> CapturePresentation(
            stateLabel = "等待授权",
            title = "请在系统窗口中确认",
            description = "只有您在系统窗口点击允许后才会开始采集；拒绝不会影响其他功能。",
            container = tokens.warningContainer,
            accent = tokens.warning,
        )

        CapturePhase.STARTING_SERVICE -> CapturePresentation(
            stateLabel = "启动中",
            title = "正在创建采集会话",
            description = "正在启动前台服务并创建虚拟显示，请稍候。",
            container = tokens.warningContainer,
            accent = tokens.warning,
        )

        CapturePhase.CAPTURING -> CapturePresentation(
            stateLabel = "运行中",
            title = "真实屏幕采集运行中",
            description = "正在本机内存中接收并统计采集帧，不上传、不保存。可随时停止。",
            container = tokens.safeContainer,
            accent = tokens.safe,
        )

        CapturePhase.STOPPED -> CapturePresentation(
            stateLabel = "已停止",
            title = "真实采集已停止",
            description = reason ?: "采集已结束，虚拟显示、图像缓冲与前台服务均已释放。",
            container = tokens.neutralContainer,
            accent = tokens.neutral,
        )

        CapturePhase.ERROR -> CapturePresentation(
            stateLabel = "异常",
            title = "真实采集未启动或已中断",
            description = reason ?: "屏幕采集发生异常，请重新尝试。",
            container = tokens.dangerContainer,
            accent = tokens.danger,
        )
    }
}

@Composable
private fun CaptureStatusCard(
    status: ScreenCaptureStatus,
    presentation: CapturePresentation,
    modifier: Modifier = Modifier,
) {
    val tokens = SilverShieldTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = presentation.container,
        border = BorderStroke(Sizes.borderWidth, presentation.accent.copy(alpha = 0.32f)),
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "采集状态：${presentation.stateLabel}",
                        style = MaterialTheme.typography.labelLarge,
                        color = presentation.accent,
                    )
                    Text(
                        text = presentation.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = tokens.textPrimary,
                    )
                }
                RealCaptureBadge()
            }

            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = presentation.description,
                style = MaterialTheme.typography.bodyLarge,
                color = tokens.textSecondary,
            )

            if (status.phase == CapturePhase.REQUESTING_PERMISSION ||
                status.phase == CapturePhase.STARTING_SERVICE
            ) {
                Spacer(Modifier.height(Spacing.md))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
