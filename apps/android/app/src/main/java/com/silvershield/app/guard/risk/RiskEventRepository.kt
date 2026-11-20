package com.silvershield.app.guard.risk

import kotlinx.coroutines.flow.Flow

/**
 * 风险事件本地记录仓库（P06）。
 *
 * 当前只有本地 Room 实现 [RoomRiskEventRepository]；后续真实 OCR / AI 推理接入时，
 * 由同一接口写入识别结果，页面与 ViewModel 不感知数据来源。
 *
 * 约束：
 * - [seedMockEvents] 与 [addMockEvent] 写入的事件一律 `isMock = true`；
 * - [updateHandleStatus] 只改用户处理状态，不修改 AI 判断结果；
 * - 不保存截图与原始屏幕内容。
 */
interface RiskEventRepository {

    /** 按发生时间倒序的本地记录流。 */
    val events: Flow<List<RiskEvent>>

    /** 首次进入记录页时写入示例事件；已写入过则不重复写入。 */
    suspend fun seedMockEvents()

    /** 手动生成一条模拟识别事件，用于验证"写入 → 列表 → 详情 → 处理状态"链路。 */
    suspend fun addMockEvent()

    /** 更新用户处理状态（与 AI 判断解耦）。 */
    suspend fun updateHandleStatus(eventId: String, status: RiskHandleStatus)

    /** 删除单条记录。 */
    suspend fun delete(eventId: String)

    /** 清空本地全部记录。 */
    suspend fun clear()
}

/** 记录页 · 风险等级筛选。 */
enum class RiskLevelFilter(val label: String) {
    All("全部"),
    High("高风险"),
    Medium("中风险"),
    Low("低风险"),
    Unknown("待评估"),
    ;

    fun matches(event: RiskEvent): Boolean = when (this) {
        All -> true
        High -> event.riskLevel == RiskLevel.High
        Medium -> event.riskLevel == RiskLevel.Medium
        Low -> event.riskLevel == RiskLevel.Low
        Unknown -> event.riskLevel == RiskLevel.Unknown
    }
}

/** 记录页 · 风险类型筛选；null 表示"全部类型"。 */
fun RiskEvent.matchesType(type: RiskType?): Boolean = type == null || riskType == type
