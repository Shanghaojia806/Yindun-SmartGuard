package com.silvershield.app.family

import com.silvershield.app.guard.risk.RiskEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * 家属端消息中心仓库（P08）。
 *
 * 本轮消息完全由本地状态派生，**不是真实推送**：
 * - 支持「正常同步 / 消息延迟 / 离线」三种模拟网络状态；
 * - 离线或延迟时新消息不会显示为「已送达」；
 * - 已读状态持久化，刷新不会重置。
 */
interface FamilyMessageRepository {

    val messages: Flow<List<FamilyMessage>>

    /** 当前模拟网络状态。 */
    val networkMode: StateFlow<FamilyNetworkMode>

    /** 切换模拟网络状态；切回正常同步时把未送达消息标记为已送达。 */
    suspend fun setNetworkMode(mode: FamilyNetworkMode)

    /** 从分享、关系、授权、处理记录派生消息（确定性 ID，幂等）。 */
    suspend fun refresh()

    suspend fun markRead(messageId: String)

    suspend fun markAllRead()
}

/**
 * 本地模拟消息仓库。
 *
 * 消息 ID 由业务对象确定性生成，重复刷新不会产生重复消息，也不会覆盖已读时间。
 */
class FakeFamilyMessageRepository(
    private val riskStore: FamilyRiskLocalStore,
    private val familyStore: FamilyLocalStore,
    private val riskEvents: Flow<List<RiskEvent>>,
    private val now: () -> Long = System::currentTimeMillis,
) : FamilyMessageRepository {

    private val modeState = MutableStateFlow(FamilyNetworkMode.Online)

    override val messages: Flow<List<FamilyMessage>> = riskStore.messages
    override val networkMode: StateFlow<FamilyNetworkMode> = modeState.asStateFlow()

    override suspend fun setNetworkMode(mode: FamilyNetworkMode) {
        modeState.value = mode
        if (mode == FamilyNetworkMode.Online) {
            riskStore.currentMessages()
                .filter { it.deliveryState != MessageDeliveryState.Delivered }
                .forEach { message ->
                    riskStore.upsertMessage(message.copy(deliveryState = MessageDeliveryState.Delivered))
                }
        }
    }

    override suspend fun refresh() {
        val timestamp = now()
        val events = riskEvents.first()
        val shares = riskStore.currentShares()
        val relations = familyStore.currentRelations()
        val consents = familyStore.currentConsents()
        val handlingRecords = riskStore.currentHandlingRecords()
        val existing = riskStore.currentMessages().associateBy { it.messageId }

        suspend fun ensure(
            messageId: String,
            type: FamilyMessageType,
            title: String,
            content: String,
            createdAt: Long,
            eventId: String? = null,
            shareId: String? = null,
        ) {
            if (existing.containsKey(messageId)) return
            riskStore.upsertMessage(
                FamilyMessage(
                    messageId = messageId,
                    messageType = type,
                    title = title,
                    content = content,
                    relatedEventId = eventId,
                    relatedShareId = shareId,
                    createdAt = createdAt,
                    readAt = null,
                    isRead = false,
                    deliveryState = deliveryStateFor(modeState.value),
                ),
            )
        }

        shares.forEach { share ->
            val eventTitle = events.firstOrNull { it.eventId == share.eventId }?.title
                ?: "一条风险事件"
            if (share.shareStatus == ShareStatus.ACTIVE) {
                ensure(
                    messageId = "msg-share-${share.shareId}",
                    type = FamilyMessageType.RiskShared,
                    title = "收到新的风险事件分享",
                    content = "老人向你分享了「$eventTitle」，请查看详情并决定如何协助。",
                    createdAt = share.sharedAt,
                    eventId = share.eventId,
                    shareId = share.shareId,
                )
            } else {
                ensure(
                    messageId = "msg-revoked-${share.shareId}",
                    type = FamilyMessageType.RiskRevoked,
                    title = "风险事件分享已失效",
                    content = "「$eventTitle」的分享已被撤销或已过期，相关详情不再可见。",
                    createdAt = share.revokedAt ?: timestamp,
                    eventId = share.eventId,
                    shareId = share.shareId,
                )
            }
        }

        relations.latestRelation()?.let { relation ->
            val status = relation.effectiveStatus(timestamp)
            if (relation.confirmedAt != null && status == FamilyRelationStatus.ACTIVE) {
                ensure(
                    messageId = "msg-relation-active-${relation.relationId}",
                    type = FamilyMessageType.RelationChanged,
                    title = "家庭关系已建立",
                    content = "你与${relation.familyDisplayName}已完成双向确认。家庭绑定不代表自动授权，共享范围仍需老人单独设置。",
                    createdAt = relation.confirmedAt,
                )
            }
            if (status == FamilyRelationStatus.REVOKED) {
                ensure(
                    messageId = "msg-relation-revoked-${relation.relationId}",
                    type = FamilyMessageType.RelationChanged,
                    title = "家庭关系已解除",
                    content = "家庭关系已解除，相关风险共享授权同时失效。",
                    createdAt = relation.revokedAt ?: timestamp,
                )
            }
        }

        consents.filter { it.authorizationType == AuthorizationType.ScopeGrant }.forEach { consent ->
            when (consent.effectiveStatus(timestamp)) {
                ConsentStatus.ACTIVE -> ensure(
                    messageId = "msg-consent-granted-${consent.consentId}",
                    type = FamilyMessageType.ConsentChanged,
                    title = "收到新的共享授权",
                    content = "老人授权你查看：${consent.scopeDescription()}。该授权可随时撤销。",
                    createdAt = consent.grantedAt,
                )

                ConsentStatus.REVOKED -> ensure(
                    messageId = "msg-consent-revoked-${consent.consentId}",
                    type = FamilyMessageType.ConsentChanged,
                    title = "共享授权已撤销",
                    content = "老人撤销了一项共享授权：${consent.scopeDescription()}。",
                    createdAt = consent.revokedAt ?: timestamp,
                )

                ConsentStatus.EXPIRED -> ensure(
                    messageId = "msg-consent-expired-${consent.consentId}",
                    type = FamilyMessageType.ConsentChanged,
                    title = "共享授权已过期",
                    content = "一项共享授权已到期：${consent.scopeDescription()}。",
                    createdAt = consent.expiresAt ?: timestamp,
                )

                ConsentStatus.NOT_GRANTED -> Unit
            }
        }

        handlingRecords.forEach { record ->
            ensure(
                messageId = "msg-handling-${record.handlingId}",
                type = FamilyMessageType.HandlingChanged,
                title = "协同处理状态已更新",
                content = buildString {
                    append("你将处理状态更新为「${record.handlingStatus.label}」")
                    if (record.note.isNotBlank()) {
                        append("，备注：${record.note}")
                    }
                },
                createdAt = record.createdAt,
                eventId = record.eventId,
                shareId = record.shareId,
            )
        }
    }

    override suspend fun markRead(messageId: String) {
        val message = riskStore.currentMessages().firstOrNull { it.messageId == messageId } ?: return
        if (message.isRead) return
        val timestamp = now()
        riskStore.upsertMessage(message.copy(readAt = timestamp, isRead = true))
    }

    override suspend fun markAllRead() {
        val timestamp = now()
        riskStore.currentMessages()
            .filter { !it.isRead }
            .forEach { message ->
                riskStore.upsertMessage(message.copy(readAt = timestamp, isRead = true))
            }
    }

    private fun deliveryStateFor(mode: FamilyNetworkMode): MessageDeliveryState = when (mode) {
        FamilyNetworkMode.Online -> MessageDeliveryState.Delivered
        FamilyNetworkMode.Delayed -> MessageDeliveryState.Delayed
        FamilyNetworkMode.Offline -> MessageDeliveryState.Undelivered
    }
}
