package com.silvershield.app.guard.risk

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.silvershield.app.family.ConsentRecordDao
import com.silvershield.app.family.ConsentRecordEntity
import com.silvershield.app.family.FamilyInviteDao
import com.silvershield.app.family.FamilyInviteEntity
import com.silvershield.app.family.FamilyMessageDao
import com.silvershield.app.family.FamilyMessageEntity
import com.silvershield.app.family.FamilyRelationDao
import com.silvershield.app.family.FamilyRelationEntity
import com.silvershield.app.family.HandlingRecordDao
import com.silvershield.app.family.HandlingRecordEntity
import com.silvershield.app.family.SharedRiskEventDao
import com.silvershield.app.family.SharedRiskEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * 风险事件本地表（P06）。
 *
 * - 只保存结论、线索摘要与处理状态，不保存截图、不保存原始画面；
 * - 枚举以 name 存字符串，新增枚举项不影响历史数据读取；
 * - 列表字段使用换行分隔，避免额外引入序列化依赖。
 */
@Entity(tableName = "risk_events")
data class RiskEventEntity(
    @PrimaryKey val eventId: String,
    val occurredAt: Long,
    val riskTypeKey: String,
    val riskLevelKey: String,
    val title: String,
    val description: String,
    val cluesText: String,
    val evidenceSummary: String?,
    val availableModalitiesText: String,
    val missingModalitiesText: String,
    val suggestedActionsText: String,
    val modelVersion: String,
    val sourceTypeKey: String,
    val syncStatusKey: String,
    val handleStatusKey: String,
    /** 模型置信度；没有明确依据时为 null，仅内部使用，不展示为"诈骗概率"。 */
    val confidence: Float?,
    val isMock: Boolean,
)

private const val SEPARATOR = "\n"

fun RiskEvent.toEntity(): RiskEventEntity = RiskEventEntity(
    eventId = eventId,
    occurredAt = occurredAt,
    riskTypeKey = riskType.name,
    riskLevelKey = riskLevel.name,
    title = title,
    description = description,
    cluesText = clues.joinToString(SEPARATOR),
    evidenceSummary = evidenceSummary,
    availableModalitiesText = availableModalities.joinToString(SEPARATOR) { it.name },
    missingModalitiesText = missingModalities.joinToString(SEPARATOR) { it.name },
    suggestedActionsText = suggestedActions.joinToString(SEPARATOR),
    modelVersion = modelVersion,
    sourceTypeKey = sourceType.name,
    syncStatusKey = syncStatus.name,
    handleStatusKey = handleStatus.name,
    confidence = confidence,
    isMock = isMock,
)

fun RiskEventEntity.toDomain(): RiskEvent = RiskEvent(
    eventId = eventId,
    occurredAt = occurredAt,
    riskType = RiskType.entries.firstOrNull { it.name == riskTypeKey } ?: RiskType.Unknown,
    riskLevel = RiskLevel.entries.firstOrNull { it.name == riskLevelKey } ?: RiskLevel.Unknown,
    title = title,
    description = description,
    clues = cluesText.toLines(),
    evidenceSummary = evidenceSummary,
    availableModalities = availableModalitiesText.toModalities(),
    missingModalities = missingModalitiesText.toModalities(),
    suggestedActions = suggestedActionsText.toLines(),
    modelVersion = modelVersion,
    sourceType = RiskSourceType.entries.firstOrNull { it.name == sourceTypeKey } ?: RiskSourceType.Unknown,
    syncStatus = RiskSyncStatus.entries.firstOrNull { it.name == syncStatusKey } ?: RiskSyncStatus.LocalOnly,
    handleStatus = RiskHandleStatus.entries.firstOrNull { it.name == handleStatusKey } ?: RiskHandleStatus.New,
    confidence = confidence,
    isMock = isMock,
)

private fun String.toLines(): List<String> =
    split(SEPARATOR).map(String::trim).filter(String::isNotEmpty)

private fun String.toModalities(): List<RiskModality> =
    toLines().mapNotNull { key -> RiskModality.entries.firstOrNull { it.name == key } }

@Dao
interface RiskEventDao {

