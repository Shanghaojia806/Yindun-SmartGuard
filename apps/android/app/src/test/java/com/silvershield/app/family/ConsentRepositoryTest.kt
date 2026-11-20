package com.silvershield.app.family

import com.silvershield.app.guard.risk.RiskType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P07 共享授权业务规则测试（JVM）。
 *
 * 覆盖：默认未授权、按事件授权、范围授权、只读边界、撤销、过期、解绑失效、重启恢复。
 */
class ConsentRepositoryTest {

    private lateinit var store: InMemoryFamilyLocalStore
    private lateinit var familyRepository: FakeFamilyRepository
    private lateinit var consentRepository: FakeConsentRepository
    private var clock: Long = 2_000_000L

    @Before
    fun setUp() {
        store = InMemoryFamilyLocalStore()
        clock = 2_000_000L
        familyRepository = FakeFamilyRepository(store = store, now = { clock })
        consentRepository = FakeConsentRepository(store = store, now = { clock })
    }

    @Test
    fun defaultState_hasNoConsent() = runBlocking {
        bindFamily()

        assertTrue(store.currentConsents().isEmpty())
        val eventStatus = store.currentConsents().eventConsentStatus("event-1", RiskType.FakeInvestment, clock)
        assertEquals(ConsentStatus.NOT_GRANTED, eventStatus)
    }

    @Test
    fun grantEventShare_withoutRelation_isRejected() = runBlocking {
        val result = consentRepository.grantEventShare("event-1", null)

        assertTrue(result.isFailure)
        assertTrue(store.currentConsents().isEmpty())
    }

    @Test
    fun grantEventShare_coversOnlyThatEvent() = runBlocking {
        bindFamily()

        consentRepository.grantEventShare("event-1", null).getOrThrow()

        assertEquals(
            ConsentStatus.ACTIVE,
            store.currentConsents().eventConsentStatus("event-1", RiskType.FakeInvestment, clock),
        )
        assertEquals(
            ConsentStatus.NOT_GRANTED,
            store.currentConsents().eventConsentStatus("event-2", RiskType.FakeInvestment, clock),
        )
    }

    @Test
    fun grantEventShare_isIdempotentForSameEvent() = runBlocking {
        bindFamily()

        val first = consentRepository.grantEventShare("event-1", null).getOrThrow()
        val second = consentRepository.grantEventShare("event-1", null).getOrThrow()

        assertEquals(first.consentId, second.consentId)
        assertEquals(1, store.currentConsents().size)
    }

    @Test
    fun grantScope_coversSelectedRiskTypesOnly() = runBlocking {
        bindFamily()

        consentRepository.grantScope(setOf(RiskType.FakeInvestment), null).getOrThrow()

        val consents = store.currentConsents()
        assertEquals(
            ConsentStatus.ACTIVE,
            consents.eventConsentStatus("any-event", RiskType.FakeInvestment, clock),
        )
        assertEquals(
            ConsentStatus.NOT_GRANTED,
            consents.eventConsentStatus("any-event", RiskType.TaskRebate, clock),
        )
    }

    @Test
    fun grantScope_withEmptySelection_isRejected() = runBlocking {
        bindFamily()

        val result = consentRepository.grantScope(emptySet(), null)

        assertTrue(result.isFailure)
        assertTrue(store.currentConsents().isEmpty())
    }

    @Test
    fun revoke_takesEffectImmediately_andSurvivesRestart() = runBlocking {
        bindFamily()
        val consent = consentRepository.grantEventShare("event-1", null).getOrThrow()

        consentRepository.revoke(consent.consentId).getOrThrow()

        assertEquals(ConsentStatus.REVOKED, store.currentConsents().single().consentStatus)
        assertNotNull(store.currentConsents().single().revokedAt)

        // 重启后仍为撤销，不会恢复有效
        val restarted = FakeConsentRepository(store, now = { clock })
        restarted.refreshExpiry()
        assertEquals(ConsentStatus.REVOKED, store.currentConsents().single().effectiveStatus(clock))
    }

    @Test
    fun consent_expiresAfterValidity_andStaysExpired() = runBlocking {
        bindFamily()
        val consent = consentRepository.grantEventShare("event-1", 60_000L).getOrThrow()
        assertEquals(ConsentStatus.ACTIVE, consent.effectiveStatus(clock))

        clock += 60_001L
        consentRepository.refreshExpiry()

        assertEquals(ConsentStatus.EXPIRED, store.currentConsents().single().consentStatus)
        val restarted = FakeConsentRepository(store, now = { clock })
        assertTrue(restarted.activeForFamily(store.currentConsents(), clock).isEmpty())
        assertEquals(ConsentStatus.EXPIRED, store.currentConsents().single().effectiveStatus(clock))
    }

    @Test
    fun revokeRelation_invalidatesAllConsents() = runBlocking {
        val relation = bindFamily()
        consentRepository.grantEventShare("event-1", null).getOrThrow()
        consentRepository.grantScope(setOf(RiskType.FakeInvestment), null).getOrThrow()

        familyRepository.revokeRelation(relation.relationId, InviteParty.Elder).getOrThrow()

        assertEquals(2, store.currentConsents().size)
        assertTrue(store.currentConsents().all { it.consentStatus == ConsentStatus.REVOKED })
        assertTrue(consentRepository.activeForFamily(store.currentConsents(), clock).isEmpty())
    }

    @Test
    fun revokeEventShare_onlyRevokesTargetEvent() = runBlocking {
        bindFamily()
        consentRepository.grantEventShare("event-1", null).getOrThrow()
        consentRepository.grantEventShare("event-2", null).getOrThrow()

        consentRepository.revokeEventShare("event-1").getOrThrow()

        val consents = store.currentConsents()
        assertEquals(ConsentStatus.REVOKED, consents.single { it.sourceEventId == "event-1" }.consentStatus)
        assertEquals(ConsentStatus.ACTIVE, consents.single { it.sourceEventId == "event-2" }.consentStatus)
    }

    @Test
    fun familyViewModel_exposesNoWriteApi() {
        // 家属端只读：编译期即可确认 ConsentViewModel 没有授权写入方法。
        val forbidden = setOf("grantEventShare", "grantScope", "revoke", "revokeForRelation")
        val declared = ConsentViewModel::class.java.declaredMethods.map { it.name }.toSet()

        assertTrue(declared.none { it in forbidden })
    }

    @Test
    fun revokedConsent_isNotVisibleAsActiveForFamily() = runBlocking {
        bindFamily()
        val consent = consentRepository.grantEventShare("event-1", null).getOrThrow()
        consentRepository.revoke(consent.consentId).getOrThrow()

        assertTrue(consentRepository.activeForFamily(store.currentConsents(), clock).isEmpty())
        assertFalse(store.currentConsents().single().consentStatus == ConsentStatus.ACTIVE)
    }

    private suspend fun bindFamily(): FamilyRelation {
        val invite = familyRepository.generateInvite(InviteParty.Elder).getOrThrow()
        return familyRepository.acceptInvite(invite.inviteCode, InviteParty.Family).getOrThrow()
    }
}
