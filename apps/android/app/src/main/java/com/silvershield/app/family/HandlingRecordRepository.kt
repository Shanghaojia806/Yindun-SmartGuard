package com.silvershield.app.family

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 协同处理仓库（P08）。
 *
 * 约束：
 * - 只有当前仍可访问的共享事件才能更新；
 * - 每次更新追加一条 [HandlingRecord]，历史不可覆盖、不可删除；
 * - 家属只能写入自己的处理记录，不能修改 AI 原始风险等级或删除老人原始事件。
 */
interface HandlingRecordRepository {

    /** 全部处理记录（按时间正序）。 */
    val records: Flow<List<HandlingRecord>>

    /** 追加一条处理记录。 */
    suspend fun updateStatus(
        shareId: String,
        status: HandlingStatus,
        note: String,
    ): Result<HandlingRecord>
}

/**
 * 本地模拟协同处理仓库。
 *
 * 更新前必须通过 [SharedRiskEventRepository.access] 的授权校验，
 * 授权撤销或关系解除后立即拒绝，避免通过旧页面继续写入。
 */
class FakeHandlingRecordRepository(
    private val riskStore: FamilyRiskLocalStore,
    private val sharedRiskRepository: SharedRiskEventRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : HandlingRecordRepository {

    override val records: Flow<List<HandlingRecord>> = riskStore.handlingRecords

    override suspend fun updateStatus(
        shareId: String,
        status: HandlingStatus,
        note: String,
    ): Result<HandlingRecord> {
        val access = sharedRiskRepository.access(shareId)
        if (access !is ShareAccess.Granted) {
            return Result.failure(
                FamilyOperationException(
                    FamilyFailureReason.FORBIDDEN,
                    "共享授权已失效，不能继续更新处理状态。",
                ),
            )
        }
        val existing = riskStore.currentHandlingRecords()
            .filter { it.shareId == shareId }
            .sortedBy { it.createdAt }
        val latest = existing.lastOrNull()
        val previous = latest?.handlingStatus ?: HandlingStatus.PendingView
        val trimmed = note.trim()
        if (status == previous && trimmed == latest?.note.orEmpty()) {
            return Result.failure(
                FamilyOperationException(
                    FamilyFailureReason.INVALID_SCOPE,
                    "请选择新的处理状态或填写处理备注",
                ),
            )
        }

        val timestamp = now()
        val record = HandlingRecord(
            handlingId = "handle-${UUID.randomUUID()}",
            shareId = shareId,
            eventId = access.view.share.eventId,
            handlerUserId = LocalFamilyAccounts.FAMILY_USER_ID,
            handlingStatus = status,
            note = trimmed,
            createdAt = timestamp,
            updatedAt = timestamp,
            previousStatus = previous,
        )
        riskStore.upsertHandlingRecord(record)
        return Result.success(record)
    }
}
