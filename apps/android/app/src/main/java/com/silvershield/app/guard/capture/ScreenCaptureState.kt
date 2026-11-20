package com.silvershield.app.guard.capture

/** 默认业务处理频率（帧/秒）。 */
const val DEFAULT_CAPTURE_FPS: Int = 5

/** 预留的高频测试选项（帧/秒），仅用于性能对比。 */
const val TEST_CAPTURE_FPS: Int = 10

/** 真实采集阶段（P05 PoC）。 */
enum class CapturePhase {
    /** 尚未开始采集。 */
    IDLE,

    /** 已点击开始，等待 Android 系统授权窗口返回结果。 */
    REQUESTING_PERMISSION,

    /** 系统已授权，正在启动前台服务并创建虚拟显示。 */
    STARTING_SERVICE,

    /** 虚拟显示已创建，正在本地接收采集帧。 */
    CAPTURING,

    /** 已停止（用户主动、通知栏或系统撤销）。 */
    STOPPED,

    /** 启动或运行异常。 */
    ERROR,
    ;

    /** 是否处于"尚未结束"的采集流程中。 */
    val isActive: Boolean
        get() = this == REQUESTING_PERMISSION || this == STARTING_SERVICE || this == CAPTURING
}

/**
 * 引擎交给状态层的一次采样结果。
 *
 * 原始帧计数与处理帧计数分开上报：
 * - [rawFrameCount] 是系统真正产生并回调的帧数（受屏幕刷新与 VirtualDisplay 刷新率影响）；
 * - 处理帧由节流策略决定，默认 5 FPS。
 */
data class CaptureFrameSnapshot(
    val widthPx: Int,
    val heightPx: Int,
    val timestampMillis: Long,
    val rawFrameCount: Long,
    val rawFirstFrameAtMillis: Long,
    val rawLastFrameAtMillis: Long,
)

/**
 * 本地采集帧统计。
 *
 * 仅在内存中记录尺寸、时间戳与数量，不保存任何图像数据。
 */
data class CaptureFrameStats(
    /** 已按节流策略处理的帧数。 */
    val frameCount: Long = 0L,
    /** 系统原始回调帧数（未节流前）。 */
    val rawFrameCount: Long = 0L,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val firstFrameAtMillis: Long? = null,
    val lastFrameAtMillis: Long? = null,
    val previousFrameAtMillis: Long? = null,
    val rawFirstFrameAtMillis: Long? = null,
    val rawLastFrameAtMillis: Long? = null,
) {
    /** 被节流跳过的帧数（已即时 close 释放）。 */
    val droppedFrameCount: Long
        get() = (rawFrameCount - frameCount).coerceAtLeast(0L)

    /** 最近两帧（处理帧）的间隔；不足两帧时为 null。 */
    val lastFrameIntervalMillis: Long?
        get() {
            val last = lastFrameAtMillis ?: return null
            val previous = previousFrameAtMillis ?: return null
            return last - previous
        }

    /** 处理帧平均频率。 */
    val averageProcessedFps: Float?
        get() = averageFps(frameCount, firstFrameAtMillis, lastFrameAtMillis)

    /** 系统原始帧平均频率。 */
    val averageRawFps: Float?
        get() = averageFps(rawFrameCount, rawFirstFrameAtMillis, rawLastFrameAtMillis)

    /** 记录一次已处理的帧，并同步原始帧计数。 */
    fun recordFrame(snapshot: CaptureFrameSnapshot): CaptureFrameStats = copy(
        frameCount = frameCount + 1,
        rawFrameCount = snapshot.rawFrameCount,
        widthPx = snapshot.widthPx,
        heightPx = snapshot.heightPx,
        firstFrameAtMillis = firstFrameAtMillis ?: snapshot.timestampMillis,
        previousFrameAtMillis = lastFrameAtMillis,
        lastFrameAtMillis = snapshot.timestampMillis,
        rawFirstFrameAtMillis = snapshot.rawFirstFrameAtMillis,
        rawLastFrameAtMillis = snapshot.rawLastFrameAtMillis,
    )

    private fun averageFps(count: Long, firstAt: Long?, lastAt: Long?): Float? {
        if (count < 2 || firstAt == null || lastAt == null) return null
        val elapsed = lastAt - firstAt
        if (elapsed <= 0L) return null
        return (count - 1) * 1_000f / elapsed
    }
}

/**
 * 一次真实采集会话的快照。
 *
 * @param phase 当前阶段
 * @param stats 本地帧统计（原始帧 / 处理帧 / 丢弃帧分开）
 * @param targetFps 业务处理频率上限
 * @param reason 停止或异常原因；正常采集中为 null
 * @param startedAtMillis 本次会话进入 [CapturePhase.CAPTURING] 的时间
 * @param lastChangedAtMillis 最近一次阶段变化时间
 * @param notificationPermissionGranted 是否获得通知权限；未获得时前台服务通知可能不显示
 */
data class ScreenCaptureStatus(
    val phase: CapturePhase = CapturePhase.IDLE,
    val stats: CaptureFrameStats = CaptureFrameStats(),
    val targetFps: Int = DEFAULT_CAPTURE_FPS,
    val reason: String? = null,
    val startedAtMillis: Long? = null,
    val lastChangedAtMillis: Long? = null,
    val notificationPermissionGranted: Boolean = true,
) {
    val isActive: Boolean
        get() = phase.isActive
}
