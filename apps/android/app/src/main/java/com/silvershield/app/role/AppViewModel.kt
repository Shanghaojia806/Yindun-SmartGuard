package com.silvershield.app.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 角色状态界面模型。
 *
 * @param loading 首次读取本地角色状态中，此时不渲染导航，避免闪现错误入口
 */
data class RoleUiState(
    val loading: Boolean = true,
    val state: LocalRoleState = LocalRoleState(),
)

/**
 * 应用级 ViewModel：统一管理角色状态。
 *
 * 页面只观察 [uiState] 与调用 [confirmRole]，不直接访问 DataStore，
 * 保证界面与存储实现解耦。
 */
class AppViewModel(private val repository: RoleRepository) : ViewModel() {

    val uiState: StateFlow<RoleUiState> = repository.state
        .map { RoleUiState(loading = false, state = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RoleUiState(),
        )

    /** 确认角色：首次引导与角色切换共用，写入后重启应用可直接恢复。 */
    fun confirmRole(role: UserRole) {
        viewModelScope.launch { repository.saveRole(role) }
    }

    companion object {
        /** 通过 Application Context 构造仓库，避免 Activity 泄漏。 */
        fun factory(repository: RoleRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(repository) }
        }
    }
}
