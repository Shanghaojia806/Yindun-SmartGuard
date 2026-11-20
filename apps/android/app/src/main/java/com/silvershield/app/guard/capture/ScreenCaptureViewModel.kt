package com.silvershield.app.guard.capture

import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 真实采集验证页状态。
 *
 * 独立于 [com.silvershield.app.guard.GuardUiState]：
 * 本页展示的 RUNNING 只可能来自 MediaProjection 真实会话，
 * 不会与 FakeGuardService 的模拟状态混用。
 */
data class ScreenCaptureUiState(
    val status: ScreenCaptureStatus = ScreenCaptureStatus(),
    /** 业务处理频率上限（5 或 10 FPS）。 */
    val targetFps: Int = DEFAULT_CAPTURE_FPS,
    /** 一次性操作反馈。 */
    val message: String? = null,
)

/**
 * 真实屏幕采集验证页模型。
 *
 * 性能相关：
 * - 采集状态按阶段变化立即上屏，统计数字最多每 1 秒采样一次，
 *   避免每帧都触发 Compose 重组；
 * - 页面不可见时 Compose 停止收集，后台不产生 UI 刷新。
 */
class ScreenCaptureViewModel(
    private val controller: ScreenCaptureController,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)
    private val selectedFps = MutableStateFlow(DEFAULT_CAPTURE_FPS)

    /** 节流后的状态，避免 5 FPS 采集时每秒 5 次全量 UI 更新。 */
    private val sampledStatus = MutableStateFlow(ScreenCaptureStatus())
    private var lastStatsPushAtMillis = 0L

    init {
        viewModelScope.launch {
            controller.status.collect { incoming ->
                val previous = sampledStatus.value
                val now = SystemClock.elapsedRealtime()
                val phaseChanged = incoming.phase != previous.phase ||
                    incoming.reason != previous.reason ||
                    incoming.targetFps != previous.targetFps
                if (phaseChanged || now - lastStatsPushAtMillis >= STATS_UI_INTERVAL_MS) {
                    sampledStatus.value = incoming
                    lastStatsPushAtMillis = now
                }
            }
        }
    }

    val uiState: StateFlow<ScreenCaptureUiState> = combine(
        sampledStatus,
        selectedFps,
        message,
    ) { status, fps, message ->
        ScreenCaptureUiState(status = status, targetFps = fps, message = message)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScreenCaptureUiState(),
    )

    fun needsNotificationPermission(): Boolean = controller.needsNotificationPermission()

    /** 切换处理频率；仅在未采集时允许。 */
    fun setTargetFps(fps: Int) {
        if (fps != DEFAULT_CAPTURE_FPS && fps != TEST_CAPTURE_FPS) return
        if (sampledStatus.value.isActive) {
            message.value = "采集进行中，请先停止再调整处理频率"
            return
        }
        selectedFps.value = fps
    }

    /** 点击"开始真实采集"：先占用状态位，再弹系统窗口。 */
    fun beginPermissionRequest(): Boolean {
        val started = controller.beginPermissionRequest()
        if (!started) {
            message.value = "采集已在进行中，无需重复启动"
        }
        return started
    }

    fun createPermissionIntent(): Intent = controller.createPermissionIntent()

    fun onPermissionGranted(resultCode: Int, data: Intent, notificationGranted: Boolean) {
        controller.onPermissionGranted(
            resultCode = resultCode,
            data = data,
            notificationGranted = notificationGranted,
            targetFps = selectedFps.value,
        )
        if (!notificationGranted) {
            message.value = "未授予通知权限：采集仍会进行，但通知栏可能无法显示停止入口"
        }
    }

    fun onPermissionDenied() {
        controller.onPermissionDenied()
        message.value = "已取消系统授权，未开始采集"
    }

    fun stop() {
        controller.stop()
        message.value = "已停止采集，正在释放采集资源"
    }

    /** 页面销毁：避免"等待授权"状态悬挂导致无法再次启动。 */
    fun onScreenDisposed() {
        controller.onScreenDisposed()
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        private const val STATS_UI_INTERVAL_MS = 1_000L

        fun factory(controller: ScreenCaptureController): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ScreenCaptureViewModel(controller) }
            }
    }
}
