package com.silvershield.app.family

import com.silvershield.app.guard.risk.RiskEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 家属端共享风险事件仓库（P08）。
 *
 * 本仓库是家属访问老人风险事件的唯一入口：
 * - 只有「有效家庭关系 + 有效共享授权 + 有效分享记录」三层同时成立才会返回事件；
 * - 页面不得自行根据菜单或本地状态判断权限；
 * - 详情路由直连也必须通过 [observeAccess] / [access]。
 *
 * 当前为本地模拟实现，不代表生产级对象级权限控制；真实跨设备访问仍需服务端校验。
 */
interface SharedRiskEventRepository {

    /** 全部分享记录（含已撤销，供审计与消息使用）。 */
    val shares: Flow<List<SharedRiskEvent>>

    /** 当前最新家庭关系（已换算有效期）。 */
    val relation: Flow<FamilyRelation?>

    /** 当前家庭关系下的有效授权。 */
    val activeConsents: Flow<List<ConsentRecord>>

    /** 依据 P07 授权记录物化分享记录；撤销/过期/解绑时同步失效。 */
    suspend fun sync()

    /** 家属当前实际可见的共享事件（三层校验后）。 */
    fun observeVisible(): Flow<List<SharedEventView>>

    /** 实时访问校验：授权被撤销后立即返回 Denied。 */
    fun observeAccess(shareId: String): Flow<ShareAccess>

    /** 一次性访问校验。 */
    suspend fun access(shareId: String): ShareAccess

    /** 打开详情后标记已查看；授权失效时拒绝。 */
    suspend fun markViewed(shareId: String): Result<Unit>
}

/**
 * 本地模拟共享事件仓库。
 *
 * [shareId] 由 `consentId + eventId` 确定性生成，同步幂等且可追溯到原始事件与授权记录。
 * 分享记录只保存授权摘要引用，不包含录像、实时屏幕或完整浏览历史。
 */
