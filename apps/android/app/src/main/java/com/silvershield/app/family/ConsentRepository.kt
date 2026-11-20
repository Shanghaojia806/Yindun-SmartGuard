package com.silvershield.app.family

import com.silvershield.app.guard.risk.RiskType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 风险共享授权仓库（P07）。
 *
 * 隐私边界：
 * - 家庭绑定不代表自动授权，本仓库不会在绑定时创建任何授权；
 * - 仅老人本人可创建 / 撤销授权（由调用方角色约束，家属端不暴露写入入口）；
 * - 授权范围只包含事件摘要，不包含原始录像、实时屏幕或完整浏览历史；
 * - 撤销与过期均为本地持久化状态，应用重启后不会恢复为有效。
 */
interface ConsentRepository {

    /** 全部授权记录（含已撤销、已过期，供老人查看历史）。 */
    val consents: Flow<List<ConsentRecord>>

    /** 把已过期授权标记为 EXPIRED 并持久化。 */
    suspend fun refreshExpiry()

    /** 按单条事件分享；[validityMillis] 为 null 表示长期有效（仍可撤销）。 */
    suspend fun grantEventShare(eventId: String, validityMillis: Long?): Result<ConsentRecord>

    /** 事先授权指定风险类型范围；默认不勾选任何类型。 */
    suspend fun grantScope(riskTypes: Set<RiskType>, validityMillis: Long?): Result<ConsentRecord>

    /** 撤销单条授权，立即生效。 */
    suspend fun revoke(consentId: String): Result<Unit>

    /** 撤销某条事件的单独分享记录（E07「撤销本次分享」）。 */
    suspend fun revokeEventShare(eventId: String): Result<Unit>

    /** 解除绑定后调用：使该关系下全部授权失效。 */
    suspend fun revokeForRelation(relationId: String): Result<Unit>

    /** 家属可见的有效授权（只读）。 */
    fun activeForFamily(consents: List<ConsentRecord>, now: Long): List<ConsentRecord>
}

/**
 * 本地模拟的授权仓库。
 *
 * 所有授权都建立在 ACTIVE 家庭关系之上；无绑定关系时拒绝任何授权。
 */
