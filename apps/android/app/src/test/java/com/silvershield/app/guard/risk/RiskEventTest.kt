package com.silvershield.app.guard.risk

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P06 风险事件模型单元测试。
 *
 * 覆盖：字段映射、信息不足判定、模拟数据标记、Fake 事件源轮换、双筛选条件。
 * Room 读写属于仪器级验证，由模拟器手工测试覆盖（见交付报告）。
 */
class RiskEventTest {

    private fun sampleEvent(
        eventId: String = "event-1",
        riskType: RiskType = RiskType.ImpersonateAuthority,
        riskLevel: RiskLevel = RiskLevel.High,
        clues: List<String> = listOf("线索一"),
        availableModalities: List<RiskModality> = listOf(RiskModality.ScreenText),
        missingModalities: List<RiskModality> = listOf(RiskModality.Audio),
        handleStatus: RiskHandleStatus = RiskHandleStatus.New,
        confidence: Float? = null,
        isMock: Boolean = true,
    ): RiskEvent = RiskEvent(
        eventId = eventId,
        occurredAt = 1_700_000_000_000L,
        riskType = riskType,
        riskLevel = riskLevel,
        title = "测试事件",
        description = "测试说明",
        clues = clues,
        evidenceSummary = "测试证据摘要",
        availableModalities = availableModalities,
        missingModalities = missingModalities,
        suggestedActions = listOf("建议一", "建议二"),
        modelVersion = "fake-risk-1.0",
        sourceType = RiskSourceType.Mock,
        syncStatus = RiskSyncStatus.LocalOnly,
        handleStatus = handleStatus,
        confidence = confidence,
        isMock = isMock,
    )

    @Test
    fun `entity round trip keeps all fields`() {
        val original = sampleEvent(handleStatus = RiskHandleStatus.NeedsVerification)
        val restored = original.toEntity().toDomain()

        assertEquals(original, restored)
    }

    @Test
    fun `unknown enum keys fall back to safe defaults`() {
        val entity = sampleEvent().toEntity().copy(
            riskTypeKey = "NotExist",
            riskLevelKey = "NotExist",
            sourceTypeKey = "NotExist",
            syncStatusKey = "NotExist",
            handleStatusKey = "NotExist",
        )
        val restored = entity.toDomain()

        assertEquals(RiskType.Unknown, restored.riskType)
        assertEquals(RiskLevel.Unknown, restored.riskLevel)
        assertEquals(RiskSourceType.Unknown, restored.sourceType)
        assertEquals(RiskSyncStatus.LocalOnly, restored.syncStatus)
        assertEquals(RiskHandleStatus.New, restored.handleStatus)
    }

    @Test
    fun `high level is urgent and other levels are not`() {
        assertTrue(sampleEvent(riskLevel = RiskLevel.High).isUrgent)
        assertFalse(sampleEvent(riskLevel = RiskLevel.Medium).isUrgent)
        assertFalse(sampleEvent(riskLevel = RiskLevel.Low).isUrgent)
        assertFalse(sampleEvent(riskLevel = RiskLevel.Unknown).isUrgent)
    }

    @Test
    fun `event without clues or modalities is insufficient`() {
        val insufficient = sampleEvent(
            riskLevel = RiskLevel.Unknown,
            clues = emptyList(),
            availableModalities = emptyList(),
        )
        assertFalse(insufficient.hasEnoughEvidence)
        assertTrue(insufficient.isInformationInsufficient)

        val unknownWithClues = sampleEvent(riskLevel = RiskLevel.Unknown)
        assertFalse(unknownWithClues.hasEnoughEvidence)

        val confidentWithoutClues = sampleEvent(
            riskLevel = RiskLevel.High,
            clues = emptyList(),
            availableModalities = emptyList(),
        )
        assertFalse(confidentWithoutClues.hasEnoughEvidence)
    }

    @Test
    fun `handle status is independent from risk judgement`() {
        val handled = sampleEvent().copy(handleStatus = RiskHandleStatus.Handled)

        assertEquals(RiskLevel.High, handled.riskLevel)
        assertEquals(RiskType.ImpersonateAuthority, handled.riskType)
        assertEquals(RiskHandleStatus.Handled, handled.handleStatus)
    }

    @Test
    fun `mock events are flagged and never claim confidence or safe content`() {
        val samples = MockRiskEvents.initial(nowMillis = 1_700_000_000_000L)

        assertEquals(3, samples.size)
        assertTrue(samples.all { it.isMock })
        assertTrue(samples.all { it.sourceType == RiskSourceType.Mock })
        assertTrue(samples.all { it.syncStatus == RiskSyncStatus.LocalOnly })
        assertTrue(samples.all { it.confidence == null })
        assertTrue(samples.all { it.modelVersion.isNotBlank() })
        assertFalse(samples.any { it.title.contains("安全") || it.description.contains("已确认安全") })
    }

    @Test
    fun `mock events cover normal insufficient and handled states`() {
        val samples = MockRiskEvents.initial(nowMillis = 1_700_000_000_000L)
        val insufficient = samples.first { it.eventId == MockRiskEvents.INSUFFICIENT_ID }
        val handled = samples.first { it.eventId == MockRiskEvents.HANDLED_ID }

        assertTrue(insufficient.isInformationInsufficient)
        assertNull(insufficient.evidenceSummary)
        assertTrue(insufficient.missingModalities.isNotEmpty())
        assertEquals(RiskHandleStatus.Handled, handled.handleStatus)
    }

    @Test
    fun `fake repository rotates generated events and marks them mock`() = runBlocking {
        val repository = FakeRiskEventRepository(nowMillis = { 1_700_000_000_000L })
        val initial = repository.initialEvents()
        val first = repository.next()
        val second = repository.next()

        assertEquals(3, initial.size)
        assertEquals(RiskType.FakeInvestment, first.riskType)
        assertEquals(RiskType.FakeCustomerService, second.riskType)
        assertTrue(first.isMock && second.isMock)
        assertTrue(first.eventId != second.eventId)
        assertEquals(1_700_000_000_000L, first.occurredAt)
    }

    @Test
    fun `level filter matches only its own level`() {
        val high = sampleEvent(riskLevel = RiskLevel.High)
        val medium = sampleEvent(riskLevel = RiskLevel.Medium)
        val unknown = sampleEvent(riskLevel = RiskLevel.Unknown)

        assertTrue(RiskLevelFilter.All.matches(high))
        assertTrue(RiskLevelFilter.All.matches(unknown))

        assertTrue(RiskLevelFilter.High.matches(high))
        assertFalse(RiskLevelFilter.High.matches(medium))
        assertTrue(RiskLevelFilter.Medium.matches(medium))
        assertTrue(RiskLevelFilter.Unknown.matches(unknown))
        assertFalse(RiskLevelFilter.Unknown.matches(high))
    }

    @Test
    fun `type filter supports all types and a single type`() {
        val investment = sampleEvent(riskType = RiskType.FakeInvestment)
        val authority = sampleEvent(riskType = RiskType.ImpersonateAuthority)

        assertTrue(investment.matchesType(null))
        assertTrue(investment.matchesType(RiskType.FakeInvestment))
        assertFalse(investment.matchesType(RiskType.ImpersonateAuthority))
        assertTrue(authority.matchesType(RiskType.ImpersonateAuthority))
    }
}