class FakeSharedRiskEventRepository(
    private val riskStore: FamilyRiskLocalStore,
    private val familyStore: FamilyLocalStore,
    private val riskEvents: Flow<List<RiskEvent>>,
    private val now: () -> Long = System::currentTimeMillis,
) : SharedRiskEventRepository {

    override val shares: Flow<List<SharedRiskEvent>> = riskStore.shares

    override val relation: Flow<FamilyRelation?> = familyStore.relations.map { relations ->
        val timestamp = now()
        relations.latestRelation()?.let { it.copy(relationStatus = it.effectiveStatus(timestamp)) }
    }

    override val activeConsents: Flow<List<ConsentRecord>> =
        combine(familyStore.relations, familyStore.consents) { relations, consents ->
            val timestamp = now()
            val active = relations.activeRelation(timestamp)
                ?: return@combine emptyList<ConsentRecord>()
            consents.filter {
                it.relationId == active.relationId && it.effectiveStatus(timestamp) == ConsentStatus.ACTIVE
            }
        }

    override suspend fun sync() {
        val timestamp = now()
        val relations = familyStore.currentRelations()
        val consents = familyStore.currentConsents()
        val events = riskEvents.first()
        val active = relations.activeRelation(timestamp)

        if (active != null) {
            val scopedConsents = consents.filter {
                it.relationId == active.relationId &&
                    it.effectiveStatus(timestamp) == ConsentStatus.ACTIVE
            }
            val existingShares = riskStore.currentShares().associateBy { it.shareId }
            events.forEach { event ->
                val consent = scopedConsents.firstOrNull { it.covers(event.eventId, event.riskType) }
                    ?: return@forEach
                val shareId = shareIdOf(consent.consentId, event.eventId)
                val existing = existingShares[shareId]
                if (existing == null) {
                    riskStore.upsertShare(
                        SharedRiskEvent(
                            shareId = shareId,
                            eventId = event.eventId,
                            elderUserId = active.elderUserId,
                            familyUserId = active.familyUserId,
                            consentId = consent.consentId,
                            sharedAt = consent.grantedAt,
                            shareStatus = ShareStatus.ACTIVE,
                            readAt = null,
                            revokedAt = null,
                        ),
                    )
                } else if (existing.shareStatus != ShareStatus.ACTIVE) {
                    riskStore.upsertShare(
                        existing.copy(shareStatus = ShareStatus.ACTIVE, revokedAt = null),
                    )
                }
            }
        }

        // 授权撤销 / 过期 / 解绑后，立即把分享记录标记为失效，保留历史供审计。
        riskStore.currentShares()
            .filter { it.shareStatus == ShareStatus.ACTIVE }
            .forEach { share ->
                val consent = consents.firstOrNull { it.consentId == share.consentId }
                val consentStatus = consent?.effectiveStatus(timestamp)
                val relationValid = active != null && consent?.relationId == active.relationId
                if (!relationValid || consentStatus != ConsentStatus.ACTIVE) {
                    riskStore.upsertShare(
                        share.copy(
                            shareStatus = if (consentStatus == ConsentStatus.EXPIRED) {
                                ShareStatus.EXPIRED
                            } else {
                                ShareStatus.REVOKED
                            },
                            revokedAt = share.revokedAt ?: timestamp,
                        ),
                    )
                }
            }
    }

    override fun observeVisible(): Flow<List<SharedEventView>> = combine(
        riskStore.shares,
        familyStore.relations,
        familyStore.consents,
        riskEvents,
    ) { shares, relations, consents, events ->
        val timestamp = now()
        val active = relations.activeRelation(timestamp)
            ?: return@combine emptyList<SharedEventView>()
        shares.mapNotNull { share ->
            val event = events.firstOrNull { it.eventId == share.eventId }
                ?: return@mapNotNull null
            if (!isVisible(share, event, active, consents, timestamp)) {
                null
            } else {
                SharedEventView(share, event)
            }
        }.sortedByDescending { it.share.sharedAt }
    }

    override fun observeAccess(shareId: String): Flow<ShareAccess> = combine(
        riskStore.shares,
        familyStore.relations,
        familyStore.consents,
        riskEvents,
    ) { shares, relations, consents, events ->
        evaluate(shareId, shares, relations, consents, events, now())
    }

    override suspend fun access(shareId: String): ShareAccess = observeAccess(shareId).first()

    override suspend fun markViewed(shareId: String): Result<Unit> {
        return when (val access = access(shareId)) {
            is ShareAccess.Denied -> Result.failure(
                FamilyOperationException(FamilyFailureReason.FORBIDDEN, access.reason.message),
            )

            is ShareAccess.Granted -> {
                val share = access.view.share
                if (share.readAt == null) {
                    riskStore.upsertShare(share.copy(readAt = now()))
                }
                Result.success(Unit)
            }
        }
    }

    private fun isVisible(
        share: SharedRiskEvent,
        event: RiskEvent,
        active: FamilyRelation,
        consents: List<ConsentRecord>,
        timestamp: Long,
    ): Boolean {
        if (share.shareStatus != ShareStatus.ACTIVE || share.revokedAt != null) return false
        if (share.elderUserId != active.elderUserId || share.familyUserId != active.familyUserId) return false
        val consent = consents.firstOrNull { it.consentId == share.consentId } ?: return false
        if (consent.relationId != active.relationId) return false
        if (consent.effectiveStatus(timestamp) != ConsentStatus.ACTIVE) return false
        return consent.covers(event.eventId, event.riskType)
    }

    private fun evaluate(
        shareId: String,
        shares: List<SharedRiskEvent>,
        relations: List<FamilyRelation>,
        consents: List<ConsentRecord>,
        events: List<RiskEvent>,
        timestamp: Long,
    ): ShareAccess {
        val share = shares.firstOrNull { it.shareId == shareId }
            ?: return ShareAccess.Denied(ShareDenyReason.SHARE_NOT_FOUND)
        val event = events.firstOrNull { it.eventId == share.eventId }
            ?: return ShareAccess.Denied(ShareDenyReason.EVENT_MISSING)
        val active = relations.activeRelation(timestamp)
            ?: return ShareAccess.Denied(
                if (relations.latestRelation() == null) {
                    ShareDenyReason.NO_RELATION
                } else {
                    ShareDenyReason.RELATION_INACTIVE
                },
            )
        if (share.elderUserId != active.elderUserId || share.familyUserId != active.familyUserId) {
            return ShareAccess.Denied(ShareDenyReason.RELATION_INACTIVE)
        }
        if (share.shareStatus != ShareStatus.ACTIVE || share.revokedAt != null) {
            return ShareAccess.Denied(ShareDenyReason.SHARE_REVOKED)
        }
        val consent = consents.firstOrNull { it.consentId == share.consentId }
            ?: return ShareAccess.Denied(ShareDenyReason.CONSENT_MISSING)
        return when (consent.effectiveStatus(timestamp)) {
            ConsentStatus.REVOKED -> ShareAccess.Denied(ShareDenyReason.CONSENT_REVOKED)
            ConsentStatus.EXPIRED -> ShareAccess.Denied(ShareDenyReason.CONSENT_EXPIRED)
            ConsentStatus.NOT_GRANTED -> ShareAccess.Denied(ShareDenyReason.CONSENT_MISSING)
            ConsentStatus.ACTIVE -> when {
                consent.relationId != active.relationId ->
                    ShareAccess.Denied(ShareDenyReason.RELATION_INACTIVE)

                !consent.covers(event.eventId, event.riskType) ->
                    ShareAccess.Denied(ShareDenyReason.OUT_OF_SCOPE)

                else -> ShareAccess.Granted(SharedEventView(share, event))
            }
        }
    }

    private fun shareIdOf(consentId: String, eventId: String): String = "share-$consentId-$eventId"
}
