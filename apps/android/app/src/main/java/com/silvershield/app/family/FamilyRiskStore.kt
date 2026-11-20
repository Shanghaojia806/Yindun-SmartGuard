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
 * P08 共享事件 / 协同处理 / 消息中心本地表。
 *
 * 所有表只保存授权摘要、处理记录与消息，不保存原始屏幕内容；
 * 枚举以 name 存字符串，新增枚举项不影响历史数据读取。
 */

// ---------------------------------------------------------------- Entity

@Entity(
    tableName = "shared_risk_events",
    indices = [Index("eventId"), Index("consentId")],
)
data class SharedRiskEventEntity(
    @PrimaryKey val shareId: String,
    val eventId: String,
    val elderUserId: String,
    val familyUserId: String,
    val consentId: String,
    val sharedAt: Long,
    val shareStatusKey: String,
    val readAt: Long?,
    val revokedAt: Long?,
    val isMock: Boolean,
)

@Entity(
    tableName = "handling_records",
    indices = [Index("shareId")],
)
data class HandlingRecordEntity(
    @PrimaryKey val handlingId: String,
    val shareId: String,
    val eventId: String,
    val handlerUserId: String,
    val handlingStatusKey: String,
    val previousStatusKey: String?,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isMock: Boolean,
)

@Entity(
    tableName = "family_messages",
    indices = [Index("relatedShareId")],
)
data class FamilyMessageEntity(
    @PrimaryKey val messageId: String,
    val messageTypeKey: String,
    val title: String,
    val content: String,
    val relatedEventId: String?,
    val relatedShareId: String?,
    val createdAt: Long,
    val readAt: Long?,
    val isRead: Boolean,
    val deliveryStateKey: String,
    val isMock: Boolean,
)

// ---------------------------------------------------------------- 转换

fun SharedRiskEvent.toEntity(): SharedRiskEventEntity = SharedRiskEventEntity(
    shareId = shareId,
    eventId = eventId,
    elderUserId = elderUserId,
    familyUserId = familyUserId,
    consentId = consentId,
    sharedAt = sharedAt,
    shareStatusKey = shareStatus.name,
    readAt = readAt,
    revokedAt = revokedAt,
    isMock = isMock,
)

fun SharedRiskEventEntity.toDomain(): SharedRiskEvent = SharedRiskEvent(
    shareId = shareId,
    eventId = eventId,
    elderUserId = elderUserId,
    familyUserId = familyUserId,
    consentId = consentId,
    sharedAt = sharedAt,
    shareStatus = ShareStatus.entries.firstOrNull { it.name == shareStatusKey } ?: ShareStatus.REVOKED,
    readAt = readAt,
    revokedAt = revokedAt,
    isMock = isMock,
)

fun HandlingRecord.toEntity(): HandlingRecordEntity = HandlingRecordEntity(
    handlingId = handlingId,
    shareId = shareId,
    eventId = eventId,
    handlerUserId = handlerUserId,
    handlingStatusKey = handlingStatus.name,
    previousStatusKey = previousStatus?.name,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isMock = isMock,
)

fun HandlingRecordEntity.toDomain(): HandlingRecord = HandlingRecord(
    handlingId = handlingId,
    shareId = shareId,
    eventId = eventId,
    handlerUserId = handlerUserId,
    handlingStatus = HandlingStatus.entries.firstOrNull { it.name == handlingStatusKey }
        ?: HandlingStatus.PendingView,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    previousStatus = previousStatusKey?.let { key ->
        HandlingStatus.entries.firstOrNull { it.name == key }
    },
    isMock = isMock,
)

fun FamilyMessage.toEntity(): FamilyMessageEntity = FamilyMessageEntity(
    messageId = messageId,
    messageTypeKey = messageType.name,
    title = title,
    content = content,
    relatedEventId = relatedEventId,
    relatedShareId = relatedShareId,
    createdAt = createdAt,
    readAt = readAt,
    isRead = isRead,
    deliveryStateKey = deliveryState.name,
    isMock = isMock,
)

fun FamilyMessageEntity.toDomain(): FamilyMessage = FamilyMessage(
    messageId = messageId,
    messageType = FamilyMessageType.entries.firstOrNull { it.name == messageTypeKey }
        ?: FamilyMessageType.RiskShared,
    title = title,
    content = content,
    relatedEventId = relatedEventId,
    relatedShareId = relatedShareId,
    createdAt = createdAt,
    readAt = readAt,
    isRead = isRead,
    deliveryState = MessageDeliveryState.entries.firstOrNull { it.name == deliveryStateKey }
        ?: MessageDeliveryState.Delivered,
    isMock = isMock,
)

// ---------------------------------------------------------------- DAO

@Dao
interface SharedRiskEventDao {

    @Query("SELECT * FROM shared_risk_events ORDER BY sharedAt DESC")
    fun observeAll(): Flow<List<SharedRiskEventEntity>>

    @Query("SELECT * FROM shared_risk_events ORDER BY sharedAt DESC")
    suspend fun getAll(): List<SharedRiskEventEntity>

