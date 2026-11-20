package com.silvershield.app.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.silvershield.app.role.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 家属绑定（F02）/ 关联关系（F04）/ 老人端家庭关系管理共用状态。
 *
 * @param role 当前使用身份；决定邀请发起角色与可操作的动作
 */
data class FamilyUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val relation: FamilyRelation? = null,
    val invites: List<FamilyInvite> = emptyList(),
    /** 本机刚刚生成的邀请；仅用于展示邀请码，不代表已绑定。 */
    val generatedInvite: FamilyInvite? = null,
    /** 当前角色发出、仍在等待确认的邀请；应用重启后仍可从本地恢复。 */
    val pendingInvite: FamilyInvite? = null,
    /** 输入邀请码后查询到的对方信息，确认前不建立关系。 */
    val preview: FamilyInvite? = null,
    val message: String? = null,
) {
    /** 当前是否已建立正式家庭关系。 */
    val isBound: Boolean
        get() = relation?.relationStatus == FamilyRelationStatus.ACTIVE
}

/**
 * P07 家庭关系 ViewModel。
 *
 * 页面只通过本 ViewModel 调用仓库；邀请码校验、双向确认、解除关系等规则
 * 全部在 [FakeFamilyRepository] 内执行，Composable 不硬编码任何关系状态。
 */
class FamilyViewModel(
    role: UserRole,
    private val familyRepository: FamilyRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val party: InviteParty =
        if (role == UserRole.Elder) InviteParty.Elder else InviteParty.Family

    private data class Stored(
        val relations: List<FamilyRelation>,
        val invites: List<FamilyInvite>,
        val failed: Boolean,
        val loaded: Boolean = false,
    )

    private data class LocalState(
        val generatedInvite: FamilyInvite? = null,
        val preview: FamilyInvite? = null,
        val message: String? = null,
    )

    private val localState = MutableStateFlow(LocalState())

    private val stored: StateFlow<Stored> = combine(
        familyRepository.relations,
        familyRepository.invites,
    ) { relations, invites ->
        Stored(relations = relations, invites = invites, failed = false, loaded = true)
    }
        .catch { emit(Stored(emptyList(), emptyList(), failed = true, loaded = true)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Stored(emptyList(), emptyList(), failed = false),
        )

    val uiState: StateFlow<FamilyUiState> = combine(stored, localState) { data, local ->
        val timestamp = now()
        val relation = data.relations.latestRelation()?.let {
            it.copy(relationStatus = it.effectiveStatus(timestamp))
        }
        val invites = data.invites.map { it.copy(status = it.effectiveStatus(timestamp)) }
        FamilyUiState(
            loading = !data.loaded && !data.failed,
            loadFailed = data.failed,
            relation = relation,
            invites = invites,
            generatedInvite = local.generatedInvite?.let { invite ->
                invites.firstOrNull { it.inviteId == invite.inviteId } ?: invite
            },
            pendingInvite = invites.firstOrNull {
                it.inviterRole == party && it.status == InviteStatus.PENDING
            },
            preview = local.preview,
            message = local.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FamilyUiState(),
    )

    init {
        viewModelScope.launch { runCatching { familyRepository.refreshExpiry() } }
    }

    /** 当前角色标签，用于界面说明。 */
    val roleLabel: String get() = party.label

    fun generateInvite() {
        viewModelScope.launch {
            familyRepository.generateInvite(party)
                .onSuccess { invite ->
                    localState.update {
                        it.copy(
                            generatedInvite = invite,
                            preview = null,
                            message = "邀请码已生成，有效期 24 小时。请让对方在自己的设备上输入。",
                        )
                    }
                }
                .onFailure { error ->
                    localState.update { it.copy(message = error.userMessage()) }
                }
        }
    }

    /** 查询邀请码，仅展示对方与邀请信息，不建立关系。 */
    fun lookupInvite(code: String) {
        viewModelScope.launch {
            when (val lookup = familyRepository.lookupInvite(code, party)) {
                is InviteLookup.Found -> localState.update {
                    it.copy(preview = lookup.invite, message = lookup.userMessage())
                }

                else -> localState.update {
                    it.copy(preview = null, message = lookup.userMessage())
                }
            }
        }
    }

    /** 接受邀请：必须由对方确认，接受后才进入 ACTIVE。 */
    fun acceptInvite(code: String) {
        viewModelScope.launch {
            familyRepository.acceptInvite(code, party)
                .onSuccess {
                    localState.update { state ->
                        state.copy(
                            preview = null,
                            generatedInvite = null,
                            message = "已确认绑定。双方都可以在「关联关系」中看到这段家庭关系。",
                        )
                    }
                }
                .onFailure { error ->
                    localState.update { it.copy(message = error.userMessage()) }
                }
        }
    }

    fun rejectInvite(code: String) {
        viewModelScope.launch {
            familyRepository.rejectInvite(code, party)
                .onSuccess {
                    localState.update {
                        it.copy(preview = null, message = "已拒绝这次邀请，不会建立家庭关系。")
                    }
                }
                .onFailure { error -> localState.update { it.copy(message = error.userMessage()) } }
        }
    }

    fun cancelInvite(inviteId: String) {
        viewModelScope.launch {
            familyRepository.cancelInvite(inviteId)
                .onSuccess {
                    localState.update {
                        it.copy(generatedInvite = null, message = "已取消邀请，邀请码作废。")
                    }
                }
                .onFailure { error -> localState.update { it.copy(message = error.userMessage()) } }
        }
    }

    fun revokeRelation(relationId: String) {
        viewModelScope.launch {
            familyRepository.revokeRelation(relationId, party)
                .onSuccess {
                    localState.update {
                        it.copy(
                            preview = null,
                            message = "已解除家庭关系。原有共享授权同时失效，老人本机风险记录不受影响。",
                        )
                    }
                }
                .onFailure { error -> localState.update { it.copy(message = error.userMessage()) } }
        }
    }

    fun clearPreview() {
        localState.update { it.copy(preview = null) }
    }

    fun consumeMessage() {
        localState.update { it.copy(message = null) }
    }

    fun retry() {
        viewModelScope.launch { runCatching { familyRepository.refreshExpiry() } }
    }

    companion object {
        fun factory(role: UserRole, repository: FamilyRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { FamilyViewModel(role, repository) }
            }
    }
}

/** 把异常转成面向老人的通俗提示，不展示堆栈信息。 */
private fun Throwable.userMessage(): String =
    (this as? FamilyOperationException)?.message ?: "操作失败，请稍后重试"
