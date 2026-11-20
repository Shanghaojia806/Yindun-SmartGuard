package com.silvershield.app.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.silvershield.app.guard.risk.RiskEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * F06 风险详情 / F07 协同处理 / F08 处理详情共享状态。
 *
 * 每次状态变化都重新走 [SharedRiskEventRepository.observeAccess]：
 * 老人撤销授权或解除关系后，页面会立即收到 [FamilyEventUiState.accessDenied]，
 * 停止展示受限详情，不需要依赖菜单或前端状态。
 */
data class FamilyEventUiState(
    val loading: Boolean = true,
    val accessDenied: ShareDenyReason? = null,
    val share: SharedRiskEvent? = null,
    val event: RiskEvent? = null,
    val handlingStatus: HandlingStatus = HandlingStatus.PendingView,
    val history: List<HandlingRecord> = emptyList(),
    val saving: Boolean = false,
    val message: String? = null,
) {
    val currentHandler: String? get() = history.lastOrNull()?.handlerUserId
    val lastHandledAt: Long? get() = history.lastOrNull()?.createdAt
    val isGranted: Boolean get() = share != null && event != null && accessDenied == null
}

class FamilyEventViewModel(
    private val shareId: String,
    private val sharedRiskRepository: SharedRiskEventRepository,
    private val handlingRecordRepository: HandlingRecordRepository,
    private val messageRepository: FamilyMessageRepository,
) : ViewModel() {

    private data class LocalState(
        val loading: Boolean = true,
        val saving: Boolean = false,
        val message: String? = null,
    )

    private val localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<FamilyEventUiState> = combine(
        sharedRiskRepository.observeAccess(shareId),
        handlingRecordRepository.records,
        localState,
    ) { access, records, local ->
        val history = records.filter { it.shareId == shareId }.sortedBy { it.createdAt }
        when (access) {
            is ShareAccess.Denied -> FamilyEventUiState(
                loading = local.loading,
                accessDenied = access.reason,
                history = history,
                saving = local.saving,
                message = local.message,
            )

            is ShareAccess.Granted -> FamilyEventUiState(
                loading = local.loading,
                share = access.view.share,
                event = access.view.event,
                handlingStatus = history.lastOrNull()?.handlingStatus ?: HandlingStatus.PendingView,
                history = history,
                saving = local.saving,
                message = local.message,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FamilyEventUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            localState.update { it.copy(loading = true) }
            runCatching {
                sharedRiskRepository.sync()
                sharedRiskRepository.markViewed(shareId)
                messageRepository.refresh()
            }.onSuccess {
                localState.update { it.copy(loading = false) }
            }.onFailure { error ->
                localState.update {
                    it.copy(loading = false, message = error.message ?: "数据加载失败，请重试")
                }
            }
        }
    }

    fun updateStatus(status: HandlingStatus, note: String) {
        if (localState.value.saving) return
        viewModelScope.launch {
            localState.update { it.copy(saving = true, message = null) }
            handlingRecordRepository.updateStatus(shareId, status, note)
                .onSuccess {
                    messageRepository.refresh()
                    localState.update { it.copy(saving = false, message = "处理记录已保存") }
                }
                .onFailure { error ->
                    localState.update {
                        it.copy(saving = false, message = error.message ?: "保存失败，请重试")
                    }
                }
        }
    }

    fun consumeMessage() {
        localState.update { it.copy(message = null) }
    }

    companion object {
        fun factory(
            shareId: String,
            sharedRiskRepository: SharedRiskEventRepository,
            handlingRecordRepository: HandlingRecordRepository,
            messageRepository: FamilyMessageRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FamilyEventViewModel(
                    shareId,
                    sharedRiskRepository,
                    handlingRecordRepository,
                    messageRepository,
                ) as T
        }
    }
}
