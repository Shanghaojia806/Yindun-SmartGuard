package com.silvershield.app.family

import com.silvershield.app.guard.risk.RiskEvent

/**
 * P08 家属端共享风险事件 / 协同处理 / 消息中心模型。
 *
 * 权限边界：
 * - [SharedRiskEvent] 只是对 P06 [RiskEvent] 的授权摘要引用，不复制事件模型；
 * - 不包含原始屏幕录像、实时屏幕或完整浏览历史字段；
 * - 所有记录均带 `isMock = true`，代表本地演示数据。
 */

/** 分享记录状态。 */
enum class ShareStatus(val label: String) {
    ACTIVE("共享中"),
    REVOKED("已撤销"),
    EXPIRED("已失效"),
}

/**
 * 分享记录。
 *
 * [shareId] 由「授权 ID + 事件 ID」确定性生成，保证同一授权同一事件重复同步时不会
 * 产生多条记录，也不会丢失家属已读时间。
 */
data class SharedRiskEvent(
    val shareId: String,
    val eventId: String,
    val elderUserId: String,
    val familyUserId: String,
    val consentId: String,
    val sharedAt: Long,
    val shareStatus: ShareStatus,
    val readAt: Long?,
    val revokedAt: Long?,
    val isMock: Boolean = true,
) {
    /** 家属是否已经查看过。与协同处理状态、AI 风险等级相互独立。 */
    val isRead: Boolean get() = readAt != null
}

/** V1.3 规定的六种协同处理状态。 */
enum class HandlingStatus(val label: String) {
    PendingView("待查看"),
    Viewed("已查看"),
    Contacted("已联系"),
    Verifying("核实中"),
    Assisted("已协助处理"),
    Unverifiable("无法核实"),
    ;

    /** 仍需要家属继续跟进的状态。 */
    val isOpen: Boolean
        get() = this == PendingView || this == Viewed || this == Contacted || this == Verifying
}

/**
 * 协同处理记录。
 *
 * 采用追加式历史：每次状态或备注变化都插入新记录，[previousStatus] 记录变更前状态，
 * 旧记录不会被覆盖。AI 原始风险等级不在本模型中，家属无法改写。
 */
data class HandlingRecord(
    val handlingId: String,
    val shareId: String,
    val eventId: String,
    val handlerUserId: String,
    val handlingStatus: HandlingStatus,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val previousStatus: HandlingStatus? = null,
    val isMock: Boolean = true,
)

/** 消息类型。 */
enum class FamilyMessageType(val label: String) {
    RiskShared("新风险事件分享"),
    RiskRevoked("风险事件撤销"),
    RelationChanged("家庭关系变更"),
    ConsentChanged("授权状态变化"),
    HandlingChanged("协同处理状态变化"),
}

/** 消息投递状态：本地模拟，不代表真实推送结果。 */
enum class MessageDeliveryState(val label: String) {
    Delivered("已送达"),
    Delayed("延迟中"),
    Undelivered("离线未送达"),
}

/** 模拟网络状态，用于演示延迟与离线。 */
enum class FamilyNetworkMode(val label: String) {
    Online("正常同步"),
    Delayed("消息延迟"),
    Offline("离线"),
}

/** 本地模拟消息。 */
data class FamilyMessage(
    val messageId: String,
    val messageType: FamilyMessageType,
    val title: String,
    val content: String,
    val relatedEventId: String?,
    val relatedShareId: String?,
    val createdAt: Long,
    val readAt: Long?,
    val isRead: Boolean,
    val deliveryState: MessageDeliveryState,
    val isMock: Boolean = true,
)

/** 家属可见的共享事件（只读摘要）。 */
data class SharedEventView(
    val share: SharedRiskEvent,
    val event: RiskEvent,
)

/** 列表 / 首页使用的共享事件条目，附带当前协同状态。 */
data class FamilyEventItem(
    val share: SharedRiskEvent,
    val event: RiskEvent,
    val handlingStatus: HandlingStatus,
    val latestHandling: HandlingRecord?,
)

/** 访问被拒绝的具体原因，用于详情页显示授权失效说明。 */
enum class ShareDenyReason(val message: String) {
    NO_RELATION("尚未绑定家人，无法查看共享事件。请先在「我的」中完成绑定。"),
    RELATION_INACTIVE("家庭关系当前不是有效状态（可能仍在等待确认、已解除或已过期），无法继续查看。"),
    SHARE_NOT_FOUND("找不到这条共享记录，它可能已经失效。"),
    SHARE_REVOKED("老人已经撤销了这条风险事件的分享，详情不再可见。"),
    CONSENT_REVOKED("共享授权已被老人撤销，无法继续查看这条事件。"),
    CONSENT_EXPIRED("共享授权已过期，无法继续查看这条事件。"),
    CONSENT_MISSING("共享授权记录不存在，无法继续查看这条事件。"),
    OUT_OF_SCOPE("这条事件不在共享授权范围内，不能显示。"),
    EVENT_MISSING("原始风险事件已不存在，无法查看。"),
}

/** 访问校验结果。所有页面必须通过它判断能否展示事件。 */
sealed interface ShareAccess {
    data class Granted(val view: SharedEventView) : ShareAccess
    data class Denied(val reason: ShareDenyReason) : ShareAccess
}
