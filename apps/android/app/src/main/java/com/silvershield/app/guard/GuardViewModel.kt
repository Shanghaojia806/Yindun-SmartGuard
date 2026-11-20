package com.silvershield.app.guard

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
 * 老人端守护界面状态。
 *
 * 五个页面（E03/E04/E05/E13/E14）共享同一个实例与同一份状态。
 */
data class GuardUiState(
    /** 首次读取 DataStore 中，避免闪现错误偏好。 */
    val loading: Boolean = true,
    val status: GuardStatus = GuardStatus(),
    val preferences: GuardPreferences = GuardPreferences(),
    /** 当前模拟场景；null 表示当前实现不支持场景切换。 */
    val scenario: GuardScenario? = null,
    /** 一次性操作反馈（Snackbar 文案）。 */
    val message: String? = null,
)

/**
 * 守护界面模型（唯一状态持有者）。
 *
 * - 通过 [GuardRepository] 调用 [GuardService]，页面不直接执行业务逻辑；
 * - 所有状态通过 [StateFlow] 单向流向 UI；
 * - 首页、运行状态、隐私与授权页面看到的是同一份状态。
 */
class GuardViewModel(private val repository: GuardRepository) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<GuardUiState> = combine(
        repository.status,
        repository.preferences,
        repository.scenario,
        message,
    ) { status, preferences, scenario, message ->
        GuardUiState(
            loading = false,
            status = status,
            preferences = preferences,
            scenario = scenario,
            message = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GuardUiState(),
    )

    /** E04「同意并继续」：记录模拟授权并启动守护。 */
    fun grantAuthorizationAndStart() {
        viewModelScope.launch {
            repository.grantScreenAuthorization()
            when (val result = repository.startGuard()) {
                GuardStartResult.NeedAuthorization -> message.value = "请先完成屏幕采集授权"
                GuardStartResult.Started -> Unit
                is GuardStartResult.Failed ->
                    message.value = result.reason ?: "守护启动失败，请重新尝试"
            }
        }
    }

    /** E14 重新授权：只记录授权，不自动启动守护。 */
    fun grantAuthorizationOnly() {
        viewModelScope.launch {
            repository.grantScreenAuthorization()
            message.value = "已完成模拟授权"
        }
    }

    /** 停止守护，并给出明确反馈。 */
    fun stopGuard() {
        viewModelScope.launch {
            repository.stopGuard()
            message.value = "已停止守护，手机不会再进行识别"
        }
    }

    /** 撤销屏幕采集授权；若守护在运行会立即停止。 */
    fun revokeScreenAuthorization() {
        viewModelScope.launch {
            repository.revokeScreenAuthorization()
            message.value = "已撤销屏幕采集授权，守护已停止"
        }
    }

    /** 切换 E05 开发测试场景。 */
    fun selectScenario(scenario: GuardScenario) {
        viewModelScope.launch {
            when (repository.selectScenario(scenario)) {
                GuardScenarioResult.APPLIED -> Unit
                GuardScenarioResult.NEED_AUTHORIZATION ->
                    message.value = "请先完成模拟授权，再切换该场景"
                GuardScenarioResult.UNSUPPORTED ->
                    message.value = "当前版本不支持场景切换"
            }
        }
    }

    fun setRiskReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setRiskReminderEnabled(enabled) }
    }

    fun setVoiceReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setVoiceReminderEnabled(enabled) }
    }

    fun setReminderVolume(volume: ReminderVolume) {
        viewModelScope.launch { repository.setReminderVolume(volume) }
    }

    fun setStatusHintEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setStatusHintEnabled(enabled) }
    }

    /** 展示一次性提示（如取消授权）。 */
    fun showMessage(text: String) {
        message.value = text
    }

    /** Snackbar 展示后消费掉消息。 */
    fun consumeMessage() {
        message.value = null
    }

    companion object {
        fun factory(repository: GuardRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { GuardViewModel(repository) }
        }
    }
}
