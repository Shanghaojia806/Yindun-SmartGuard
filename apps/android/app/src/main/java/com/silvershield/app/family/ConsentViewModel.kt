package com.silvershield.app.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.silvershield.app.guard.risk.RiskType
import com.silvershield.app.role.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 事先授权的有效期选项；长期有效仍可随时撤销。 */
enum class ConsentValidity(val label: String, val millis: Long?) {
    SevenDays("7 天", 7L * 24 * 60 * 60 * 1000),
    ThirtyDays("30 天", 30L * 24 * 60 * 60 * 1000),
    LongTerm("长期有效（仍可撤销）", null),
}

/** 授权页状态。家属端与老人端共用，家属端只读取不写入。 */
data class ConsentUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val relation: FamilyRelation? = null,
    val consents: List<ConsentRecord> = emptyList(),
    val message: String? = null,
) {
    val isBound: Boolean
        get() = relation?.relationStatus == FamilyRelationStatus.ACTIVE

    val activeConsents: List<ConsentRecord>
        get() = consents.filter { it.consentStatus == ConsentStatus.ACTIVE }

    /** 已撤销 / 已过期记录，老人端展示为授权历史。 */
    val historyConsents: List<ConsentRecord>
        get() = consents.filter { it.consentStatus != ConsentStatus.ACTIVE }
}

/**
 * 风险共享授权 ViewModel（只读基类）。
 *
 * 家属端只使用本类：**没有任何写入方法**，从编译期保证家属不能代老人授权。
 */
open class ConsentViewModel(
    role: UserRole,
    protected val consentRepository: ConsentRepository,
    private val familyRepository: FamilyRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    protected val party: InviteParty =
        if (role == UserRole.Elder) InviteParty.Elder else InviteParty.Family

    protected val messageState = MutableStateFlow<String?>(null)

    private data class Stored(
        val consents: List<ConsentRecord>,
        val relations: List<FamilyRelation>,
        val failed: Boolean,
        val loaded: Boolean = false,
    )

    private val stored: StateFlow<Stored> = combine(
        consentRepository.consents,
        familyRepository.relations,
    ) { consents, relations ->
        Stored(consents = consents, relations = relations, failed = false, loaded = true)
    }
        .catch { emit(Stored(emptyList(), emptyList(), failed = true, loaded = true)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Stored(emptyList(), emptyList(), failed = false),
        )

    val uiState: StateFlow<ConsentUiState> = combine(stored, messageState) { data, message ->
        val timestamp = now()
        val relation = data.relations.latestRelation()?.let {
            it.copy(relationStatus = it.effectiveStatus(timestamp))
        }
        ConsentUiState(
            loading = !data.loaded && !data.failed,
            loadFailed = data.failed,
            relation = relation,
            consents = data.consents
                .map { it.copy(consentStatus = it.effectiveStatus(timestamp)) }
                .sortedByDescending { it.grantedAt },
            message = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConsentUiState(),
    )

    init {
        viewModelScope.launch {
            runCatching { consentRepository.refreshExpiry() }
            runCatching { familyRepository.refreshExpiry() }
        }
    }

    /** 当前身份标签，用于只读说明。 */
    val roleLabel: String get() = party.label

    fun refresh() {
        viewModelScope.launch {
            runCatching { consentRepository.refreshExpiry() }
            runCatching { familyRepository.refreshExpiry() }
        }
    }

    fun consumeMessage() {
        messageState.value = null
    }

    protected fun showMessage(text: String) {
        messageState.value = text
    }

    companion object {
        /** 家属端只读 ViewModel；不提供任何授权写入入口。 */
        fun familyFactory(
            consentRepository: ConsentRepository,
            familyRepository: FamilyRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ConsentViewModel(UserRole.Family, consentRepository, familyRepository)
            }
        }
    }
}

/**
 * 老人端授权管理 ViewModel。
 *
 * 只有老人身份会创建本类，家属端无法拿到任何 grant / revoke 方法。
 */
class ElderConsentViewModel(
    consentRepository: ConsentRepository,
    familyRepository: FamilyRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ConsentViewModel(UserRole.Elder, consentRepository, familyRepository, now) {

    /** 事先授权指定风险类型范围；默认不勾选，必须至少选一项。 */
    fun grantScope(riskTypes: Set<RiskType>, validity: ConsentValidity) {
        viewModelScope.launch {
            consentRepository.grantScope(riskTypes, validity.millis)
                .onSuccess { record ->
                    showMessage("已授权：${record.scopeDescription()}（仅本机模拟，未跨设备发送）")
                }
                .onFailure { error -> showMessage(error.friendlyMessage()) }
        }
    }

    /** 按事件分享，供 E07 详情页复用。 */
    fun grantEventShare(eventId: String, validity: ConsentValidity = ConsentValidity.SevenDays) {
        viewModelScope.launch {
            consentRepository.grantEventShare(eventId, validity.millis)
                .onSuccess { showMessage("已记录这次分享授权（仅事件摘要，未跨设备发送）") }
                .onFailure { error -> showMessage(error.friendlyMessage()) }
        }
    }

    /** 撤销授权：立即生效，重启后不会恢复。 */
    fun revoke(consentId: String) {
        viewModelScope.launch {
            consentRepository.revoke(consentId)
                .onSuccess { showMessage("已撤销这条授权，家属将不再看到对应内容。") }
                .onFailure { error -> showMessage(error.friendlyMessage()) }
        }
    }

    companion object {
        fun factory(
            consentRepository: ConsentRepository,
            familyRepository: FamilyRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ElderConsentViewModel(consentRepository, familyRepository) }
        }
    }
}

internal fun Throwable.friendlyMessage(): String =
    (this as? FamilyOperationException)?.message ?: "操作失败，请稍后重试"
