package com.silvershield.app.family

import com.silvershield.app.guard.risk.RiskType

/**
 * P07 家庭关系与授权模型。
 *
 * 核心约束：
 * - 家庭绑定（[FamilyRelation]）不等于数据共享授权（[ConsentRecord]）；
 * - 绑定成功后不自动产生任何授权，默认全部关闭；
 * - 家属权限只来源于老人签发的有效 [ConsentRecord]；
 * - 本轮所有数据均为本地模拟（`isMock = true`），不代表真实账号或服务端关系。
 */

/** 家庭关系状态。PENDING 只表示邀请已发出，尚未完成双方确认。 */
enum class FamilyRelationStatus(val label: String) {
    PENDING("等待确认"),
    ACTIVE("已绑定"),
    REJECTED("已拒绝"),
    EXPIRED("邀请已过期"),
    REVOKED("已解除"),
}

/** 邀请状态。邀请与正式家庭关系分开管理，邀请被接受后才产生 ACTIVE 关系。 */
enum class InviteStatus(val label: String) {
    PENDING("等待确认"),
    ACCEPTED("已接受"),
    REJECTED("已拒绝"),
    EXPIRED("已过期"),
    CANCELLED("已取消"),
}

/** 参与方角色。邀请机制角色中立：老人和家属均可发起。 */
enum class InviteParty(val label: String) {
    Elder("老人"),
    Family("家属"),
    ;

    fun other(): InviteParty = if (this == Elder) Family else Elder
}

/** 授权方式：按单条事件分享 / 事先授权指定范围。 */
enum class AuthorizationType(val label: String) {
    EventShare("按事件分享"),
    ScopeGrant("事先授权范围"),
}

/** 授权状态。NOT_GRANTED 表示没有任何记录，仅用于界面展示。 */
enum class ConsentStatus(val label: String) {
    NOT_GRANTED("未授权"),
    ACTIVE("授权有效"),
    EXPIRED("授权已过期"),
    REVOKED("授权已撤销"),
}

/** 授权范围类型。 */
enum class ConsentScopeType {
    EventIds,
    RiskTypes,
    AllTypes,
}

/**
 * 家庭关系（正式关联）。
 *
 * 邀请码与邀请生命周期在 [FamilyInvite] 中管理；本模型只记录关系本身，
 * 邀请被接受时才从 PENDING 转为 ACTIVE（双向确认）。
 */
data class FamilyRelation(
    val relationId: String,
    val elderUserId: String,
    val familyUserId: String,
    val elderDisplayName: String,
    val familyDisplayName: String,
    val relationStatus: FamilyRelationStatus,
    val initiatedBy: InviteParty,
    val createdAt: Long,
    val confirmedAt: Long?,
    val expiresAt: Long,
    val revokedAt: Long?,
    val isMock: Boolean = true,
) {
    /** 结合当前时间计算有效状态；REVOKED 优先，PENDING 超时视为 EXPIRED。 */
    fun effectiveStatus(now: Long): FamilyRelationStatus = when {
        relationStatus == FamilyRelationStatus.REVOKED -> FamilyRelationStatus.REVOKED
        relationStatus == FamilyRelationStatus.REJECTED -> FamilyRelationStatus.REJECTED
        relationStatus == FamilyRelationStatus.PENDING && now >= expiresAt -> FamilyRelationStatus.EXPIRED
        else -> relationStatus
    }

    fun displayNameOf(role: InviteParty): String = when (role) {
        InviteParty.Elder -> elderDisplayName
        InviteParty.Family -> familyDisplayName
    }

    fun counterpartNameOf(role: InviteParty): String = displayNameOf(role.other())
}

/**
 * 绑定邀请。
 *
 * 邀请码为 6 位本地模拟码，具有有效期且只能使用一次；
 * 接受/拒绝前不会产生 ACTIVE 家庭关系。
 */
data class FamilyInvite(
    val inviteId: String,
    val inviteCode: String,
    val inviterRole: InviteParty,
    val inviterUserId: String,
    val inviterDisplayName: String,
    val inviteeRole: InviteParty,
    val createdAt: Long,
    val expiresAt: Long,
    val status: InviteStatus,
    val usedAt: Long? = null,
    val relationId: String? = null,
    val isMock: Boolean = true,
) {
    /** PENDING 且超过有效期 → EXPIRED；其余按原状态。 */
    fun effectiveStatus(now: Long): InviteStatus =
        if (status == InviteStatus.PENDING && now >= expiresAt) InviteStatus.EXPIRED else status

    /** 对方（被邀请方）显示名称；本机模拟环境中按角色映射固定身份。 */
    val inviteeDisplayName: String
        get() = LocalFamilyAccounts.displayNameOf(inviteeRole)
}

/**
 * 授权记录。
 *
 * - [authorizationType] 区分「按事件分享」与「事先授权范围」；
 * - [scopeValues] 在 EventIds 时为 eventId 列表，在 RiskTypes 时为 RiskType.name；
 * - 不包含原始屏幕录像、实时屏幕或完整浏览历史字段，从模型层禁止这些范围。
 */
