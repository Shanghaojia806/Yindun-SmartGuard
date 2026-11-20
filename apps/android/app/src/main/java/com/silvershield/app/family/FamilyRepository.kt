package com.silvershield.app.family

import kotlinx.coroutines.flow.Flow
import java.util.UUID
import kotlin.random.Random

/**
 * 家庭关系仓库（P07）。
 *
 * 当前只有本地模拟实现 [FakeFamilyRepository]；后续接入真实服务端时，
 * 页面与 ViewModel 无需修改，只需替换该接口实现。
 */
interface FamilyRepository {

    /** 全部家庭关系（按创建时间倒序）。 */
    val relations: Flow<List<FamilyRelation>>

    /** 全部绑定邀请（按创建时间倒序）。 */
    val invites: Flow<List<FamilyInvite>>

    /** 把已过期的邀请 / 等待关系标记为 EXPIRED 并持久化。 */
    suspend fun refreshExpiry()

    /** 生成邀请码；角色中立，老人和家属均可发起。 */
    suspend fun generateInvite(role: InviteParty): Result<FamilyInvite>

    /** 查询邀请码，返回对方身份与邀请信息，不触发接受。 */
    suspend fun lookupInvite(code: String, role: InviteParty): InviteLookup

    /** 接受邀请（必须由对方角色操作），完成后关系为 ACTIVE。 */
    suspend fun acceptInvite(code: String, role: InviteParty): Result<FamilyRelation>

    /** 拒绝邀请，完成后关系为 REJECTED。 */
    suspend fun rejectInvite(code: String, role: InviteParty): Result<Unit>

    /** 取消自己发出的待确认邀请。 */
    suspend fun cancelInvite(inviteId: String): Result<Unit>

    /** 解除已建立的家庭关系，同时使该关系下的授权全部失效。 */
    suspend fun revokeRelation(relationId: String, role: InviteParty): Result<Unit>
}

/**
 * 本地模拟的家庭关系仓库。
 *
 * 规则：
 * - 邀请码 6 位、唯一、24 小时有效、只能使用一次；
 * - 仅对方角色可接受/拒绝，禁止自己接受自己的邀请；
 * - 接受前关系为 PENDING，完成双方确认后才进入 ACTIVE；
 * - 解除关系不会删除老人本机风险记录。
 */
