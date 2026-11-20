package com.silvershield.app.family

import com.silvershield.app.guard.risk.RiskEvent
import com.silvershield.app.guard.risk.RiskHandleStatus
import com.silvershield.app.guard.risk.RiskLevel
import com.silvershield.app.guard.risk.RiskModality
import com.silvershield.app.guard.risk.RiskSourceType
import com.silvershield.app.guard.risk.RiskSyncStatus
import com.silvershield.app.guard.risk.RiskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P08 协同处理测试（JVM）。
 *
 * 覆盖：状态更新、备注、追加式历史、AI 原始判断不被覆盖、
 * 不能删除原始事件、授权撤销后拒绝写入。
 */
class HandlingRecordRepositoryTest {

    private lateinit var familyStore: InMemoryFamilyLocalStore
    private lateinit var riskStore: InMemoryFamilyRiskLocalStore
    private lateinit var familyRepository: FakeFamilyRepository
    private lateinit var consentRepository: FakeConsentRepository
    private lateinit var sharedRepository: FakeSharedRiskEventRepository
    private lateinit var handlingRepository: FakeHandlingRecordRepository
    private lateinit var events: MutableStateFlow<List<RiskEvent>>
    private var clock: Long = 2_000_000L
    private lateinit var shareId: String
    private lateinit var consentId: String

    @Before
    fun setUp() = runBlocking {
        familyStore = InMemoryFamilyLocalStore()
        riskStore = InMemoryFamilyRiskLocalStore()
        clock = 2_000_000L
        events = MutableStateFlow(listOf(riskEvent("event-1")))
        familyRepository = FakeFamilyRepository(store = familyStore, now = { clock })
        consentRepository = FakeConsentRepository(store = familyStore, now = { clock })
        sharedRepository = FakeSharedRiskEventRepository(
            riskStore = riskStore,
            familyStore = familyStore,
            riskEvents = events,
            now = { clock },
        )
        handlingRepository = FakeHandlingRecordRepository(
            riskStore = riskStore,
            sharedRiskRepository = sharedRepository,
            now = { clock },
        )

        val invite = familyRepository.generateInvite(InviteParty.Elder).getOrThrow()
        familyRepository.acceptInvite(invite.inviteCode, InviteParty.Family).getOrThrow()
        val consent = consentRepository.grantEventShare("event-1", null).getOrThrow()
        consentId = consent.consentId
        sharedRepository.sync()
        shareId = "share-${consent.consentId}-event-1"
    }

    @Test
    fun updateStatus_appendsFirstRecord() = runBlocking {
        val record = handlingRepository.updateStatus(
            shareId = shareId,
            status = HandlingStatus.Contacted,
            note = "已打电话，老人说对方要求转账",
        ).getOrThrow()

        assertEquals(HandlingStatus.Contacted, record.handlingStatus)
        assertEquals(HandlingStatus.PendingView, record.previousStatus)
        assertEquals("event-1", record.eventId)
        assertEquals(LocalFamilyAccounts.FAMILY_USER_ID, record.handlerUserId)
        assertEquals(1, riskStore.currentHandlingRecords().size)
    }

    @Test
    fun repeatedUpdates_keepFullHistory() = runBlocking {
        clock += 1_000L
        handlingRepository.updateStatus(shareId, HandlingStatus.Viewed, "已查看详情").getOrThrow()
        clock += 1_000L
        val second = handlingRepository.updateStatus(
            shareId,
            HandlingStatus.Verifying,
            "正在核实对方身份",
        ).getOrThrow()
        clock += 1_000L
        handlingRepository.updateStatus(shareId, HandlingStatus.Assisted, "已陪同老人挂断电话").getOrThrow()

        val history = riskStore.currentHandlingRecords().sortedBy { it.createdAt }

        assertEquals(3, history.size)
        assertEquals(HandlingStatus.Viewed, history[0].handlingStatus)
        assertEquals(HandlingStatus.Viewed, second.previousStatus)
        assertEquals(HandlingStatus.Assisted, history[2].handlingStatus)
        assertEquals(HandlingStatus.Verifying, history[2].previousStatus)
        // 旧记录内容不被覆盖
        assertEquals("已查看详情", history[0].note)
    }

    @Test
    fun noteIsTrimmed_andAttachedToEvent() = runBlocking {
        val record = handlingRepository.updateStatus(
            shareId = shareId,
            status = HandlingStatus.Contacted,
            note = "  已联系老人，确认没有转账  ",
        ).getOrThrow()

        assertEquals("已联系老人，确认没有转账", record.note)
        assertEquals("event-1", riskStore.currentHandlingRecords().single().eventId)
    }

    @Test
    fun aiRiskLevel_isNeverOverwritten() = runBlocking {
        val before = events.value.single()

        handlingRepository.updateStatus(shareId, HandlingStatus.Assisted, "已协助处理").getOrThrow()

        val after = events.value.single()
        assertEquals(before, after)
        assertEquals(RiskLevel.High, after.riskLevel)
        assertEquals(RiskHandleStatus.New, after.handleStatus)
    }

    @Test
    fun revokedConsent_blocksFurtherUpdates() = runBlocking {
        handlingRepository.updateStatus(shareId, HandlingStatus.Viewed, "已查看").getOrThrow()
        val recorded = riskStore.currentHandlingRecords().size

        consentRepository.revoke(consentId).getOrThrow()
        sharedRepository.sync()

        val result = handlingRepository.updateStatus(shareId, HandlingStatus.Assisted, "尝试越权更新")
        assertTrue(result.isFailure)
        assertEquals(recorded, riskStore.currentHandlingRecords().size)
        assertNotNull(riskStore.currentShares().single().revokedAt)
    }

    @Test
    fun unchangedStatusAndNote_isRejected() = runBlocking {
        handlingRepository.updateStatus(shareId, HandlingStatus.Contacted, "已联系").getOrThrow()
        clock += 1_000L

        val result = handlingRepository.updateStatus(shareId, HandlingStatus.Contacted, "已联系")

        assertTrue(result.isFailure)
        assertEquals(1, riskStore.currentHandlingRecords().size)
        // 计时并未因此写入新记录
        assertNotEquals(clock, riskStore.currentHandlingRecords().single().createdAt)
    }

    private fun riskEvent(eventId: String): RiskEvent = RiskEvent(
        eventId = eventId,
        occurredAt = clock - 60_000L,
        riskType = RiskType.ImpersonateAuthority,
        riskLevel = RiskLevel.High,
        title = "疑似冒充公检法",
        description = "测试用风险事件",
        clues = listOf("要求转账到安全账户"),
        evidenceSummary = "测试证据摘要",
        availableModalities = listOf(RiskModality.ScreenText),
        missingModalities = emptyList(),
        suggestedActions = listOf("联系家人"),
        modelVersion = "test-1.0",
        sourceType = RiskSourceType.Mock,
        syncStatus = RiskSyncStatus.LocalOnly,
        handleStatus = RiskHandleStatus.New,
        confidence = null,
        isMock = true,
    )
}
