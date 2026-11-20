package com.silvershield.app.guard

/**
 * 守护状态（V1.3 提示词 04 定义的六态）。
 *
 * P04 仅由 [FakeGuardService] 驱动，用于演示界面状态；
 * 不代表真实屏幕采集、真实识别或真实服务端授权已启用。
 */
enum class GuardState {
    /** 尚未开启守护。 */
    OFF,

    /** 正在请求授权 / 模拟启动中。 */
    REQUESTING,

    /** 守护正常运行（P04 为模拟运行）。 */
    RUNNING,

    /** 降级运行，例如系统限制导致音频不可用。 */
    DEGRADED,

    /** 用户主动停止守护。 */
    STOPPED,

    /** 启动失败、运行异常或授权失效。 */
    ERROR,
    ;

    /** 是否需要展示"停止守护"主入口。 */
    val isActive: Boolean
        get() = this == REQUESTING || this == RUNNING || this == DEGRADED
}

/**
 * 音频能力状态。
 *
 * P04 尚未接入真实音频采集，默认 [UNVERIFIED]；
 * 只有模拟场景才会出现 [AVAILABLE] / [UNAVAILABLE]。
 */
enum class GuardAudioState {
    AVAILABLE,
    UNAVAILABLE,
    UNVERIFIED,
}

/**
 * 一次守护运行快照。
 *
 * 由 [GuardService] 统一产出，页面不得各自维护独立状态。
 *
 * @param state 当前守护状态
 * @param audioState 当前音频能力
 * @param lastChangedAt 最近一次状态变化时间（毫秒时间戳），null 表示尚未变化
 * @param reason 异常或降级原因；正常状态下为 null
 */
data class GuardStatus(
    val state: GuardState = GuardState.OFF,
    val audioState: GuardAudioState = GuardAudioState.UNVERIFIED,
    val lastChangedAt: Long? = null,
    val reason: String? = null,
)
