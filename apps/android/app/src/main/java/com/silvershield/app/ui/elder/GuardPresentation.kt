package com.silvershield.app.ui.elder

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.silvershield.app.guard.GuardAudioState
import com.silvershield.app.guard.GuardState
import com.silvershield.app.guard.GuardStatus
import com.silvershield.app.guard.ScreenAuthorizationState
import com.silvershield.app.ui.components.StatusTone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 守护状态的展示模型。
 *
 * 状态文字、说明、图标与语义色统一在此映射，
 * 保证首页与运行状态页对同一状态给出一致表达。
 */
data class GuardStatePresentation(
    val stateLabel: String,
    val title: String,
    val description: String,
    val tone: StatusTone,
    val icon: ImageVector,
)

/** 将守护快照映射为展示模型。 */
fun GuardStatus.toPresentation(): GuardStatePresentation = when (state) {
    GuardState.OFF -> GuardStatePresentation(
        stateLabel = "未开启",
        title = "守护尚未开启",
        description = "开启后，手机会在本机识别短视频里的诈骗风险，并及时提醒您。原始画面不会上传。",
        tone = StatusTone.Neutral,
        icon = Icons.Filled.Lock,
    )

    GuardState.REQUESTING -> GuardStatePresentation(
        stateLabel = "授权中",
        title = "正在准备守护",
        description = "正在准备模拟守护，请稍候。本页不会弹出真实系统录屏授权窗口。",
        tone = StatusTone.Info,
        icon = Icons.Filled.Refresh,
    )

    GuardState.RUNNING -> GuardStatePresentation(
        stateLabel = "运行中",
        title = "守护正在运行",
        description = "当前使用模拟守护服务：不会读取、录制或上传您的真实屏幕内容。",
        tone = StatusTone.Safe,
        icon = Icons.Filled.CheckCircle,
    )

    GuardState.DEGRADED -> GuardStatePresentation(
        stateLabel = "降级运行",
        title = "守护正在降级运行",
        description = reason ?: "部分能力暂不可用，风险提醒仍会继续。",
        tone = StatusTone.Warning,
        icon = Icons.Filled.Warning,
    )

    GuardState.STOPPED -> GuardStatePresentation(
        stateLabel = "已停止",
        title = "守护已停止",
        description = "您已主动停止守护，手机不会再进行任何识别。需要时可以重新开启。",
        tone = StatusTone.Neutral,
        icon = Icons.Filled.Close,
    )

    GuardState.ERROR -> GuardStatePresentation(
        stateLabel = "异常",
        title = if (reason?.contains("授权") == true) "守护授权已失效" else "守护启动失败",
        description = reason ?: "守护发生异常，请重新尝试。",
        tone = StatusTone.Danger,
        icon = Icons.Filled.Warning,
    )
}

/** 音频状态文案。 */
fun GuardAudioState.displayLabel(): String = when (this) {
    GuardAudioState.AVAILABLE -> "可用"
    GuardAudioState.UNAVAILABLE -> "不可用（降级运行）"
    GuardAudioState.UNVERIFIED -> "未验证"
}

/** 屏幕采集授权状态文案。 */
fun ScreenAuthorizationState.displayLabel(): String = when (this) {
    ScreenAuthorizationState.NOT_GRANTED -> "未授权"
    ScreenAuthorizationState.GRANTED -> "已授权"
    ScreenAuthorizationState.REVOKED -> "已撤销"
}

private val guardTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss")

/** 格式化状态变化时间；null 表示本次进入应用后尚未变化。 */
fun formatGuardTimestamp(value: Long?): String = value?.let { millis ->
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(guardTimestampFormatter)
} ?: "本次进入后尚未变化"