class FakeFamilyRepository(
    private val store: FamilyLocalStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val codeGenerator: () -> String = { randomInviteCode() },
    private val invitationValidityMillis: Long = INVITE_VALIDITY_MILLIS,
) : FamilyRepository {

    override val relations: Flow<List<FamilyRelation>> = store.relations
    override val invites: Flow<List<FamilyInvite>> = store.invites

    override suspend fun refreshExpiry() {
        val timestamp = now()
        store.currentInvites().forEach { invite ->
            if (invite.status == InviteStatus.PENDING && timestamp >= invite.expiresAt) {
                store.upsertInvite(invite.copy(status = InviteStatus.EXPIRED))
            }
        }
        store.currentRelations().forEach { relation ->
            if (relation.relationStatus == FamilyRelationStatus.PENDING && timestamp >= relation.expiresAt) {
                store.upsertRelation(relation.copy(relationStatus = FamilyRelationStatus.EXPIRED))
            }
        }
    }

    override suspend fun generateInvite(role: InviteParty): Result<FamilyInvite> {
        val timestamp = now()
        val existingRelations = store.currentRelations()
        val hasOpenRelation = existingRelations.any {
            val status = it.effectiveStatus(timestamp)
            status == FamilyRelationStatus.PENDING || status == FamilyRelationStatus.ACTIVE
        }
        if (hasOpenRelation) {
            return failure(
                FamilyFailureReason.ALREADY_BOUND,
                "当前已有绑定或等待确认的关系，请先处理现有关系",
            )
        }
        val hasOpenInvite = store.currentInvites().any {
            it.inviterRole == role && it.effectiveStatus(timestamp) == InviteStatus.PENDING
        }
        if (hasOpenInvite) {
            return failure(
                FamilyFailureReason.ALREADY_BOUND,
                "已经有一条等待确认的邀请，请先生成或取消",
            )
        }

        val existingCodes = store.currentInvites().map { it.inviteCode.uppercase() }.toSet()
        var code = codeGenerator()
        var attempts = 0
        while (code.uppercase() in existingCodes && attempts < MAX_CODE_ATTEMPTS) {
            code = codeGenerator()
            attempts++
        }
        if (code.uppercase() in existingCodes) {
            return failure(FamilyFailureReason.ALREADY_BOUND, "邀请码生成失败，请稍后重试")
        }

        val relationId = "rel-${UUID.randomUUID()}"
        val invite = FamilyInvite(
            inviteId = "inv-${UUID.randomUUID()}",
            inviteCode = code.uppercase(),
            inviterRole = role,
            inviterUserId = LocalFamilyAccounts.userIdOf(role),
            inviterDisplayName = LocalFamilyAccounts.displayNameOf(role),
            inviteeRole = role.other(),
            createdAt = timestamp,
            expiresAt = timestamp + invitationValidityMillis,
            status = InviteStatus.PENDING,
            relationId = relationId,
        )
        val relation = FamilyRelation(
            relationId = relationId,
            elderUserId = LocalFamilyAccounts.ELDER_USER_ID,
            familyUserId = LocalFamilyAccounts.FAMILY_USER_ID,
            elderDisplayName = LocalFamilyAccounts.ELDER_DISPLAY_NAME,
            familyDisplayName = LocalFamilyAccounts.FAMILY_DISPLAY_NAME,
            relationStatus = FamilyRelationStatus.PENDING,
            initiatedBy = role,
            createdAt = timestamp,
            confirmedAt = null,
            expiresAt = timestamp + invitationValidityMillis,
            revokedAt = null,
        )

        store.upsertRelation(relation)
        store.upsertInvite(invite)
        return Result.success(invite)
    }

    override suspend fun lookupInvite(code: String, role: InviteParty): InviteLookup {
        val normalized = normalizeCode(code)
        if (!isValidCode(normalized)) return InviteLookup.InvalidFormat
        val invite = store.currentInvites().firstOrNull { it.inviteCode.uppercase() == normalized }
            ?: return InviteLookup.NotFound
        return when (val status = invite.effectiveStatus(now())) {
            InviteStatus.PENDING -> if (invite.inviterRole == role) {
                InviteLookup.SelfInvite
            } else {
                InviteLookup.Found(invite)
            }

            InviteStatus.EXPIRED -> InviteLookup.Expired
            else -> InviteLookup.Used(status)
        }
    }

    override suspend fun acceptInvite(code: String, role: InviteParty): Result<FamilyRelation> {
        val lookup = lookupInvite(code, role)
        if (lookup !is InviteLookup.Found) {
            return failure(reasonOf(lookup), lookup.userMessage())
        }
        val invite = lookup.invite
        val timestamp = now()
        val hasOtherOpenRelation = store.currentRelations().any { relation ->
            relation.relationId != invite.relationId && relation.effectiveStatus(timestamp).let {
                it == FamilyRelationStatus.PENDING || it == FamilyRelationStatus.ACTIVE
            }
        }
        if (hasOtherOpenRelation) {
            return failure(
                FamilyFailureReason.ALREADY_BOUND,
                "当前已有其他绑定或等待确认的关系，请先解除后再接受新邀请",
            )
        }
        val relation = store.currentRelations()
            .firstOrNull { it.relationId == invite.relationId }
            ?: return failure(FamilyFailureReason.NOT_FOUND, "邀请对应的关系不存在，请重新生成")
        if (relation.effectiveStatus(timestamp) != FamilyRelationStatus.PENDING) {
            return failure(FamilyFailureReason.EXPIRED, "邀请已失效，不能接受")
        }

        val confirmed = relation.copy(
            relationStatus = FamilyRelationStatus.ACTIVE,
            confirmedAt = timestamp,
        )
        store.upsertRelation(confirmed)
        store.upsertInvite(
            invite.copy(
                status = InviteStatus.ACCEPTED,
                usedAt = timestamp,
            ),
        )
        return Result.success(confirmed)
    }

    override suspend fun rejectInvite(code: String, role: InviteParty): Result<Unit> {
        val lookup = lookupInvite(code, role)
        if (lookup !is InviteLookup.Found) {
            return failure(reasonOf(lookup), lookup.userMessage())
        }
        val invite = lookup.invite
        val timestamp = now()
        store.upsertInvite(invite.copy(status = InviteStatus.REJECTED, usedAt = timestamp))
        invite.relationId?.let { relationId ->
            store.currentRelations()
                .firstOrNull { it.relationId == relationId }
                ?.let { relation ->
                    store.upsertRelation(
                        relation.copy(
                            relationStatus = FamilyRelationStatus.REJECTED,
                            revokedAt = timestamp,
                        ),
                    )
                }
        }
        return Result.success(Unit)
    }

    override suspend fun cancelInvite(inviteId: String): Result<Unit> {
        val invite = store.currentInvites().firstOrNull { it.inviteId == inviteId }
            ?: return failure(FamilyFailureReason.NOT_FOUND, "邀请不存在")
        if (invite.effectiveStatus(now()) != InviteStatus.PENDING) {
            return failure(FamilyFailureReason.USED, "这条邀请已不能取消")
        }
        store.upsertInvite(invite.copy(status = InviteStatus.CANCELLED))
        invite.relationId?.let { relationId ->
            store.currentRelations()
                .firstOrNull { it.relationId == relationId }
                ?.takeIf { it.relationStatus == FamilyRelationStatus.PENDING }
                ?.let { store.deleteRelation(relationId) }
        }
        return Result.success(Unit)
    }

    override suspend fun revokeRelation(relationId: String, role: InviteParty): Result<Unit> {
        val relation = store.currentRelations().firstOrNull { it.relationId == relationId }
            ?: return failure(FamilyFailureReason.NOT_FOUND, "家庭关系不存在")
        val timestamp = now()
        if (relation.relationStatus != FamilyRelationStatus.REVOKED) {
            store.upsertRelation(
                relation.copy(
                    relationStatus = FamilyRelationStatus.REVOKED,
                    revokedAt = timestamp,
                ),
            )
        }
        // 解除绑定后立即停止共享：该关系下所有授权置为已撤销。
        store.currentConsents()
            .filter { it.relationId == relationId && it.revokedAt == null }
            .forEach { consent ->
                store.upsertConsent(
                    consent.copy(
                        revokedAt = timestamp,
                        consentStatus = ConsentStatus.REVOKED,
                        updatedAt = timestamp,
                    ),
                )
            }
        return Result.success(Unit)
    }

    private fun failure(reason: FamilyFailureReason, message: String): Result<Nothing> =
        Result.failure(FamilyOperationException(reason, message))

    private fun reasonOf(lookup: InviteLookup): FamilyFailureReason = when (lookup) {
        is InviteLookup.Found -> FamilyFailureReason.NOT_FOUND
        InviteLookup.NotFound -> FamilyFailureReason.NOT_FOUND
        InviteLookup.Expired -> FamilyFailureReason.EXPIRED
        is InviteLookup.Used -> FamilyFailureReason.USED
        InviteLookup.SelfInvite -> FamilyFailureReason.SELF_INVITE
        InviteLookup.InvalidFormat -> FamilyFailureReason.INVALID_FORMAT
    }

    companion object {
        const val INVITE_VALIDITY_MILLIS: Long = 24 * 60 * 60 * 1000L
        private const val MAX_CODE_ATTEMPTS = 50
        private const val CODE_LENGTH = 6
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** 生成 6 位、去除易混淆字符（I/O/0/1）的邀请码。 */
        fun randomInviteCode(): String = buildString(CODE_LENGTH) {
            repeat(CODE_LENGTH) {
                append(CODE_CHARS[Random.nextInt(CODE_CHARS.length)])
            }
        }

        fun normalizeCode(code: String): String = code.trim().uppercase()

        fun isValidCode(code: String): Boolean =
            code.length == CODE_LENGTH && code.all { it in CODE_CHARS }
    }
}
