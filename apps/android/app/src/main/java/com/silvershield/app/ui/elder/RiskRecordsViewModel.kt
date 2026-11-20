package com.silvershield.app.ui.elder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.silvershield.app.guard.risk.RiskEvent
import com.silvershield.app.guard.risk.RiskEventRepository
import com.silvershield.app.guard.risk.RiskHandleStatus
import com.silvershield.app.guard.risk.RiskLevelFilter
import com.silvershield.app.guard.risk.RiskType
import com.silvershield.app.guard.risk.matchesType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * E08 风险记录页状态。
 *
 * - [events] 为筛选后的列表，按发生时间倒序；
 * - [latestAlertEvent] 供 E06 提醒使用：最新一条**未处理**记录，不受筛选影响；
 * - [loadFailed] 为 true 时页面展示 ErrorState，而不是 EmptyState。
 */
data class RiskRecordsUiState(
    val events: List<RiskEvent> = emptyList(),
    val levelFilter: RiskLevelFilter = RiskLevelFilter.All,
    val typeFilter: RiskType? = null,
    val totalCount: Int = 0,
    val latestAlertEvent: RiskEvent? = null,
    val message: String? = null,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
)

/**
 * E08 风险记录 ViewModel（P06）。
 *
 * 数据只来自本地 Room（经 [RiskEventRepository]），页面不直接访问数据库；
 * 删除/清空真实作用于 Room，示例事件带「模拟数据」标记。
 */
class RiskRecordsViewModel(
    private val repository: RiskEventRepository,
) : ViewModel() {

    private data class StoredEvents(
        val events: List<RiskEvent>?,
        val failed: Boolean,
    )

    private val levelFilter = MutableStateFlow(RiskLevelFilter.All)
    private val typeFilter = MutableStateFlow<RiskType?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val reloadTrigger = MutableStateFlow(0)

    // null 表示首次数据尚未到达（Room 查询是 IO 异步）；failed 表示读取异常。
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val storedEvents: StateFlow<StoredEvents> = reloadTrigger
        .flatMapLatest {
            repository.events.map { events -> StoredEvents(events = events, failed = false) }
                .catch { emit(StoredEvents(events = null, failed = true)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StoredEvents(events = null, failed = false),
        )

    val uiState: StateFlow<RiskRecordsUiState> =
        combine(
            storedEvents,
            levelFilter,
            typeFilter,
            message,
        ) { stored, currentLevel, currentType, currentMessage ->
            val all = stored.events.orEmpty()
            RiskRecordsUiState(
                events = all.filter { currentLevel.matches(it) && it.matchesType(currentType) },
                levelFilter = currentLevel,
                typeFilter = currentType,
                totalCount = all.size,
                latestAlertEvent = all.firstOrNull { it.handleStatus != RiskHandleStatus.Handled },
                message = currentMessage,
                loading = stored.events == null && !stored.failed,
                loadFailed = stored.failed,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RiskRecordsUiState(),
        )

    init {
        viewModelScope.launch {
            runCatching { repository.seedMockEvents() }
                .onFailure { message.value = "示例数据写入失败，不影响查看已有记录" }
        }
    }

    fun setLevelFilter(value: RiskLevelFilter) {
        levelFilter.value = value
    }

    /** 传 null 表示"全部类型"。 */
    fun setTypeFilter(value: RiskType?) {
        typeFilter.value = value
    }

    fun addMockEvent() {
        viewModelScope.launch {
            runCatching { repository.addMockEvent() }
                .onSuccess { message.value = "已写入一条模拟风险事件" }
                .onFailure { message.value = "写入失败，请稍后重试" }
        }
    }

    fun delete(eventId: String) {
        viewModelScope.launch {
            runCatching { repository.delete(eventId) }
                .onSuccess { message.value = "已删除这条记录" }
                .onFailure { message.value = "删除失败，请稍后重试" }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            runCatching { repository.clear() }
                .onSuccess { message.value = "已清空本地风险记录" }
                .onFailure { message.value = "清空失败，请稍后重试" }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    /** 读取失败后重试：重新订阅 Room 查询。 */
    fun retryLoad() {
        reloadTrigger.value += 1
    }

    /** 供界面层反馈一次性提示（例如语音引擎不可用）。 */
    fun notify(text: String) {
        message.value = text
    }

    companion object {
        fun factory(repository: RiskEventRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { RiskRecordsViewModel(repository) }
        }
    }
}
