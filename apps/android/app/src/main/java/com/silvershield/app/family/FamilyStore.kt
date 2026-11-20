package com.silvershield.app.family

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.silvershield.app.guard.risk.SilverShieldDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * P07 家庭关系 / 邀请 / 授权本地表。
 *
 * 全部数据仅保存在本机，不做网络上传；枚举以 name 存字符串，
 * 列表以换行分隔，避免额外序列化依赖。
 */

private const val SEPARATOR = "\n"

// ---------------------------------------------------------------- Entity

@Entity(tableName = "family_relations")
data class FamilyRelationEntity(
    @PrimaryKey val relationId: String,
    val elderUserId: String,
    val familyUserId: String,
    val elderDisplayName: String,
    val familyDisplayName: String,
    val relationStatusKey: String,
    val initiatedByKey: String,
    val createdAt: Long,
    val confirmedAt: Long?,
    val expiresAt: Long,
    val revokedAt: Long?,
    val isMock: Boolean,
)

@Entity(
    tableName = "family_invites",
    indices = [Index(value = ["inviteCode"], unique = true)],
)
data class FamilyInviteEntity(
    @PrimaryKey val inviteId: String,
    val inviteCode: String,
    val inviterRoleKey: String,
    val inviterUserId: String,
    val inviterDisplayName: String,
    val inviteeRoleKey: String,
    val createdAt: Long,
    val expiresAt: Long,
    val statusKey: String,
    val usedAt: Long?,
    val relationId: String?,
    val isMock: Boolean,
)

@Entity(tableName = "consent_records")
data class ConsentRecordEntity(
    @PrimaryKey val consentId: String,
    val elderUserId: String,
    val familyUserId: String,
    val relationId: String,
    val authorizationTypeKey: String,
    val scopeTypeKey: String,
    val scopeValuesText: String,
    val sourceEventId: String?,
    val grantedAt: Long,
    val expiresAt: Long?,
    val revokedAt: Long?,
    val consentStatusKey: String,
    val updatedAt: Long,
    val note: String?,
    val isMock: Boolean,
)

// ---------------------------------------------------------------- 转换

fun FamilyRelation.toEntity(): FamilyRelationEntity = FamilyRelationEntity(
    relationId = relationId,
    elderUserId = elderUserId,
    familyUserId = familyUserId,
    elderDisplayName = elderDisplayName,
    familyDisplayName = familyDisplayName,
    relationStatusKey = relationStatus.name,
    initiatedByKey = initiatedBy.name,
    createdAt = createdAt,
    confirmedAt = confirmedAt,
    expiresAt = expiresAt,
    revokedAt = revokedAt,
    isMock = isMock,
)

fun FamilyRelationEntity.toDomain(): FamilyRelation = FamilyRelation(
    relationId = relationId,
    elderUserId = elderUserId,
    familyUserId = familyUserId,
    elderDisplayName = elderDisplayName,
    familyDisplayName = familyDisplayName,
    relationStatus = FamilyRelationStatus.entries.firstOrNull { it.name == relationStatusKey }
        ?: FamilyRelationStatus.EXPIRED,
    initiatedBy = InviteParty.entries.firstOrNull { it.name == initiatedByKey } ?: InviteParty.Elder,
    createdAt = createdAt,
    confirmedAt = confirmedAt,
    expiresAt = expiresAt,
    revokedAt = revokedAt,
    isMock = isMock,
)

fun FamilyInvite.toEntity(): FamilyInviteEntity = FamilyInviteEntity(
    inviteId = inviteId,
    inviteCode = inviteCode,
    inviterRoleKey = inviterRole.name,
    inviterUserId = inviterUserId,
    inviterDisplayName = inviterDisplayName,
    inviteeRoleKey = inviteeRole.name,
    createdAt = createdAt,
    expiresAt = expiresAt,
    statusKey = status.name,
    usedAt = usedAt,
    relationId = relationId,
    isMock = isMock,
)

