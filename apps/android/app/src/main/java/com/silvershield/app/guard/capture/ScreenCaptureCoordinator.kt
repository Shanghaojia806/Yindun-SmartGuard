package com.silvershield.app.guard.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 真实采集会话状态持有者。
 *
 * 为什么单独维护：
 * - MediaProjection 授权令牌来自 Activity 的授权结果，无法套用
 *   [com.silvershield.app.guard.GuardService.requestStart] 的无参挂起契约；
 * - 采集状态需要跨 Activity / 前台服务共享，因此放在进程级单例 [ScreenCaptureSession]；
 * - 与 FakeGuardService 的 GuardStatus 完全隔离，杜绝"模拟 RUNNING 冒充真实采集"。
 *
 * 所有方法均通过 [Synchronized] 串行化，可从主线程与采集线程安全调用。
 */
class ScreenCaptureStateHolder(
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _status = MutableStateFlow(ScreenCaptureStatus())
    val status: StateFlow<ScreenCaptureStatus> = _status.asStateFlow()

    /**
     * 尝试进入"等待系统授权"阶段。
     *
     * @return true 表示可以弹出系统授权窗口；false 表示已有会话在进行，需忽略本次重复点击。
     */
    @Synchronized
    fun beginPermissionRequest(): Boolean {
        if (_status.value.isActive) return false
        _status.value = ScreenCaptureStatus(
            phase = CapturePhase.REQUESTING_PERMISSION,
            notificationPermissionGranted = _status.value.notificationPermissionGranted,
            lastChangedAtMillis = clock(),
        )
        return true
    }

    /** 系统授权通过，前台服务即将启动。 */
    @Synchronized
    fun onPermissionGranted(
        notificationPermissionGranted: Boolean,
        targetFps: Int = DEFAULT_CAPTURE_FPS,
    ) {
        _status.value = ScreenCaptureStatus(
            phase = CapturePhase.STARTING_SERVICE,
            targetFps = targetFps,
            notificationPermissionGranted = notificationPermissionGranted,
            lastChangedAtMillis = clock(),
        )
    }

    /**
     * 用户取消系统授权。
     *
     * 明确回到 [CapturePhase.IDLE] 而非 ERROR：拒绝授权是正常选择，不是系统故障。
     */
    @Synchronized
    fun onPermissionDenied(
        reason: String = "用户取消了系统屏幕采集授权，未开始采集。",
    ) {
        _status.value = ScreenCaptureStatus(
            phase = CapturePhase.IDLE,
            reason = reason,
            targetFps = _status.value.targetFps,
            notificationPermissionGranted = _status.value.notificationPermissionGranted,
            lastChangedAtMillis = clock(),
        )
    }

    /** 虚拟显示创建成功，进入真实采集。保留启动过程中可能已到达的帧统计。 */
    @Synchronized
    fun onCapturing(widthPx: Int, heightPx: Int) {
        val now = clock()
        val current = _status.value
        _status.value = current.copy(
            phase = CapturePhase.CAPTURING,
            stats = current.stats.copy(widthPx = widthPx, heightPx = heightPx),
            reason = null,
            startedAtMillis = now,
            lastChangedAtMillis = now,
        )
    }

    /** 记录一帧已处理帧；仅采集流程中生效，避免停止后仍被写入。 */
    @Synchronized
    fun onFrame(snapshot: CaptureFrameSnapshot) {
        val current = _status.value
        if (current.phase != CapturePhase.CAPTURING &&
            current.phase != CapturePhase.STARTING_SERVICE
        ) {
            return
        }
        _status.value = current.copy(stats = current.stats.recordFrame(snapshot))
    }

    /** 正常停止（用户主动、通知栏或服务结束）。 */
    @Synchronized
    fun onStopped(reason: String? = null) {
        _status.value = _status.value.copy(
            phase = CapturePhase.STOPPED,
            reason = reason,
            lastChangedAtMillis = clock(),
        )
    }

    /** 启动或运行异常。 */
    @Synchronized
    fun onError(reason: String) {
        _status.value = _status.value.copy(
            phase = CapturePhase.ERROR,
            reason = reason,
            lastChangedAtMillis = clock(),
        )
    }

    /**
     * 页面销毁时清理"等待授权"悬挂状态。
     *
     * 只处理仍停留在 [CapturePhase.REQUESTING_PERMISSION] 的情况；
     * 若系统随后仍返回授权结果，[onPermissionGranted] 会正常继续启动。
     */
    @Synchronized
    fun resetPendingRequest() {
        if (_status.value.phase == CapturePhase.REQUESTING_PERMISSION) {
            _status.value = ScreenCaptureStatus(
                phase = CapturePhase.IDLE,
                notificationPermissionGranted = _status.value.notificationPermissionGranted,
                lastChangedAtMillis = clock(),
            )
        }
    }
}

/** 进程级真实采集会话状态。前台服务与验证页面共享同一份。 */
val ScreenCaptureSession: ScreenCaptureStateHolder = ScreenCaptureStateHolder()