class FakeConsentRepository(
    private val store: FamilyLocalStore,
    private val now: () -> Long = System::currentTimeMillis,
) : ConsentRepository {

    override val consents: Flow<List<ConsentRecord>> = store.consents

    override suspend fun refreshExpiry() {
        val timestamp = now()
        store.currentConsents().forEach { consent ->
            if (consent.effectiveStatus(timestamp) == ConsentStatus.EXPIRED &&
                consent.consentStatus != ConsentStatus.EXPIRED
            ) {
                store.upsertConsent(
                    consent.copy(
                        consentStatus = ConsentStatus.EXPIRED,
                        updatedAt = timestamp,
                    ),
                )
            }
        }
    }

    override suspend fun grantEventShare(
        eventId: String,
        validityMillis: Long?,
    ): Result<ConsentRecord> {
        val timestamp = now()
        val relation = activeRelation(timestamp)
            ?: return failure(FamilyFailureReason.NO_RELATION, "请先与家人建立绑定关系，再分享风险事件")
        if (eventId.isBlank()) {
            return failure(FamilyFailureReason.NOT_FOUND, "风险事件不存在，无法分享")
        }

        val existing = store.currentConsents().firstOrNull {
            it.authorizationType == AuthorizationType.EventShare &&
                it.sourceEventId == eventId &&
                it.effectiveStatus(timestamp) == ConsentStatus.ACTIVE
        }
        if (existing != null) return Result.success(existing)

        val record = ConsentRecord(
            consentId = "consent-${UUID.randomUUID()}",
            elderUserId = relation.elderUserId,
            familyUserId = relation.familyUserId,
            relationId = relation.relationId,
            authorizationType = AuthorizationType.EventShare,
            scopeType = ConsentScopeType.EventIds,
            scopeValues = listOf(eventId),
            sourceEventId = eventId,
            grantedAt = timestamp,
            expiresAt = validityMillis?.let { timestamp + it },
            revokedAt = null,
            consentStatus = ConsentStatus.ACTIVE,
            updatedAt = timestamp,
            note = "老人主动分享单条风险事件摘要（本地模拟，未跨设备发送）",
        )
        store.upsertConsent(record)
        return Result.success(record)
    }

    override suspend fun grantScope(
        riskTypes: Set<RiskType>,
        validityMillis: Long?,
    ): Result<ConsentRecord> {
        val timestamp = now()
        val relation = activeRelation(timestamp)
            ?: return failure(FamilyFailureReason.NO_RELATION, "请先与家人建立绑定关系，再设置共享范围")
        if (riskTypes.isEmpty()) {
            return failure(FamilyFailureReason.INVALID_SCOPE, "请至少选择一种风险类型")
        }

        val allTypes = riskTypes.containsAll(RiskType.entries.toSet())
        val record = ConsentRecord(
            consentId = "consent-${UUID.randomUUID()}",
            elderUserId = relation.elderUserId,
            familyUserId = relation.familyUserId,
            relationId = relation.relationId,
            authorizationType = AuthorizationType.ScopeGrant,
            scopeType = if (allTypes) ConsentScopeType.AllTypes else ConsentScopeType.RiskTypes,
            scopeValues = if (allTypes) emptyList() else riskTypes.map { it.name }.sorted(),
            sourceEventId = null,
            grantedAt = timestamp,
            expiresAt = validityMillis?.let { timestamp + it },
            revokedAt = null,
            consentStatus = ConsentStatus.ACTIVE,
            updatedAt = timestamp,
            note = "老人主动授权风险事件范围（本地模拟，未跨设备发送）",
        )
        store.upsertConsent(record)
        return Result.success(record)
    }

    override suspend fun revoke(consentId: String): Result<Unit> {
        val record = store.currentConsents().firstOrNull { it.consentId == consentId }
            ?: return failure(FamilyFailureReason.NOT_FOUND, "授权记录不存在")
        if (record.revokedAt == null && record.consentStatus != ConsentStatus.REVOKED) {
            val timestamp = now()
            store.upsertConsent(
                record.copy(
                    revokedAt = timestamp,
                    consentStatus = ConsentStatus.REVOKED,
                    updatedAt = timestamp,
                ),
            )
        }
        return Result.success(Unit)
    }

    override suspend fun revokeEventShare(eventId: String): Result<Unit> {
        val timestamp = now()
        val records = store.currentConsents().filter {
            it.sourceEventId == eventId &&
                it.authorizationType == AuthorizationType.EventShare &&
                it.effectiveStatus(timestamp) == ConsentStatus.ACTIVE
        }
        if (records.isEmpty()) {
            return failure(FamilyFailureReason.NOT_FOUND, "这条事件目前没有单独的分享记录")
        }
        records.forEach { record ->
            store.upsertConsent(
                record.copy(
                    revokedAt = timestamp,
                    consentStatus = ConsentStatus.REVOKED,
                    updatedAt = timestamp,
                ),
            )
        }
        return Result.success(Unit)
    }

    override suspend fun revokeForRelation(relationId: String): Result<Unit> {        val timestamp = now()
        store.currentConsents()
            .filter { it.relationId == relationId && it.revokedAt == null }
            .forEach { record ->
                store.upsertConsent(
                    record.copy(
                        revokedAt = timestamp,
                        consentStatus = ConsentStatus.REVOKED,
                        updatedAt = timestamp,
                    ),
                )
            }
        return Result.success(Unit)
    }

    override fun activeForFamily(
        consents: List<ConsentRecord>,
        now: Long,
    ): List<ConsentRecord> = consents.filter { it.effectiveStatus(now) == ConsentStatus.ACTIVE }

    private suspend fun activeRelation(timestamp: Long): FamilyRelation? =
        store.currentRelations()
            .firstOrNull { it.effectiveStatus(timestamp) == FamilyRelationStatus.ACTIVE }

    private fun failure(reason: FamilyFailureReason, message: String): Result<Nothing> =
        Result.failure(FamilyOperationException(reason, message))
}
