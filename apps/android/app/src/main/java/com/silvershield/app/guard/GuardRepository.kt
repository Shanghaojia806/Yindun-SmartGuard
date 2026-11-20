package com.silvershield.app.guard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** 开始守护的结果。 */
sealed interface GuardStartResult {
    /** 尚未完成屏幕采集授权，需要先进入授权说明页。 */
    data object NeedAuthorization : GuardStartResult

    /** 已进入模拟守护流程。 */
    data object Started : GuardStartResult

    /** 启动失败，携带可展示的原因。 */
    data class Failed(val reason: String?) : GuardStartResult
}

/** 切换模拟场景的结果。 */
enum class GuardScenarioResult {
    APPLIED,
    NEED_AUTHORIZATION,
    UNSUPPORTED,
}

/**
 * 守护仓库：页面与守护服务之间的唯一桥接层。
 *
 * 统一数据流：
 * `FakeGuardService / GuardSettingsStore → GuardRepository → GuardViewModel → Compose UI`
 *
 * 页面不得直接调用 [GuardService]，也不得自行维护硬编码状态。
 */
class GuardRepository(
    private val service: GuardService,
    private val settingsStore: GuardSettingsStore,
) {

    /** 守护状态（唯一来源）。 */
    val status: StateFlow<GuardStatus> = service.status

    /** 本地守护偏好。 */
    val preferences: Flow<GuardPreferences> = settingsStore.preferences

    private val scenarioController: GuardScenarioController? = service as? GuardScenarioController

    /** 模拟场景；真实实现下为 null，页面据此隐藏开发测试面板。 */
    val scenario: StateFlow<GuardScenario?> = scenarioController?.scenario ?: MutableStateFlow(null)

    /** 是否存在模拟场景控制器。 */
    val supportsScenarioSimulation: Boolean = scenarioController != null

    /** 记录模拟授权成功（P04：页面内模拟，不调用系统 MediaProjection）。 */
    suspend fun grantScreenAuthorization() {
        updatePreferences { it.copy(screenAuthorization = ScreenAuthorizationState.GRANTED) }
    }

    /**
     * 撤销屏幕采集授权。
     *
     * 先持久化"已撤销"，再通知服务；若守护正在运行会立即离开 RUNNING。
     */
    suspend fun revokeScreenAuthorization() {
        updatePreferences { it.copy(screenAuthorization = ScreenAuthorizationState.REVOKED) }
        service.onAuthorizationRevoked()
    }

    /** 开始守护；未授权时拒绝启动，避免出现"未授权却在运行"的矛盾状态。 */
    suspend fun startGuard(): GuardStartResult {
        val current = preferences.first()
        if (current.screenAuthorization != ScreenAuthorizationState.GRANTED) {
            return GuardStartResult.NeedAuthorization
        }
        val result = service.requestStart()
        return if (result.state == GuardState.ERROR) {
            GuardStartResult.Failed(result.reason)
        } else {
            GuardStartResult.Started
        }
    }

    /** 停止守护。 */
    suspend fun stopGuard() {
        service.stop()
    }

    /**
     * 切换模拟场景（开发测试用）。
     *
     * 授权失效场景会同步把授权状态置为已撤销，避免 E05 与 E14 状态冲突。
     */
    suspend fun selectScenario(scenario: GuardScenario): GuardScenarioResult {
        val controller = scenarioController ?: return GuardScenarioResult.UNSUPPORTED
        val current = preferences.first()
        if (scenario != GuardScenario.USER_STOPPED &&
            current.screenAuthorization != ScreenAuthorizationState.GRANTED
        ) {
            return GuardScenarioResult.NEED_AUTHORIZATION
        }
        if (scenario == GuardScenario.AUTHORIZATION_LOST) {
            updatePreferences { it.copy(screenAuthorization = ScreenAuthorizationState.REVOKED) }
        }
        controller.selectScenario(scenario)
        return GuardScenarioResult.APPLIED
    }

    suspend fun setRiskReminderEnabled(enabled: Boolean) {
        updatePreferences { it.copy(riskReminderEnabled = enabled) }
    }

    suspend fun setVoiceReminderEnabled(enabled: Boolean) {
        updatePreferences { it.copy(voiceReminderEnabled = enabled) }
    }

    suspend fun setReminderVolume(volume: ReminderVolume) {
        updatePreferences { it.copy(reminderVolume = volume) }
    }

    suspend fun setStatusHintEnabled(enabled: Boolean) {
        updatePreferences { it.copy(statusHintEnabled = enabled) }
    }

    private suspend fun updatePreferences(transform: (GuardPreferences) -> GuardPreferences) {
        settingsStore.update(transform)
    }
}
