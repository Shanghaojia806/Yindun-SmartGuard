package com.silvershield.app.guard.risk

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room 实现的风险事件仓库（P06）。
 *
 * - 数据只在本机读写，不做任何网络上传；
 * - 示例事件由 [FakeRiskEventRepository] 提供，首次进入记录页写入一次，
 *   用户清空后不再自动回填；
 * - 后续真实识别引擎只需调用 [RiskEventRepository] 的写入入口即可接入，
 *   不需要修改页面与 ViewModel。
 */
class RoomRiskEventRepository(
    context: Context,
    private val mockSource: RiskEventSource = FakeRiskEventRepository(),
) : RiskEventRepository {

    private val appContext = context.applicationContext
    private val dao: RiskEventDao = SilverShieldDatabase.getInstance(appContext).riskEventDao()

    override val events: Flow<List<RiskEvent>> =
        dao.observeAll().map { entities -> entities.map(RiskEventEntity::toDomain) }

    override suspend fun seedMockEvents() {
        // 只在首次使用时写入；标记持久化在 SharedPreferences，清空记录后不会自动回填。
        val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_MOCK_SEEDED, false)) return
        if (dao.count() == 0) {
            dao.upsert(mockSource.initialEvents().map(RiskEvent::toEntity))
        }
        preferences.edit().putBoolean(KEY_MOCK_SEEDED, true).apply()
    }

    override suspend fun addMockEvent() {
        dao.upsert(listOf(mockSource.next().toEntity()))
    }

    override suspend fun updateHandleStatus(eventId: String, status: RiskHandleStatus) {
        dao.updateHandleStatus(eventId = eventId, status = status.name)
    }

    override suspend fun delete(eventId: String) = dao.deleteById(eventId)

    override suspend fun clear() = dao.clear()

    private companion object {
        const val PREFERENCES_NAME = "risk_event_store"
        const val KEY_MOCK_SEEDED = "mock_events_seeded"
    }
}