fun FamilyInviteEntity.toDomain(): FamilyInvite = FamilyInvite(
    inviteId = inviteId,
    inviteCode = inviteCode,
    inviterRole = InviteParty.entries.firstOrNull { it.name == inviterRoleKey } ?: InviteParty.Elder,
    inviterUserId = inviterUserId,
    inviterDisplayName = inviterDisplayName,
    inviteeRole = InviteParty.entries.firstOrNull { it.name == inviteeRoleKey } ?: InviteParty.Family,
    createdAt = createdAt,
    expiresAt = expiresAt,
    status = InviteStatus.entries.firstOrNull { it.name == statusKey } ?: InviteStatus.EXPIRED,
    usedAt = usedAt,
    relationId = relationId,
    isMock = isMock,
)

fun ConsentRecord.toEntity(): ConsentRecordEntity = ConsentRecordEntity(
    consentId = consentId,
    elderUserId = elderUserId,
    familyUserId = familyUserId,
    relationId = relationId,
    authorizationTypeKey = authorizationType.name,
    scopeTypeKey = scopeType.name,
    scopeValuesText = scopeValues.joinToString(SEPARATOR),
    sourceEventId = sourceEventId,
    grantedAt = grantedAt,
    expiresAt = expiresAt,
    revokedAt = revokedAt,
    consentStatusKey = consentStatus.name,
    updatedAt = updatedAt,
    note = note,
    isMock = isMock,
)

fun ConsentRecordEntity.toDomain(): ConsentRecord = ConsentRecord(
    consentId = consentId,
    elderUserId = elderUserId,
    familyUserId = familyUserId,
    relationId = relationId,
    authorizationType = AuthorizationType.entries.firstOrNull { it.name == authorizationTypeKey }
        ?: AuthorizationType.EventShare,
    scopeType = ConsentScopeType.entries.firstOrNull { it.name == scopeTypeKey }
        ?: ConsentScopeType.EventIds,
    scopeValues = scopeValuesText.toLines(),
    sourceEventId = sourceEventId,
    grantedAt = grantedAt,
    expiresAt = expiresAt,
    revokedAt = revokedAt,
    consentStatus = ConsentStatus.entries.firstOrNull { it.name == consentStatusKey }
        ?: ConsentStatus.EXPIRED,
    updatedAt = updatedAt,
    note = note,
    isMock = isMock,
)

private fun String.toLines(): List<String> =
    split(SEPARATOR).map(String::trim).filter(String::isNotEmpty)

// ---------------------------------------------------------------- DAO

@Dao
interface FamilyRelationDao {

    @Query("SELECT * FROM family_relations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FamilyRelationEntity>>

    @Query("SELECT * FROM family_relations ORDER BY createdAt DESC")
    suspend fun getAll(): List<FamilyRelationEntity>

    @Query("SELECT * FROM family_relations WHERE relationId = :relationId")
    suspend fun getById(relationId: String): FamilyRelationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FamilyRelationEntity)

    @Query("DELETE FROM family_relations WHERE relationId = :relationId")
    suspend fun deleteById(relationId: String)
}

@Dao
interface FamilyInviteDao {

    @Query("SELECT * FROM family_invites ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FamilyInviteEntity>>

    @Query("SELECT * FROM family_invites ORDER BY createdAt DESC")
    suspend fun getAll(): List<FamilyInviteEntity>

    @Query("SELECT * FROM family_invites WHERE inviteCode = :code LIMIT 1")
    suspend fun getByCode(code: String): FamilyInviteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FamilyInviteEntity)
}

@Dao
interface ConsentRecordDao {

    @Query("SELECT * FROM consent_records ORDER BY grantedAt DESC")
    fun observeAll(): Flow<List<ConsentRecordEntity>>

    @Query("SELECT * FROM consent_records ORDER BY grantedAt DESC")
    suspend fun getAll(): List<ConsentRecordEntity>

    @Query("SELECT * FROM consent_records WHERE consentId = :consentId")
    suspend fun getById(consentId: String): ConsentRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConsentRecordEntity)
}

// ---------------------------------------------------------------- Store

/**
 * 家庭关系与授权的本地存储抽象。
 *
 * Room 实现用于应用运行；[InMemoryFamilyLocalStore] 供 JVM 单元测试验证业务规则，
 * 两者共享同一套仓库逻辑，避免测试绕过真实校验。
 */