    @Query("SELECT * FROM risk_events ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<RiskEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(events: List<RiskEventEntity>)

    @Query("UPDATE risk_events SET handleStatusKey = :status WHERE eventId = :eventId")
    suspend fun updateHandleStatus(eventId: String, status: String)

    @Query("DELETE FROM risk_events WHERE eventId = :eventId")
    suspend fun deleteById(eventId: String)

    @Query("DELETE FROM risk_events")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM risk_events")
    suspend fun count(): Int
}

@Database(
    entities = [
        RiskEventEntity::class,
        FamilyRelationEntity::class,
        FamilyInviteEntity::class,
        ConsentRecordEntity::class,
        SharedRiskEventEntity::class,
        HandlingRecordEntity::class,
        FamilyMessageEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SilverShieldDatabase : RoomDatabase() {

    abstract fun riskEventDao(): RiskEventDao

    /** P07：家庭关系、邀请与授权记录。 */
    abstract fun familyRelationDao(): FamilyRelationDao

    abstract fun familyInviteDao(): FamilyInviteDao

    abstract fun consentRecordDao(): ConsentRecordDao

    /** P08：共享事件、协同处理历史与消息中心。 */
    abstract fun sharedRiskEventDao(): SharedRiskEventDao

    abstract fun handlingRecordDao(): HandlingRecordDao

    abstract fun familyMessageDao(): FamilyMessageDao

    companion object {
        private const val DB_NAME = "silver_shield.db"

        /**
         * P06 → P07 增量迁移：只新增家庭关系相关表，`risk_events` 结构与数据保持不变。
         *
         * 不使用破坏性迁移，避免升级时丢失 P06 本地风险记录。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `family_relations` (
                        `relationId` TEXT NOT NULL,
                        `elderUserId` TEXT NOT NULL,
                        `familyUserId` TEXT NOT NULL,
                        `elderDisplayName` TEXT NOT NULL,
                        `familyDisplayName` TEXT NOT NULL,
                        `relationStatusKey` TEXT NOT NULL,
                        `initiatedByKey` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `confirmedAt` INTEGER,
                        `expiresAt` INTEGER NOT NULL,
                        `revokedAt` INTEGER,
                        `isMock` INTEGER NOT NULL,
                        PRIMARY KEY(`relationId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `family_invites` (
                        `inviteId` TEXT NOT NULL,
                        `inviteCode` TEXT NOT NULL,
                        `inviterRoleKey` TEXT NOT NULL,
                        `inviterUserId` TEXT NOT NULL,
                        `inviterDisplayName` TEXT NOT NULL,
                        `inviteeRoleKey` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        `statusKey` TEXT NOT NULL,
                        `usedAt` INTEGER,
                        `relationId` TEXT,
                        `isMock` INTEGER NOT NULL,
                        PRIMARY KEY(`inviteId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_family_invites_inviteCode` " +
                        "ON `family_invites` (`inviteCode`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `consent_records` (
                        `consentId` TEXT NOT NULL,
                        `elderUserId` TEXT NOT NULL,
                        `familyUserId` TEXT NOT NULL,
                        `relationId` TEXT NOT NULL,
                        `authorizationTypeKey` TEXT NOT NULL,
                        `scopeTypeKey` TEXT NOT NULL,
                        `scopeValuesText` TEXT NOT NULL,
                        `sourceEventId` TEXT,
                        `grantedAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER,
                        `revokedAt` INTEGER,
                        `consentStatusKey` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `note` TEXT,
                        `isMock` INTEGER NOT NULL,
                        PRIMARY KEY(`consentId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * P07 → P08 增量迁移：只新增共享事件、协同处理与消息表。
         *
         * 不修改 `risk_events`、`family_relations`、`family_invites`、`consent_records`，
         * 保证升级后 P06/P07 数据完整保留。
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shared_risk_events` (
                        `shareId` TEXT NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `elderUserId` TEXT NOT NULL,
                        `familyUserId` TEXT NOT NULL,
                        `consentId` TEXT NOT NULL,
                        `sharedAt` INTEGER NOT NULL,
                        `shareStatusKey` TEXT NOT NULL,
                        `readAt` INTEGER,
                        `revokedAt` INTEGER,
                        `isMock` INTEGER NOT NULL,
                        PRIMARY KEY(`shareId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shared_risk_events_eventId` " +
                        "ON `shared_risk_events` (`eventId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shared_risk_events_consentId` " +
                        "ON `shared_risk_events` (`consentId`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `handling_records` (
                        `handlingId` TEXT NOT NULL,
                        `shareId` TEXT NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `handlerUserId` TEXT NOT NULL,
                        `handlingStatusKey` TEXT NOT NULL,
                        `previousStatusKey` TEXT,
                        `note` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `isMock` INTEGER NOT NULL,
                        PRIMARY KEY(`handlingId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_handling_records_shareId` " +
                        "ON `handling_records` (`shareId`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `family_messages` (
                        `messageId` TEXT NOT NULL,
                        `messageTypeKey` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `relatedEventId` TEXT,
                        `relatedShareId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `readAt` INTEGER,
                        `isRead` INTEGER NOT NULL,
                        `deliveryStateKey` TEXT NOT NULL,
                        `isMock` INTEGER NOT NULL,
                        PRIMARY KEY(`messageId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_family_messages_relatedShareId` " +
                        "ON `family_messages` (`relatedShareId`)",
                )
            }
        }

        @Volatile
        private var instance: SilverShieldDatabase? = null

        fun getInstance(context: Context): SilverShieldDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SilverShieldDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
