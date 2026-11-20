package com.silvershield.app.guard

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 模拟守护服务（P04 唯一实现）。
 *
 * 重要说明：
 * - **不采集屏幕、不录制音频、不执行 OCR / AI 推理**；
 * - 仅通过状态机模拟不同守护状态，供界面联调与验收；
 * - 界面必须显著标注"模拟守护"，不得描述为真实屏幕监控已启动。
 *
 * @param requestDelayMillis 模拟授权 / 启动耗时，单元测试可传入较小值
 * @param clock 当前时间来源，便于测试替换
 */
class FakeGuardService(
    private val requestDelayMillis: Long = 900L,
    private val clock: () -> Long = System::currentTimeMillis,
) : GuardService, GuardScenarioController {

    private val _status = MutableStateFlow(GuardStatus())
    override val status: StateFlow<GuardStatus> = _status.asStateFlow()

    private val _scenario = MutableStateFlow(GuardScenario.NORMAL)
    override val scenario: StateFlow<GuardScenario> = _scenario.asStateFlow()

    override suspend fun requestStart(): GuardStatus {
        // 上一次若停留在"用户停止"场景，重新开启时先恢复为正常运行场景。
        if (_scenario.value == GuardScenario.USER_STOPPED) {
            _scenario.value = GuardScenario.NORMAL
        }

        _status.value = GuardStatus(
            state = GuardState.REQUESTING,
            audioState = GuardAudioState.UNVERIFIED,
            lastChangedAt = clock(),
            reason = null,
        )

        delay(requestDelayMillis)

        // 请求期间用户可能已点击停止：此时不再覆盖停止结果。
        if (_status.value.state != GuardState.REQUESTING) {
            return _status.value
        }

        val next = when (_scenario.value) {
            GuardScenario.NORMAL -> GuardStatus(
                state = GuardState.RUNNING,
                audioState = GuardAudioState.UNVERIFIED,
                lastChangedAt = clock(),
                reason = null,
            )

            GuardScenario.AUDIO_UNAVAILABLE -> GuardStatus(
                state = GuardState.DEGRADED,
                audioState = GuardAudioState.UNAVAILABLE,
                lastChangedAt = clock(),
                reason = "音频不可用：系统限制导致无法获取正在播放的声音（模拟场景）。",
            )

            GuardScenario.AUTHORIZATION_LOST -> GuardStatus(
                state = GuardState.ERROR,
                audioState = GuardAudioState.UNVERIFIED,
                lastChangedAt = clock(),
                reason = "屏幕采集授权已失效，请重新授权（模拟场景）。",
            )

            GuardScenario.START_FAILURE -> GuardStatus(
                state = GuardState.ERROR,
                audioState = GuardAudioState.UNVERIFIED,
                lastChangedAt = clock(),
                reason = "模拟启动失败：守护服务未能启动，请稍后重试。",
            )

            GuardScenario.USER_STOPPED -> GuardStatus(
                state = GuardState.STOPPED,
                audioState = GuardAudioState.UNVERIFIED,
                lastChangedAt = clock(),
                reason = null,
            )
        }

        _status.value = next
        return next
    }

    override suspend fun stop() {
        _scenario.value = GuardScenario.USER_STOPPED
        _status.value = GuardStatus(
            state = GuardState.STOPPED,
            audioState = GuardAudioState.UNVERIFIED,
            lastChangedAt = clock(),
            reason = null,
        )
    }

    override suspend fun onAuthorizationRevoked() {
        // 只有正在守护或请求中才需要立刻中断；未开启时保持原状态。
        if (_status.value.state.isActive) {
            _scenario.value = GuardScenario.AUTHORIZATION_LOST
            _status.value = GuardStatus(
                state = GuardState.ERROR,
                audioState = GuardAudioState.UNVERIFIED,
                lastChangedAt = clock(),
                reason = "屏幕采集授权已被撤销，守护已停止（模拟场景）。",
            )
        }
    }

    override suspend fun selectScenario(scenario: GuardScenario) {
        _scenario.value = scenario
        val now = clock()
        _status.value = when (scenario) {
            GuardScenario.NORMAL -> GuardStatus(
                state = GuardState.RUNNING,
                audioState = GuardAudioState.UNVERIFIED,
                lastChangedAt = now,
                reason = null,
            )

            GuardScenario.AUDIO_UNAVAILABLE -> GuardStatus(
                state = GuardState.DEGRADED,
                audioState = GuardAudioState.UNAVAILABLE,
                lastChangedAt = now,
                reason = "音频不可用：系统限制导致无法获取正在播放的声音（模拟场景）。",
            )

            GuardScenario.AUTHORIZATION_LOST -> GuardStatus(
                state = GuardState.ERROR,
                audioState = GuardAudioState.UNVERIFIED,
                lastChangedAt = now,
                reason = "屏幕采集授权已失效，请重新授权（模拟场景）。",
            )

            GuardScenario.START_FAILURE -> GuardStatus(
                state = GuardState.ERROR,
                audioState = GuardAudioState.UNVERIFIED,
                lastChangedAt = now,
                reason = "模拟启动失败：守护服务未能启动，请稍后重试。",
            )

            GuardScenario.USER_STOPPED -> GuardStatus(
                state = GuardState.STOPPED,
                audioState = GuardAudioState.UNVERIFIED,
                lastChangedAt = now,
                reason = null,
            )
        }
    }
}
