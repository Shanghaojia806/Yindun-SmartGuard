package com.silvershield.app.guard

import kotlinx.coroutines.flow.StateFlow

/**
 * 守护服务接口。
 *
 * UI 与 ViewModel 只依赖本接口，不感知具体实现；
 * P04 唯一实现为 [FakeGuardService]，真实 MediaProjection 采集属于 P05。
 */
interface GuardService {

    /** 当前守护状态；所有页面共用该唯一数据源。 */
    val status: StateFlow<GuardStatus>

    /**
     * 请求开始守护。
     *
     * 先进入 [GuardState.REQUESTING]，模拟授权 / 启动完成后返回最终状态。
     * 返回值为本次启动后的快照，调用方据此判断是否启动成功。
     */
    suspend fun requestStart(): GuardStatus

    /** 停止守护，状态变为 [GuardState.STOPPED]。 */
    suspend fun stop()

    /** 授权被撤销：若守护正在运行，必须立即离开 RUNNING。 */
    suspend fun onAuthorizationRevoked()
}

/**
 * 模拟场景控制器。
 *
 * 仅 [FakeGuardService] 实现，用于 E05 开发测试面板切换不同 UI 状态。
 * 真实实现不应暴露该能力，页面在控制器为 null 时不展示测试面板。
 */
interface GuardScenarioController {
    val scenario: StateFlow<GuardScenario>

    suspend fun selectScenario(scenario: GuardScenario)
}

/**
 * FakeGuardService 可模拟的场景。
 *
 * 全部为开发测试用途，界面必须明确标注"模拟场景"。
 */
enum class GuardScenario(val label: String, val description: String) {
    NORMAL(
        label = "正常运行",
        description = "模拟守护正常运行，音频状态为未验证。",
    ),
    AUDIO_UNAVAILABLE(
        label = "音频不可用",
        description = "模拟系统限制导致无法获取播放音频，守护降级运行。",
    ),
    AUTHORIZATION_LOST(
        label = "授权失效",
        description = "模拟屏幕采集授权被撤销，守护进入异常状态。",
    ),
    START_FAILURE(
        label = "启动失败",
        description = "模拟守护服务启动失败。",
    ),
    USER_STOPPED(
        label = "用户停止",
        description = "模拟老人主动停止守护。",
    ),
}
