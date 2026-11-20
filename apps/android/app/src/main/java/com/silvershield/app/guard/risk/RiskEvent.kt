package com.silvershield.app.guard.risk

import java.util.UUID

/**
 * P06 统一风险事件模型。
 *
 * 设计约束：
 * - AI 判断结果（[riskLevel]、[clues]）与用户处理状态（[handleStatus]）分开保存，互不覆盖；
 * - [confidence] 只表示模型自身的判断置信度，且仅在模型给出依据时记录，禁止当作"诈骗概率"展示；
 * - [isMock] 为 true 的事件全部来自 [FakeRiskEventRepository]，界面必须显著标记；
 * - 不保存截图、不保存原始屏幕内容。
 */

/** 风险类型（可扩展；新增类型只需追加枚举，不影响历史数据读取）。 */
enum class RiskType(val label: String) {
    ImpersonateAuthority("冒充公检法"),
    FakeInvestment("虚假投资理财"),
    FakeCustomerService("假冒客服退款"),
    TaskRebate("刷单返利"),
    Unknown("暂无法判断"),
}

/** 风险等级。项目尚未确认正式等级标准，这里先定义内部可扩展枚举，后续可整体替换。 */
enum class RiskLevel(val label: String) {
    Low("低风险"),
    Medium("中风险"),
    High("高风险"),
    Unknown("待评估"),
}

/** 可用的数据模态（用于证据摘要区分"已获取 / 未获取"）。 */
enum class RiskModality(val label: String) {
    ScreenText("屏幕文字"),
    Audio("声音"),
    Other("其他模态"),
}

/** 事件来源；P06 只有模拟来源，真实识别引擎接入后追加。 */
enum class RiskSourceType(val label: String) {
    Mock("模拟数据"),
    ScreenCapture("屏幕采集"),
    Manual("手动录入"),
    Unknown("未知来源"),
}

/** 同步状态；P06 不做真实同步，全部为仅本机。 */
enum class RiskSyncStatus(val label: String) {
    LocalOnly("仅本机"),
    Pending("待同步"),
    Synced("已同步"),
}

/** 用户处理状态；与 AI 判断解耦，可独立修改。 */
enum class RiskHandleStatus(val label: String) {
    New("待查看"),
    NeedsVerification("待核实"),
    Handled("已处理"),
}

data class RiskEvent(
    /** 事件唯一标识。 */
    val eventId: String,
    /** 发生时间（毫秒时间戳）。 */
    val occurredAt: Long,
    val riskType: RiskType,
    val riskLevel: RiskLevel,
    val title: String,
    /** 风险说明，面向老人、不含专业术语。 */
    val description: String,
    val clues: List<String>,
    val evidenceSummary: String?,
    val availableModalities: List<RiskModality>,
    /** 尚未获取的证据模态；界面必须显示"未获取"，不得编造。 */
    val missingModalities: List<RiskModality>,
    val suggestedActions: List<String>,
    /** 生成该事件的模型版本；模拟事件固定标记。 */
    val modelVersion: String,
    val sourceType: RiskSourceType,
    val syncStatus: RiskSyncStatus,
    val handleStatus: RiskHandleStatus,
    /** 模型判断置信度（0..1），没有明确依据时为 null；不是诈骗概率。 */
    val confidence: Float?,
    val isMock: Boolean,
) {
    /** 是否需要重点提醒。 */
    val isUrgent: Boolean
        get() = riskLevel == RiskLevel.High

    /** 线索是否足以支撑结论；不足时必须展示"信息不足"，不得显示"已确认安全"。 */
    val hasEnoughEvidence: Boolean
        get() = riskLevel != RiskLevel.Unknown && (clues.isNotEmpty() || availableModalities.isNotEmpty())

    val isInformationInsufficient: Boolean
        get() = !hasEnoughEvidence
}

/**
 * 模拟事件素材。
 *
 * 所有事件 [RiskEvent.isMock] = true，[RiskEvent.sourceType] = Mock，
 * 界面上显示「模拟数据」，不可被当作真实识别结果。
 */
object MockRiskEvents {

    const val AUTHORITY_ID: String = "mock-authority"
    const val INSUFFICIENT_ID: String = "mock-insufficient"
    const val HANDLED_ID: String = "mock-handled"

    private const val MOCK_MODEL_VERSION = "fake-risk-1.0"

