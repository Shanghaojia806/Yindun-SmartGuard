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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P08 共享风险事件授权校验测试（JVM）。
 *
 * 覆盖：未分享不可见、分享后可见、未绑定拒绝、无授权拒绝、
 * 授权撤销即时失效、关系解除失效、详情路由直连不可绕过、范围外事件拒绝。
 */
class SharedRiskEventRepositoryTest {

    private lateinit var familyStore: InMemoryFamilyLocalStore
    private lateinit var riskStore: InMemoryFamilyRiskLocalStore
    private lateinit var familyRepository: FakeFamilyRepository
    private lateinit var consentRepository: FakeConsentRepository
    private lateinit var sharedRepository: FakeSharedRiskEventRepository
    private lateinit var events: MutableStateFlow<List<RiskEvent>>
    private var clock: Long = 2_000_000L

    @Before
    fun setUp() {
        familyStore = InMemoryFamilyLocalStore()
        riskStore = InMemoryFamilyRiskLocalStore()
        clock = 2_000_000L
        events = MutableStateFlow(
            listOf(
                riskEvent("event-1", RiskType.ImpersonateAuthority),
                riskEvent("event-2", RiskType.FakeInvestment),
            ),
        )
        familyRepository = FakeFamilyRepository(store = familyStore, now = { clock })
        consentRepository = FakeConsentRepository(store = familyStore, now = { clock })
        sharedRepository = FakeSharedRiskEventRepository(
            riskStore = riskStore,
            familyStore = familyStore,
            riskEvents = events,
            now = { clock },
        )
    }

    @Test
    fun unsharedEvent_isInvisibleToFamily() = runBlocking {
        bindFamily()

        sharedRepository.sync()

        assertTrue(riskStore.currentShares().isEmpty())
        assertTrue(sharedRepository.observeVisible().first().isEmpty())
    }

    @Test
    fun sharedEvent_becomesVisible_andTraceable() = runBlocking {
        bindFamily()
        val consent = consentRepository.grantEventShare("event-1", null).getOrThrow()

        sharedRepository.sync()
        val visible = sharedRepository.observeVisible().first()

        assertEquals(1, visible.size)
        assertEquals("event-1", visible.single().event.eventId)
        assertEquals(consent.consentId, visible.single().share.consentId)
        assertEquals(LocalFamilyAccounts.ELDER_USER_ID, visible.single().share.elderUserId)
        assertEquals(LocalFamilyAccounts.FAMILY_USER_ID, visible.single().share.familyUserId)
    }

    @Test
    fun unboundFamily_cannotAccessShare() = runBlocking {
        // 直接写入一条历史分享记录，模拟解绑后残留数据
        riskStore.upsertShare(staleShare("event-1", "consent-x"))

        val access = sharedRepository.access("share-consent-x-event-1")

        assertTrue(access is ShareAccess.Denied)
        assertEquals(ShareDenyReason.NO_RELATION, (access as ShareAccess.Denied).reason)
        assertTrue(sharedRepository.observeVisible().first().isEmpty())
    }

    @Test
    fun shareWithoutConsent_isDenied() = runBlocking {
        bindFamily()
        riskStore.upsertShare(staleShare("event-1", "consent-missing"))

        val access = sharedRepository.access("share-consent-missing-event-1")

        assertTrue(access is ShareAccess.Denied)
        assertEquals(ShareDenyReason.CONSENT_MISSING, (access as ShareAccess.Denied).reason)
    }

    @Test
    fun revokedConsent_takesEffectImmediately() = runBlocking {
        bindFamily()
        val consent = consentRepository.grantEventShare("event-1", null).getOrThrow()
        sharedRepository.sync()
        assertEquals(1, sharedRepository.observeVisible().first().size)

        consentRepository.revoke(consent.consentId).getOrThrow()

        assertTrue(sharedRepository.observeVisible().first().isEmpty())
        val access = sharedRepository.access("share-${consent.consentId}-event-1")
        assertEquals(ShareDenyReason.CONSENT_REVOKED, (access as ShareAccess.Denied).reason)
    }