data class ConsentRecord(
    val consentId: String,
    val elderUserId: String,
    val familyUserId: String,
    val relationId: String,
    val authorizationType: AuthorizationType,
    val scopeType: ConsentScopeType,
    val scopeValues: List<String>,
    val sourceEventId: String?,
    val grantedAt: Long,
    val expiresAt: Long?,
    val revokedAt: Long?,
    val consentStatus: ConsentStatus,
    val updatedAt: Long,
    val note: String? = null,
    val isMock: Boolean = true,
) {
    /** 结合当前时间计算有效状态：撤销 > 过期 > 有效。 */
    fun effectiveStatus(now: Long): ConsentStatus = when {
        consentStatus == ConsentStatus.REVOKED || revokedAt != null -> ConsentStatus.REVOKED
        expiresAt != null && now >= expiresAt -> ConsentStatus.EXPIRED
        consentStatus == ConsentStatus.EXPIRED -> ConsentStatus.EXPIRED
        else -> consentStatus
    }

    /** 该授权是否覆盖指定事件。 */
    fun covers(eventId: String, riskType: RiskType): Boolean = when (scopeType) {
        ConsentScopeType.EventIds -> eventId in scopeValues
        ConsentScopeType.RiskTypes -> riskType.name in scopeValues
        ConsentScopeType.AllTypes -> true
    }

    /** 面向老人的范围说明，不使用专业术语。 */
    fun scopeDescription(): String = when (authorizationType) {
        AuthorizationType.EventShare -> "仅这一条风险事件（${sourceEventId ?: "未知事件"}）"
        AuthorizationType.ScopeGrant -> when (scopeType) {
            ConsentScopeType.AllTypes -> "全部风险类型的摘要信息"
            ConsentScopeType.RiskTypes -> scopeValues
                .mapNotNull { key -> RiskType.entries.firstOrNull { it.name == key }?.label }
                .joinToString("、")
                .ifBlank { "未指定范围" }
            ConsentScopeType.EventIds -> "指定的 ${scopeValues.size} 条风险事件"
        }
    }
}

/** 最近一次有效家庭关系；没有则返回 null。 */
fun List<FamilyRelation>.activeRelation(now: Long): FamilyRelation? =
    firstOrNull { it.effectiveStatus(now) == FamilyRelationStatus.ACTIVE }

/** 最近一次关系（含等待/已解除），用于界面展示。 */
fun List<FamilyRelation>.latestRelation(): FamilyRelation? = maxByOrNull { it.createdAt }

/**
 * 计算某条风险事件对家属的共享状态。
 *
 * 优先返回 ACTIVE：只要仍存在覆盖该事件的有效授权，就不能显示"已撤销"。
 */
fun List<ConsentRecord>.eventConsentStatus(
    eventId: String,
    riskType: RiskType,
    now: Long,
): ConsentStatus {
    val related = filter { it.covers(eventId, riskType) }
    if (related.isEmpty()) return ConsentStatus.NOT_GRANTED
    if (related.any { it.effectiveStatus(now) == ConsentStatus.ACTIVE }) return ConsentStatus.ACTIVE
    if (related.any { it.effectiveStatus(now) == ConsentStatus.REVOKED }) return ConsentStatus.REVOKED
    if (related.any { it.effectiveStatus(now) == ConsentStatus.EXPIRED }) return ConsentStatus.EXPIRED
    return ConsentStatus.NOT_GRANTED
}

/** 邀请码查询结果。 */
sealed interface InviteLookup {
    data class Found(val invite: FamilyInvite) : InviteLookup
    data object NotFound : InviteLookup
    data object Expired : InviteLookup
    data class Used(val status: InviteStatus) : InviteLookup
    data object SelfInvite : InviteLookup
    data object InvalidFormat : InviteLookup
}

fun InviteLookup.userMessage(): String = when (this) {
    is InviteLookup.Found -> "邀请码有效，请核对下方信息后确认"
    InviteLookup.NotFound -> "没有找到这个邀请码，请确认是否输入正确"
    InviteLookup.Expired -> "这个邀请码已过期，请让对方重新生成"
    is InviteLookup.Used -> "这个邀请码已被使用（${status.label}），不能重复使用"
    InviteLookup.SelfInvite -> "这是你自己生成的邀请码，请交给对方输入"
    InviteLookup.InvalidFormat -> "邀请码应为 6 位字母或数字"
}

/** 业务失败原因；界面统一转成通俗中文。 */
enum class FamilyFailureReason {
    INVALID_FORMAT,
    NOT_FOUND,
    EXPIRED,
    USED,
    SELF_INVITE,
    NO_RELATION,
    ALREADY_BOUND,
    INVALID_SCOPE,
    FORBIDDEN,
}

class FamilyOperationException(
    val reason: FamilyFailureReason,
    override val message: String,
) : Exception(message)

/**
 * 本机模拟身份。
 *
 * 不接入真实账号体系；老人端与家属端在同一台设备上通过角色切换模拟双方。
 */
object LocalFamilyAccounts {
    const val ELDER_USER_ID = "local-elder"
    const val FAMILY_USER_ID = "local-family"
    const val ELDER_DISPLAY_NAME = "本机老人（模拟）"
    const val FAMILY_DISPLAY_NAME = "本机家属（模拟）"

    fun userIdOf(role: InviteParty): String = when (role) {
        InviteParty.Elder -> ELDER_USER_ID
        InviteParty.Family -> FAMILY_USER_ID
    }

    fun displayNameOf(role: InviteParty): String = when (role) {
        InviteParty.Elder -> ELDER_DISPLAY_NAME
        InviteParty.Family -> FAMILY_DISPLAY_NAME
    }
}
