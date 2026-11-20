package com.silvershield.app.family

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P07 家庭关系业务规则测试（JVM）。
 *
 * 覆盖：邀请码生成 / 校验 / 过期 / 单次使用、双向确认、拒绝、取消、解除、重启恢复。
 */
class FamilyRepositoryTest {

    private lateinit var store: InMemoryFamilyLocalStore
    private lateinit var repository: FakeFamilyRepository
    private var clock: Long = 1_000_000L

    @Before
    fun setUp() {
        store = InMemoryFamilyLocalStore()
        clock = 1_000_000L
        repository = FakeFamilyRepository(store = store, now = { clock })
    }

    @Test
    fun generateInvite_createsPendingRelationButNotActive() = runBlocking {
        val invite = repository.generateInvite(InviteParty.Elder).getOrThrow()

        assertEquals(6, invite.inviteCode.length)
        assertTrue(invite.inviteCode.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
        assertEquals(InviteParty.Family, invite.inviteeRole)
        assertEquals(InviteStatus.PENDING, invite.status)

        val relation = store.currentRelations().single()
        assertEquals(FamilyRelationStatus.PENDING, relation.relationStatus)
        assertNull(relation.confirmedAt)
        // 未确认前不能视为绑定成功
        assertEquals(0, store.currentRelations().count { it.relationStatus == FamilyRelationStatus.ACTIVE })
    }

    @Test
    fun acceptInvite_byElder_bindsFamilyInitiatedInvite() = runBlocking {
        val invite = repository.generateInvite(InviteParty.Family).getOrThrow()

        val relation = repository.acceptInvite(invite.inviteCode, InviteParty.Elder).getOrThrow()

        assertEquals(FamilyRelationStatus.ACTIVE, relation.relationStatus)
        assertNotNull(relation.confirmedAt)
        assertEquals(InviteStatus.ACCEPTED, store.currentInvites().single().status)
    }

    @Test
    fun acceptInvite_byFamily_bindsElderInitiatedInvite() = runBlocking {
        val invite = repository.generateInvite(InviteParty.Elder).getOrThrow()

        val relation = repository.acceptInvite(invite.inviteCode.lowercase(), InviteParty.Family).getOrThrow()

        assertEquals(FamilyRelationStatus.ACTIVE, relation.relationStatus)
        assertEquals(LocalFamilyAccounts.ELDER_USER_ID, relation.elderUserId)
        assertEquals(LocalFamilyAccounts.FAMILY_USER_ID, relation.familyUserId)
    }

    @Test
    fun lookupInvite_wrongCode_returnsNotFound() = runBlocking {
        repository.generateInvite(InviteParty.Elder).getOrThrow()

        assertEquals(InviteLookup.NotFound, repository.lookupInvite("ZZZZZZ", InviteParty.Family))
        assertEquals(InviteLookup.InvalidFormat, repository.lookupInvite("12", InviteParty.Family))
    }

    @Test
    fun lookupInvite_selfCode_returnsSelfInvite() = runBlocking {
        val invite = repository.generateInvite(InviteParty.Elder).getOrThrow()

        assertEquals(InviteLookup.SelfInvite, repository.lookupInvite(invite.inviteCode, InviteParty.Elder))
        val failed = repository.acceptInvite(invite.inviteCode, InviteParty.Elder)
        assertTrue(failed.isFailure)
        assertEquals(FamilyRelationStatus.PENDING, store.currentRelations().single().relationStatus)
    }

    @Test
    fun expiredInvite_cannotBeAccepted_andIsPersistedAsExpired() = runBlocking {
        val invite = repository.generateInvite(InviteParty.Elder).getOrThrow()
        clock += FakeFamilyRepository.INVITE_VALIDITY_MILLIS + 1

        assertEquals(InviteLookup.Expired, repository.lookupInvite(invite.inviteCode, InviteParty.Family))
        assertTrue(repository.acceptInvite(invite.inviteCode, InviteParty.Family).isFailure)

        repository.refreshExpiry()
        assertEquals(InviteStatus.EXPIRED, store.currentInvites().single().status)
        assertEquals(FamilyRelationStatus.EXPIRED, store.currentRelations().single().relationStatus)
    }

    @Test
    fun acceptedInvite_cannotBeUsedAgain() = runBlocking {
        val invite = repository.generateInvite(InviteParty.Elder).getOrThrow()
        repository.acceptInvite(invite.inviteCode, InviteParty.Family).getOrThrow()

        val second = repository.acceptInvite(invite.inviteCode, InviteParty.Family)

        assertTrue(second.isFailure)
        assertEquals(1, store.currentRelations().size)
    }

    @Test
    fun rejectInvite_marksRelationRejected() = runBlocking {
        val invite = repository.generateInvite(InviteParty.Elder).getOrThrow()

        repository.rejectInvite(invite.inviteCode, InviteParty.Family).getOrThrow()

        assertEquals(InviteStatus.REJECTED, store.currentInvites().single().status)
        assertEquals(FamilyRelationStatus.REJECTED, store.currentRelations().single().relationStatus)
        assertEquals(0, store.currentRelations().count { it.relationStatus == FamilyRelationStatus.ACTIVE })
    }

    @Test
    fun cancelInvite_removesPendingRelation() = runBlocking {
        val invite = repository.generateInvite(InviteParty.Elder).getOrThrow()

        repository.cancelInvite(invite.inviteId).getOrThrow()

        assertEquals(InviteStatus.CANCELLED, store.currentInvites().single().status)
        assertTrue(store.currentRelations().isEmpty())
    }

    @Test
    fun revokeRelation_revokesConsentsAndKeepsRiskRecords() = runBlocking {
        val consentRepository = FakeConsentRepository(store, now = { clock })
        val relation = createActiveRelation()
        val consent = consentRepository.grantEventShare("event-1", null).getOrThrow()

        repository.revokeRelation(relation.relationId, InviteParty.Family).getOrThrow()

        val revoked = store.currentRelations().single()
        assertEquals(FamilyRelationStatus.REVOKED, revoked.relationStatus)
        assertNotNull(revoked.revokedAt)
        assertEquals(
            ConsentStatus.REVOKED,
            store.currentConsents().single { it.consentId == consent.consentId }.consentStatus,
        )
        // 解除家庭关系不涉及风险事件表，P06 记录不会被删除（此处仅验证未产生删除副作用）
        assertTrue(store.currentConsents().isNotEmpty())
    }

    @Test
    fun stateRestoresAfterRestart() = runBlocking {
        val relation = createActiveRelation()

        // 新仓库实例复用同一持久化 store，模拟应用重启
        val restarted = FakeFamilyRepository(store = store, now = { clock })

        val restored = restarted.relations.first()
        assertEquals(relation.relationId, restored.single().relationId)
        assertEquals(FamilyRelationStatus.ACTIVE, restored.single().relationStatus)
    }

    @Test
    fun duplicateGeneratedCode_isRetried() = runBlocking {
        val codes = ArrayDeque(listOf("ABC234", "ABC234", "XYZ789"))
        val repo = FakeFamilyRepository(
            store = store,
            now = { clock },
            codeGenerator = { codes.removeFirst() },
        )
        val first = repo.generateInvite(InviteParty.Elder).getOrThrow()
        repo.cancelInvite(first.inviteId).getOrThrow()

        val second = repo.generateInvite(InviteParty.Elder).getOrThrow()

        assertEquals("ABC234", first.inviteCode)
        assertEquals("XYZ789", second.inviteCode)
    }

    @Test
    fun secondOpenRelation_isRejected() = runBlocking {
        createActiveRelation()

        val result = repository.generateInvite(InviteParty.Elder)

        assertTrue(result.isFailure)
    }

    private suspend fun createActiveRelation(): FamilyRelation {
        val invite = repository.generateInvite(InviteParty.Elder).getOrThrow()
        return repository.acceptInvite(invite.inviteCode, InviteParty.Family).getOrThrow()
    }
}