    @Test
    fun revokedRelation_stopsSharing() = runBlocking {
        val relation = bindFamily()
        val consent = consentRepository.grantEventShare("event-1", null).getOrThrow()
        sharedRepository.sync()
        assertEquals(1, sharedRepository.observeVisible().first().size)

        familyRepository.revokeRelation(relation.relationId, InviteParty.Elder).getOrThrow()
        sharedRepository.sync()

        assertTrue(sharedRepository.observeVisible().first().isEmpty())
        val access = sharedRepository.access("share-${consent.consentId}-event-1")
        assertEquals(ShareDenyReason.RELATION_INACTIVE, (access as ShareAccess.Denied).reason)
        assertTrue(riskStore.currentShares().all { it.shareStatus != ShareStatus.ACTIVE })
    }

    @Test
    fun directDetailRoute_cannotBypassCheck() = runBlocking {
        bindFamily()
        val consent = consentRepository.grantEventShare("event-1", null).getOrThrow()
        sharedRepository.sync()

        // 未知 shareId 直连详情
        val unknown = sharedRepository.access("share-does-not-exist")
        assertEquals(ShareDenyReason.SHARE_NOT_FOUND, (unknown as ShareAccess.Denied).reason)

        // 授权撤销后同一直连地址被拒绝，且不能写入已读
        consentRepository.revoke(consent.consentId).getOrThrow()
        val revoked = sharedRepository.access("share-${consent.consentId}-event-1")
        assertTrue(revoked is ShareAccess.Denied)
        assertTrue(sharedRepository.markViewed("share-${consent.consentId}-event-1").isFailure)
    }

    @Test
    fun outOfScopeEvent_isDenied() = runBlocking {
        bindFamily()
        val consent = consentRepository.grantEventShare("event-1", null).getOrThrow()
        // 复用仅覆盖 event-1 的授权，挂上 event-2 的分享记录
        riskStore.upsertShare(staleShare("event-2", consent.consentId))

        val access = sharedRepository.access("share-${consent.consentId}-event-2")

        assertEquals(ShareDenyReason.OUT_OF_SCOPE, (access as ShareAccess.Denied).reason)
        assertTrue(sharedRepository.observeVisible().first().isEmpty())
    }

    @Test
    fun markViewed_recordsReadTime_once() = runBlocking {
        bindFamily()
        val consent = consentRepository.grantEventShare("event-1", null).getOrThrow()
        sharedRepository.sync()
        val shareId = "share-${consent.consentId}-event-1"

        assertTrue(sharedRepository.markViewed(shareId).isSuccess)
        val readAt = riskStore.currentShares().single().readAt
        assertNotNull(readAt)

        clock += 10_000L
        sharedRepository.markViewed(shareId)
        assertEquals(readAt, riskStore.currentShares().single().readAt)
    }

    private suspend fun bindFamily(): FamilyRelation {
        val invite = familyRepository.generateInvite(InviteParty.Elder).getOrThrow()
        return familyRepository.acceptInvite(invite.inviteCode, InviteParty.Family).getOrThrow()
    }

    private fun staleShare(eventId: String, consentId: String): SharedRiskEvent = SharedRiskEvent(
        shareId = "share-$consentId-$eventId",
        eventId = eventId,
        elderUserId = LocalFamilyAccounts.ELDER_USER_ID,
        familyUserId = LocalFamilyAccounts.FAMILY_USER_ID,
        consentId = consentId,
        sharedAt = clock,
        shareStatus = ShareStatus.ACTIVE,
        readAt = null,
        revokedAt = null,
    )

    private fun riskEvent(eventId: String, riskType: RiskType): RiskEvent = RiskEvent(
        eventId = eventId,
        occurredAt = clock - 60_000L,
        riskType = riskType,
        riskLevel = RiskLevel.High,
        title = "疑似${riskType.label}",
        description = "测试用风险事件",
        clues = listOf("测试线索"),
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
