package com.silvershield.app.family

import com.silvershield.app.guard.risk.RiskEvent
import com.silvershield.app.guard.risk.RiskHandleStatus
import com.silvershield.app.guard.risk.RiskLevel
import com.silvershield.app.guard.risk.RiskModality
import com.silvershield.app.guard.risk.RiskSourceType
import com.silvershield.app.guard.risk.RiskSyncStatus
import com.silvershield.app.guard.risk.RiskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P08 消息中心测试（JVM）。
 *
 * 覆盖：分享生成消息、未读数量、单条/全部已读、消息跳转关联、
 * 撤销后消息状态、延迟与离线投递状态、已读持久化。
 */
class FamilyMessageRepositoryTest {

    private lateinit var familyStore: InMemoryFamilyLocalStore
    private lateinit var riskStore: InMemoryFamilyRiskLocalStore
    private lateinit var familyRepository: FakeFamilyRepository
    private lateinit var consentRepository: FakeConsentRepository
    private lateinit var sharedRepository: FakeSharedRiskEventRepository
    private lateinit var messageRepository: FakeFamilyMessageRepository
    private lateinit var events: MutableStateFlow<List<RiskEvent>>
    private var clock: Long = 2_000_000L

    @Before
    fun setUp(): Unit = runBlocking {
        familyStore = InMemoryFamilyLocalStore()
        riskStore = InMemoryFamilyRiskLocalStore()
        clock = 2_000_000L
        events = MutableStateFlow(listOf(riskEvent("event-1"), riskEvent("event-2")))
        familyRepository = FakeFamilyRepository(store = familyStore, now = { clock })
        consentRepository = FakeConsentRepository(store = familyStore, now = { clock })
        sharedRepository = FakeSharedRiskEventRepository(
            riskStore = riskStore,
            familyStore = familyStore,
            riskEvents = events,
            now = { clock },
        )
        messageRepository = FakeFamilyMessageRepository(
            riskStore = riskStore,
            familyStore = familyStore,
            riskEvents = events,
            now = { clock },
        )
        val invite = familyRepository.generateInvite(InviteParty.Elder).getOrThrow()
        familyRepository.acceptInvite(invite.inviteCode, InviteParty.Family).getOrThrow()
    }

    @Test
    fun newShare_createsUnreadMessage_linkedToEvent() = runBlocking {
        val consent = consentRepository.grantEventShare("event-1", null).getOrThrow()
        sharedRepository.sync()
        messageRepository.refresh()

        val message = messages().single { it.messageType == FamilyMessageType.RiskShared }
        assertEquals("event-1", message.relatedEventId)
        assertEquals("share-${consent.consentId}-event-1", message.relatedShareId)
        assertFalse(message.isRead)
        assertNull(message.readAt)
        assertEquals(MessageDeliveryState.Delivered, message.deliveryState)
        assertTrue(message.isMock)

        messageRepository.refresh()
        assertEquals(1, messages().count { it.messageType == FamilyMessageType.RiskShared })
    }

    @Test
    fun markRead_updatesSingleMessage_andSurvivesRestart() = runBlocking {
        consentRepository.grantEventShare("event-1", null).getOrThrow()
        sharedRepository.sync()
        messageRepository.refresh()
        val target = messages().single { it.messageType == FamilyMessageType.RiskShared }

        clock += 5_000L
        messageRepository.markRead(target.messageId)

        val updated = messages().single { it.messageId == target.messageId }
        assertTrue(updated.isRead)
        assertEquals(clock, updated.readAt)

        val restarted = FakeFamilyMessageRepository(
            riskStore = riskStore,
            familyStore = familyStore,
            riskEvents = events,
            now = { clock },
        )
        restarted.refresh()
        val reloaded = restarted.messages.first().single { it.messageId == target.messageId }
        assertTrue(reloaded.isRead)
        assertEquals(clock, reloaded.readAt)
    }

    @Test
    fun markAllRead_clearsUnreadCount() = runBlocking {
        consentRepository.grantEventShare("event-1", null).getOrThrow()
        consentRepository.grantEventShare("event-2", null).getOrThrow()
        sharedRepository.sync()
        messageRepository.refresh()
        assertTrue(messages().any { !it.isRead })

        messageRepository.markAllRead()

        assertTrue(messages().none { !it.isRead })
        assertTrue(messages().all { it.readAt != null })
    }

    @Test
    fun revokedShare_createsRevocationMessage() = runBlocking {
        val consent = consentRepository.grantEventShare("event-1", null).getOrThrow()
        sharedRepository.sync()
        messageRepository.refresh()

        consentRepository.revoke(consent.consentId).getOrThrow()
        sharedRepository.sync()
        messageRepository.refresh()

        val revoked = messages().single { it.messageType == FamilyMessageType.RiskRevoked }
        assertEquals("event-1", revoked.relatedEventId)
        assertNotNull(revoked.content)
        assertEquals("share-${consent.consentId}-event-1", revoked.relatedShareId)
    }

    @Test
    fun offlineAndDelayed_modes_areReflectedOnNewMessages() = runBlocking {
        messageRepository.setNetworkMode(FamilyNetworkMode.Offline)
        consentRepository.grantEventShare("event-1", null).getOrThrow()
        sharedRepository.sync()
        messageRepository.refresh()

        assertEquals(
            MessageDeliveryState.Undelivered,
            messages().single { it.relatedEventId == "event-1" && it.messageType == FamilyMessageType.RiskShared }
                .deliveryState,
        )

        messageRepository.setNetworkMode(FamilyNetworkMode.Delayed)
        consentRepository.grantEventShare("event-2", null).getOrThrow()
        sharedRepository.sync()
        messageRepository.refresh()

        assertEquals(
            MessageDeliveryState.Delayed,
            messages().single { it.relatedEventId == "event-2" && it.messageType == FamilyMessageType.RiskShared }
                .deliveryState,
        )

        // 恢复在线后，此前未送达/延迟的消息才标记为已送达
        messageRepository.setNetworkMode(FamilyNetworkMode.Online)
        assertTrue(messages().all { it.deliveryState == MessageDeliveryState.Delivered })
    }

    private suspend fun messages(): List<FamilyMessage> = messageRepository.messages.first()

    private fun riskEvent(eventId: String): RiskEvent = RiskEvent(
        eventId = eventId,
        occurredAt = clock - 60_000L,
        riskType = RiskType.FakeInvestment,
        riskLevel = RiskLevel.Medium,
        title = "疑似虚假投资理财",
        description = "测试用风险事件",
        clues = listOf("承诺高额回报"),
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