    /** 首次使用时写入的三条示例，覆盖正常风险 / 信息不足 / 已处理三种形态。 */
    fun initial(nowMillis: Long = System.currentTimeMillis()): List<RiskEvent> = listOf(
        RiskEvent(
            eventId = AUTHORITY_ID,
            occurredAt = nowMillis - 2 * 60 * 60 * 1000L,
            riskType = RiskType.ImpersonateAuthority,
            riskLevel = RiskLevel.High,
            title = "疑似冒充公检法",
            description = "画面中出现「你涉嫌洗钱，需要配合资金核查」等话术，并要求转账到所谓的“安全账户”。",
            clues = listOf(
                "自称公安、检察院或法院工作人员",
                "要求单独接听、不要告诉家人",
                "要求把钱转入所谓的“安全账户”",
                "要求开启屏幕共享或下载远程控制软件",
            ),
            evidenceSummary = "线索来自屏幕上的文字内容，原始画面不保存、不上传。",
            availableModalities = listOf(RiskModality.ScreenText),
            missingModalities = listOf(RiskModality.Audio, RiskModality.Other),
            suggestedActions = listOf(
                "立即挂断，不要按对方要求转账或提供验证码",
                "拨打 110 或到就近派出所当面核实",
                "把这件事告诉家人，请家人陪同处理",
            ),
            modelVersion = MOCK_MODEL_VERSION,
            sourceType = RiskSourceType.Mock,
            syncStatus = RiskSyncStatus.LocalOnly,
            handleStatus = RiskHandleStatus.New,
            confidence = null,
            isMock = true,
        ),
        RiskEvent(
            eventId = INSUFFICIENT_ID,
            occurredAt = nowMillis - 40 * 60 * 1000L,
            riskType = RiskType.Unknown,
            riskLevel = RiskLevel.Unknown,
            title = "信息不足，暂无法判断",
            description = "画面只出现模糊的转账输入界面，缺少可核对的文字内容，暂时无法确认是否存在风险。",
            clues = emptyList(),
            evidenceSummary = null,
            availableModalities = emptyList(),
            missingModalities = listOf(RiskModality.ScreenText, RiskModality.Audio, RiskModality.Other),
            suggestedActions = listOf(
                "先暂停操作，与家人确认收款方身份",
                "不要向陌生账户转账，不要提供验证码",
            ),
            modelVersion = MOCK_MODEL_VERSION,
            sourceType = RiskSourceType.Mock,
            syncStatus = RiskSyncStatus.LocalOnly,
            handleStatus = RiskHandleStatus.NeedsVerification,
            confidence = null,
            isMock = true,
        ),
        RiskEvent(
            eventId = HANDLED_ID,
            occurredAt = nowMillis - 26 * 60 * 60 * 1000L,
            riskType = RiskType.FakeCustomerService,
            riskLevel = RiskLevel.Medium,
            title = "疑似假冒客服退款",
            description = "有人以“订单退款”为由，要求添加好友并点击陌生链接。",
            clues = listOf(
                "要求添加陌生社交账号",
                "发送来路不明的退款链接",
            ),
            evidenceSummary = "线索来自屏幕上的聊天文字，未获取通话声音。",
            availableModalities = listOf(RiskModality.ScreenText),
            missingModalities = listOf(RiskModality.Audio),
            suggestedActions = listOf(
                "不要点击陌生链接",
                "通过购物平台的官方客服核实订单",
            ),
            modelVersion = MOCK_MODEL_VERSION,
            sourceType = RiskSourceType.Mock,
            syncStatus = RiskSyncStatus.LocalOnly,
            handleStatus = RiskHandleStatus.Handled,
            confidence = null,
            isMock = true,
        ),
    )

    /** 生成一条新的模拟事件，用于验证"写入 → 列表 → 详情 → 处理状态"链路。 */
    fun generated(sequence: Int, nowMillis: Long = System.currentTimeMillis()): RiskEvent {
        val variants = listOf(
            Triple(RiskType.FakeInvestment, RiskLevel.High, "疑似虚假投资理财"),
            Triple(RiskType.FakeCustomerService, RiskLevel.Medium, "疑似假冒客服退款"),
            Triple(RiskType.TaskRebate, RiskLevel.Medium, "疑似刷单返利"),
        )
        val (type, level, title) = variants[(sequence - 1).coerceAtLeast(0) % variants.size]
        return RiskEvent(
            eventId = "mock-${UUID.randomUUID()}",
            occurredAt = nowMillis,
            riskType = type,
            riskLevel = level,
            title = title,
            description = "这是模拟识别结果，用于验证风险事件的本机记录与详情展示，不是真实告警。",
            clues = listOf("出现诱导转账的话术", "要求添加陌生人的联系方式"),
            evidenceSummary = "模拟数据：没有采集或分析任何真实屏幕内容。",
            availableModalities = listOf(RiskModality.ScreenText),
            missingModalities = listOf(RiskModality.Audio, RiskModality.Other),
            suggestedActions = listOf("不要转账，先与家人核实", "有疑问拨打 110 咨询"),
            modelVersion = MOCK_MODEL_VERSION,
            sourceType = RiskSourceType.Mock,
            syncStatus = RiskSyncStatus.LocalOnly,
            handleStatus = RiskHandleStatus.New,
            confidence = null,
            isMock = true,
        )
    }
}
