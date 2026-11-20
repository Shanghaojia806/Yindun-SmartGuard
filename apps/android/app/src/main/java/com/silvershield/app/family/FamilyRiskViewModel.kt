package com.silvershield.app.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 家属端家庭首页 / 风险列表 / 协同清单 / 消息中心共享状态（P08）。
 *
 * 所有统计只基于 [SharedRiskEventRepository.observeVisible] 的结果，
 * 未授权事件不会被计入任何数字。
 */
data class FamilyRiskUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val relation: FamilyRelation? = null,
    val activeConsents: List<ConsentRecord> = emptyList(),
    val items: List<FamilyEventItem> = emptyList(),
    val allShares: List<SharedRiskEvent> = emptyList(),
    val messages: List<FamilyMessage> = emptyList(),
    val networkMode: FamilyNetworkMode = FamilyNetworkMode.Online,
    val message: String? = null,
) {
    val isBound: Boolean get() = relation?.relationStatus == FamilyRelationStatus.ACTIVE
    val isPendingRelation: Boolean get() = relation?.relationStatus == FamilyRelationStatus.PENDING
    val hasConsent: Boolean get() = activeConsents.isNotEmpty()

    val revokedShareCount: Int get() = allShares.count { it.shareStatus != ShareStatus.ACTIVE }
    val revokedShareIds: Set<String>
        get() = allShares.filter { it.shareStatus != ShareStatus.ACTIVE }
            .mapTo(mutableSetOf()) { it.shareId }

    val unreadMessageCount: Int get() = messages.count { !it.isRead }

    /** 已授权共享事件数量。 */
    val sharedCount: Int get() = items.size

    /** 待查看事件数量。 */
    val unreadEventCount: Int get() = items.count { it.share.readAt == null }

    /** 待处理事件数量。 */
    val pendingHandlingCount: Int get() = items.count { it.handlingStatus.isOpen }

    /** 已协助处理事件数量。 */
    val assistedCount: Int get() = items.count { it.handlingStatus == HandlingStatus.Assisted }

    /** 新共享且未查看。 */
    val newSharedCount: Int
        get() = items.count {
            it.share.readAt == null && it.handlingStatus == HandlingStatus.PendingView
        }

    val viewedPendingCount: Int get() = items.count { it.handlingStatus == HandlingStatus.Viewed }
    val verifyingCount: Int get() = items.count { it.handlingStatus == HandlingStatus.Verifying }
    val contactedCount: Int get() = items.count { it.handlingStatus == HandlingStatus.Contacted }

    val allHandled: Boolean
        get() = items.isNotEmpty() && items.all {
            it.handlingStatus == HandlingStatus.Assisted ||
                it.handlingStatus == HandlingStatus.Unverifiable
        }

    val consentSummary: String
        get() = if (hasConsent) "已获得 ${activeConsents.size} 项共享授权" else "未获得共享授权"
}

class FamilyRiskViewModel(
    private val sharedRiskRepository: SharedRiskEventRepository,
    private val handlingRecordRepository: HandlingRecordRepository,
    private val messageRepository: FamilyMessageRepository,
) : ViewModel() {

    private data class LocalState(
        val loading: Boolean = true,
        val loadFailed: Boolean = false,
        val message: String? = null,
    )

    private data class CoreState(
        val visible: List<SharedEventView>,
        val relation: FamilyRelation?,
        val consents: List<ConsentRecord>,
        val messages: List<FamilyMessage>,
    )

    private data class SnapshotState(
        val core: CoreState,
        val items: List<FamilyEventItem>,
        val shares: List<SharedRiskEvent>,
        val networkMode: FamilyNetworkMode,
    )

    private val localState = MutableStateFlow(LocalState())

    private val core = combine(
        sharedRiskRepository.observeVisible(),
        sharedRiskRepository.relation,
        sharedRiskRepository.activeConsents,
        messageRepository.messages,
    ) { visible, relation, consents, messages ->
        CoreState(visible, relation, consents, messages)
    }

    private val snapshot = combine(
        core,
        handlingRecordRepository.records,
        sharedRiskRepository.shares,
        messageRepository.networkMode,
    ) { coreState, records, shares, mode ->
        val latestByShare = records.groupBy { it.shareId }
            .mapValues { entry -> entry.value.maxByOrNull { it.createdAt } }
        val items = coreState.visible.map { view ->
            val latest = latestByShare[view.share.shareId]
            FamilyEventItem(
                share = view.share,
                event = view.event,
                handlingStatus = latest?.handlingStatus ?: HandlingStatus.PendingView,
                latestHandling = latest,
            )
        }
        SnapshotState(coreState, items, shares, mode)
    }

    val uiState: StateFlow<FamilyRiskUiState> = combine(snapshot, localState) { snapshotState, local ->
        FamilyRiskUiState(
            loading = local.loading,
            loadFailed = local.loadFailed,
            relation = snapshotState.core.relation,
            activeConsents = snapshotState.core.consents,
            items = snapshotState.items,
            allShares = snapshotState.shares,
            messages = snapshotState.core.messages,
            networkMode = snapshotState.networkMode,
            message = local.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FamilyRiskUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            localState.update { it.copy(loading = true, loadFailed = false) }
            runCatching {
                sharedRiskRepository.sync()
                messageRepository.refresh()
            }.onSuccess {
                localState.update { it.copy(loading = false) }
            }.onFailure { error ->
                localState.update {
                    it.copy(
                        loading = false,
                        loadFailed = true,
                        message = error.message ?: "数据加载失败，请重试",
                    )
                }
            }
        }
    }

    fun retry() = refresh()

    fun markMessageRead(messageId: String) {
        viewModelScope.launch { messageRepository.markRead(messageId) }
    }

    fun markAllMessagesRead() {
        viewModelScope.launch { messageRepository.markAllRead() }
    }

    fun setNetworkMode(mode: FamilyNetworkMode) {
        viewModelScope.launch { messageRepository.setNetworkMode(mode) }
    }

    fun consumeMessage() {
        localState.update { it.copy(message = null) }
    }

    companion object {
        fun factory(
            sharedRiskRepository: SharedRiskEventRepository,
            handlingRecordRepository: HandlingRecordRepository,
            messageRepository: FamilyMessageRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FamilyRiskViewModel(
                    sharedRiskRepository,
                    handlingRecordRepository,
                    messageRepository,
                ) as T
        }
    }
}