interface FamilyLocalStore {
    val relations: Flow<List<FamilyRelation>>
    val invites: Flow<List<FamilyInvite>>
    val consents: Flow<List<ConsentRecord>>

    suspend fun currentRelations(): List<FamilyRelation>
    suspend fun currentInvites(): List<FamilyInvite>
    suspend fun currentConsents(): List<ConsentRecord>

    suspend fun upsertRelation(relation: FamilyRelation)
    suspend fun deleteRelation(relationId: String)
    suspend fun upsertInvite(invite: FamilyInvite)
    suspend fun upsertConsent(consent: ConsentRecord)
}

/** Room 实现：随数据库持久化，应用重启后恢复。 */
class RoomFamilyLocalStore(
    private val relationDao: FamilyRelationDao,
    private val inviteDao: FamilyInviteDao,
    private val consentDao: ConsentRecordDao,
) : FamilyLocalStore {

    constructor(context: Context) : this(
        relationDao = SilverShieldDatabase.getInstance(context.applicationContext).familyRelationDao(),
        inviteDao = SilverShieldDatabase.getInstance(context.applicationContext).familyInviteDao(),
        consentDao = SilverShieldDatabase.getInstance(context.applicationContext).consentRecordDao(),
    )

    override val relations: Flow<List<FamilyRelation>> =
        relationDao.observeAll().map { list -> list.map(FamilyRelationEntity::toDomain) }

    override val invites: Flow<List<FamilyInvite>> =
        inviteDao.observeAll().map { list -> list.map(FamilyInviteEntity::toDomain) }

    override val consents: Flow<List<ConsentRecord>> =
        consentDao.observeAll().map { list -> list.map(ConsentRecordEntity::toDomain) }

    override suspend fun currentRelations(): List<FamilyRelation> =
        relationDao.getAll().map(FamilyRelationEntity::toDomain)

    override suspend fun currentInvites(): List<FamilyInvite> =
        inviteDao.getAll().map(FamilyInviteEntity::toDomain)

    override suspend fun currentConsents(): List<ConsentRecord> =
        consentDao.getAll().map(ConsentRecordEntity::toDomain)

    override suspend fun upsertRelation(relation: FamilyRelation) =
        relationDao.upsert(relation.toEntity())

    override suspend fun deleteRelation(relationId: String) = relationDao.deleteById(relationId)

    override suspend fun upsertInvite(invite: FamilyInvite) = inviteDao.upsert(invite.toEntity())

    override suspend fun upsertConsent(consent: ConsentRecord) = consentDao.upsert(consent.toEntity())
}

/** 内存实现：仅用于 JVM 单元测试，行为与 Room 实现一致。 */
class InMemoryFamilyLocalStore : FamilyLocalStore {

    private val relationState = MutableStateFlow<List<FamilyRelation>>(emptyList())
    private val inviteState = MutableStateFlow<List<FamilyInvite>>(emptyList())
    private val consentState = MutableStateFlow<List<ConsentRecord>>(emptyList())

    override val relations: Flow<List<FamilyRelation>> = relationState
    override val invites: Flow<List<FamilyInvite>> = inviteState
    override val consents: Flow<List<ConsentRecord>> = consentState

    override suspend fun currentRelations(): List<FamilyRelation> = relationState.value
    override suspend fun currentInvites(): List<FamilyInvite> = inviteState.value
    override suspend fun currentConsents(): List<ConsentRecord> = consentState.value

    override suspend fun upsertRelation(relation: FamilyRelation) {
        relationState.update { list ->
            list.filterNot { it.relationId == relation.relationId } + relation
        }
    }

    override suspend fun deleteRelation(relationId: String) {
        relationState.update { list -> list.filterNot { it.relationId == relationId } }
    }

    override suspend fun upsertInvite(invite: FamilyInvite) {
        inviteState.update { list ->
            list.filterNot { it.inviteId == invite.inviteId } + invite
        }
    }

    override suspend fun upsertConsent(consent: ConsentRecord) {
        consentState.update { list ->
            list.filterNot { it.consentId == consent.consentId } + consent
        }
    }
}
