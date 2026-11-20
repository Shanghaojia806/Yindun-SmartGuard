package com.silvershield.app.ui.elder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.silvershield.app.family.ConsentRecord
import com.silvershield.app.family.ConsentRepository
import com.silvershield.app.family.ConsentStatus
import com.silvershield.app.family.ConsentValidity
import com.silvershield.app.family.FamilyRelation
import com.silvershield.app.family.FamilyRepository
import com.silvershield.app.family.activeRelation
import com.silvershield.app.family.eventConsentStatus
import com.silvershield.app.guard.risk.RiskEvent
import com.silvershield.app.guard.risk.RiskEventRepository
import com.silvershield.app.guard.risk.RiskHandleStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * E07 风险详情页状态。
 *
 * [loading] 为 true 表示首次读取尚未完成；
 * 读取完成但 [event] 为 null 表示记录不存在或已被删除。
 */
data class RiskDetailUiState(
    val event: RiskEvent? = null,
    val loading: Boolean = true,
    val message: String? = null,
    /** 本条事件对家属的共享状态，来自真实 ConsentRecord，而不是全局开关。 */
    val shareStatus: ConsentStatus = ConsentStatus.NOT_GRANTED,
    /** 是否已与家人绑定；未绑定时不能分享。 */
    val canShare: Boolean = false,
)

/**
 * E07 风险详情 ViewModel（P06）。
 *
 * 按 eventId 订阅单条记录；用户处理状态可独立更新，不影响 AI 判断字段。
 */
class RiskDetailViewModel(
    private val repository: RiskEventRepository,
    private val eventId: String,
    private val consentRepository: ConsentRepository,
    private val familyRepository: FamilyRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private data class StoredEvents(
        val events: List<RiskEvent>?,
        val failed: Boolean,
    )

    private data class SharingState(
        val consents: List<ConsentRecord>,
        val relations: List<FamilyRelation>,
    )

    private val message = MutableStateFlow<String?>(null)

    private val storedEvents: StateFlow<StoredEvents> = repository.events
        .map { StoredEvents(events = it, failed = false) }
        .catch { emit(StoredEvents(events = null, failed = true)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StoredEvents(events = null, failed = false),
        )

    private val sharing: StateFlow<SharingState> = combine(
        consentRepository.consents,
        familyRepository.relations,
    ) { consents, relations ->
        SharingState(consents = consents, relations = relations)
    }
        .catch { emit(SharingState(emptyList(), emptyList())) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SharingState(emptyList(), emptyList()),
        )

    val uiState: StateFlow<RiskDetailUiState> =
        combine(storedEvents, message, sharing) { stored, currentMessage, shareState ->
            val timestamp = now()
            val event = stored.events?.firstOrNull { it.eventId == eventId }
            val relationActive = shareState.relations.activeRelation(timestamp) != null
            RiskDetailUiState(
                event = event,
                loading = stored.events == null && !stored.failed,
                message = currentMessage,
                shareStatus = if (event == null || !relationActive) {
                    ConsentStatus.NOT_GRANTED
                } else {
                    shareState.consents.eventConsentStatus(eventId, event.riskType, timestamp)
                },
                canShare = event != null && relationActive,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RiskDetailUiState(),
        )

    /** 标记为已处理：只修改用户处理状态，不改动 AI 判断结果。 */
    fun markHandled() {
        viewModelScope.launch {
            runCatching { repository.updateHandleStatus(eventId, RiskHandleStatus.Handled) }
                .onSuccess { message.value = "已标记为处理完成" }
                .onFailure { message.value = "操作失败，请稍后重试" }
        }
    }

    /** 老人主动分享本条事件：只记录事件摘要授权，不复制风险记录、不上传画面。 */
    fun shareEvent() {
        viewModelScope.launch {
            consentRepository.grantEventShare(eventId, ConsentValidity.SevenDays.millis)
                .onSuccess {
                    message.value = "已记录这次分享授权：只分享事件摘要，尚未跨设备发送。"
                }
                .onFailure { error ->
                    message.value = (error as? com.silvershield.app.family.FamilyOperationException)
                        ?.message ?: "分享失败，请稍后重试"
                }
        }
    }

    /** 撤销本条事件的单独分享记录；范围授权请在「风险共享授权」页撤销。 */
    fun revokeShare() {
        viewModelScope.launch {
            consentRepository.revokeEventShare(eventId)
                .onSuccess { message.value = "已撤销这条事件的分享授权。" }
                .onFailure { error ->
                    message.value = (error as? com.silvershield.app.family.FamilyOperationException)
                        ?.message ?: "撤销失败，请稍后重试"
                }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        fun factory(
            repository: RiskEventRepository,
            eventId: String,
            consentRepository: ConsentRepository,
            familyRepository: FamilyRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RiskDetailViewModel(repository, eventId, consentRepository, familyRepository)
            }
        }
    }
}
