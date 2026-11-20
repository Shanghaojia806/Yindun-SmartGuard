package com.silvershield.app.guard.risk

/**
 * 模拟事件来源（Fake）。
 *
 * P06 不接入真实 OCR / AI 推理，所有演示事件都由本类产生：
 * - 手工触发「模拟识别到一条风险」时调用 [next]；
 * - 首次使用时 [initialEvents] 提供三条固定示例。
 *
 * 后续接入真实引擎时，新增真实实现并让 [RiskEventRepository] 注入即可，
 * 页面与 ViewModel 不需要改动。
 */
interface RiskEventSource {
    /** 首次使用时写入的示例事件。 */
    fun initialEvents(): List<RiskEvent>

    /** 生成下一条新事件。 */
    suspend fun next(): RiskEvent
}

class FakeRiskEventRepository(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : RiskEventSource {

    private var sequence = 0

    override fun initialEvents(): List<RiskEvent> = MockRiskEvents.initial(nowMillis())

    override suspend fun next(): RiskEvent {
        sequence += 1
        return MockRiskEvents.generated(sequence, nowMillis())
    }
}