    @Query("SELECT * FROM shared_risk_events WHERE shareId = :shareId")
    suspend fun getById(shareId: String): SharedRiskEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SharedRiskEventEntity)
}

@Dao
interface HandlingRecordDao {

    @Query("SELECT * FROM handling_records ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<HandlingRecordEntity>>

    @Query("SELECT * FROM handling_records ORDER BY createdAt ASC")
    suspend fun getAll(): List<HandlingRecordEntity>

    @Query("SELECT * FROM handling_records WHERE shareId = :shareId ORDER BY createdAt ASC")
    suspend fun getByShare(shareId: String): List<HandlingRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HandlingRecordEntity)
}

@Dao
interface FamilyMessageDao {

    @Query("SELECT * FROM family_messages ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FamilyMessageEntity>>

    @Query("SELECT * FROM family_messages ORDER BY createdAt DESC")
    suspend fun getAll(): List<FamilyMessageEntity>

    @Query("SELECT * FROM family_messages WHERE messageId = :messageId")
    suspend fun getById(messageId: String): FamilyMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FamilyMessageEntity)
}

// ---------------------------------------------------------------- Store

/** 共享事件 / 处理记录 / 消息的本地存储抽象。 */
interface FamilyRiskLocalStore {
    val shares: Flow<List<SharedRiskEvent>>
    val handlingRecords: Flow<List<HandlingRecord>>
    val messages: Flow<List<FamilyMessage>>

    suspend fun currentShares(): List<SharedRiskEvent>
    suspend fun currentHandlingRecords(): List<HandlingRecord>
    suspend fun currentMessages(): List<FamilyMessage>

    suspend fun upsertShare(share: SharedRiskEvent)
    suspend fun upsertHandlingRecord(record: HandlingRecord)
    suspend fun upsertMessage(message: FamilyMessage)
}

/** Room 实现：随数据库持久化，应用重启后恢复。 */
class RoomFamilyRiskLocalStore(
    private val shareDao: SharedRiskEventDao,
    private val handlingDao: HandlingRecordDao,
    private val messageDao: FamilyMessageDao,
) : FamilyRiskLocalStore {

    constructor(context: Context) : this(
        shareDao = SilverShieldDatabase.getInstance(context.applicationContext).sharedRiskEventDao(),
        handlingDao = SilverShieldDatabase.getInstance(context.applicationContext).handlingRecordDao(),
        messageDao = SilverShieldDatabase.getInstance(context.applicationContext).familyMessageDao(),
    )

    override val shares: Flow<List<SharedRiskEvent>> =
        shareDao.observeAll().map { list -> list.map(SharedRiskEventEntity::toDomain) }

    override val handlingRecords: Flow<List<HandlingRecord>> =
        handlingDao.observeAll().map { list -> list.map(HandlingRecordEntity::toDomain) }

    override val messages: Flow<List<FamilyMessage>> =
        messageDao.observeAll().map { list -> list.map(FamilyMessageEntity::toDomain) }

    override suspend fun currentShares(): List<SharedRiskEvent> =
        shareDao.getAll().map(SharedRiskEventEntity::toDomain)

    override suspend fun currentHandlingRecords(): List<HandlingRecord> =
        handlingDao.getAll().map(HandlingRecordEntity::toDomain)

    override suspend fun currentMessages(): List<FamilyMessage> =
        messageDao.getAll().map(FamilyMessageEntity::toDomain)

    override suspend fun upsertShare(share: SharedRiskEvent) = shareDao.upsert(share.toEntity())

    override suspend fun upsertHandlingRecord(record: HandlingRecord) =
        handlingDao.upsert(record.toEntity())

    override suspend fun upsertMessage(message: FamilyMessage) =
        messageDao.upsert(message.toEntity())
}

/** 内存实现：仅供 JVM 单元测试，行为与 Room 实现保持一致。 */
class InMemoryFamilyRiskLocalStore : FamilyRiskLocalStore {

    private val shareState = MutableStateFlow<List<SharedRiskEvent>>(emptyList())
    private val handlingState = MutableStateFlow<List<HandlingRecord>>(emptyList())
    private val messageState = MutableStateFlow<List<FamilyMessage>>(emptyList())

    override val shares: Flow<List<SharedRiskEvent>> = shareState
    override val handlingRecords: Flow<List<HandlingRecord>> = handlingState
    override val messages: Flow<List<FamilyMessage>> = messageState

    override suspend fun currentShares(): List<SharedRiskEvent> = shareState.value
    override suspend fun currentHandlingRecords(): List<HandlingRecord> = handlingState.value
    override suspend fun currentMessages(): List<FamilyMessage> = messageState.value

    override suspend fun upsertShare(share: SharedRiskEvent) {
        shareState.update { list -> list.filterNot { it.shareId == share.shareId } + share }
    }

    override suspend fun upsertHandlingRecord(record: HandlingRecord) {
        handlingState.update { list ->
            list.filterNot { it.handlingId == record.handlingId } + record
        }
    }

    override suspend fun upsertMessage(message: FamilyMessage) {
        messageState.update { list ->
            list.filterNot { it.messageId == message.messageId } + message
        }
    }
}
