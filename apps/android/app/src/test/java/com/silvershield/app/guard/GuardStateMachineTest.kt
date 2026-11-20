package com.silvershield.app.guard

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P04 守护状态机与仓库单元测试。
 *
 * 覆盖：状态转换、开始 / 停止、音频不可用、授权撤销、启动失败、设置持久化。
 * 不依赖 Android 运行时，也不涉及真实屏幕采集。
 */
class GuardStateMachineTest {

    private class FakeSettingsStore(
        initial: GuardPreferences = GuardPreferences(),
    ) : GuardSettingsStore {
        private val state = MutableStateFlow(initial)
        override val preferences: Flow<GuardPreferences> = state
        override suspend fun update(transform: (GuardPreferences) -> GuardPreferences) {
            state.value = transform(state.value)
        }
    }

    private fun newRepository(
        store: FakeSettingsStore = FakeSettingsStore(),
        service: FakeGuardService = FakeGuardService(requestDelayMillis = 20L),
    ): GuardRepository = GuardRepository(service, store)

    @Test
    fun `初始状态为未开启`() = runBlocking {
        val repository = newRepository()
        assertEquals(GuardState.OFF, repository.status.value.state)
        assertEquals(GuardAudioState.UNVERIFIED, repository.status.value.audioState)
    }

    @Test
    fun `未授权时开始守护需要先授权`() = runBlocking {
        val repository = newRepository()
        val result = repository.startGuard()
        assertEquals(GuardStartResult.NeedAuthorization, result)
        assertEquals(GuardState.OFF, repository.status.value.state)
    }

    @Test
    fun `授权后开始守护依次进入请求中与运行中`() = runBlocking {
        val repository = newRepository()
        repository.grantScreenAuthorization()
        assertEquals(ScreenAuthorizationState.GRANTED, repository.preferences.first().screenAuthorization)

        val job = launch { repository.startGuard() }
        yield()
        assertEquals(GuardState.REQUESTING, repository.status.value.state)

        job.join()
        assertEquals(GuardState.RUNNING, repository.status.value.state)
        assertNotNull(repository.status.value.lastChangedAt)
    }

    @Test
    fun `停止守护后进入已停止`() = runBlocking {
        val repository = newRepository()
        repository.grantScreenAuthorization()
        repository.startGuard()
        assertEquals(GuardState.RUNNING, repository.status.value.state)

        repository.stopGuard()
        assertEquals(GuardState.STOPPED, repository.status.value.state)
    }

    @Test
    fun `音频不可用场景进入降级运行并标记音频不可用`() = runBlocking {
        val repository = newRepository()
        repository.grantScreenAuthorization()

        val result = repository.selectScenario(GuardScenario.AUDIO_UNAVAILABLE)
        assertEquals(GuardScenarioResult.APPLIED, result)
        assertEquals(GuardState.DEGRADED, repository.status.value.state)
        assertEquals(GuardAudioState.UNAVAILABLE, repository.status.value.audioState)
        assertTrue(repository.status.value.reason?.contains("音频") == true)
    }

    @Test
    fun `启动失败场景进入异常并返回失败原因`() = runBlocking {
        val repository = newRepository()
        repository.grantScreenAuthorization()
        repository.selectScenario(GuardScenario.START_FAILURE)

        val result = repository.startGuard()
        assertTrue(result is GuardStartResult.Failed)
        assertEquals(GuardState.ERROR, repository.status.value.state)
        assertTrue(repository.status.value.reason?.contains("启动失败") == true)
    }

    @Test
    fun `撤销授权后运行中的守护不再处于运行中`() = runBlocking {
        val repository = newRepository()
        repository.grantScreenAuthorization()
        repository.startGuard()
        assertEquals(GuardState.RUNNING, repository.status.value.state)

        repository.revokeScreenAuthorization()
        assertEquals(GuardState.ERROR, repository.status.value.state)
        assertEquals(
            ScreenAuthorizationState.REVOKED,
            repository.preferences.first().screenAuthorization,
        )
    }

    @Test
    fun `模拟授权失效场景同步撤销授权状态`() = runBlocking {
        val repository = newRepository()
        repository.grantScreenAuthorization()

        repository.selectScenario(GuardScenario.AUTHORIZATION_LOST)
        assertEquals(GuardState.ERROR, repository.status.value.state)
        assertEquals(
            ScreenAuthorizationState.REVOKED,
            repository.preferences.first().screenAuthorization,
        )
    }

    @Test
    fun `未授权时不能切换到运行类模拟场景`() = runBlocking {
        val repository = newRepository()
        val result = repository.selectScenario(GuardScenario.NORMAL)
        assertEquals(GuardScenarioResult.NEED_AUTHORIZATION, result)
        assertEquals(GuardState.OFF, repository.status.value.state)

        // 「用户停止」场景不要求授权，可用于验证停止界面。
        val stopResult = repository.selectScenario(GuardScenario.USER_STOPPED)
        assertEquals(GuardScenarioResult.APPLIED, stopResult)
        assertEquals(GuardState.STOPPED, repository.status.value.state)
    }

    @Test
    fun `停止后可以重新开启守护`() = runBlocking {
        val repository = newRepository()
        repository.grantScreenAuthorization()
        repository.startGuard()
        repository.stopGuard()
        assertEquals(GuardState.STOPPED, repository.status.value.state)

        repository.startGuard()
        assertEquals(GuardState.RUNNING, repository.status.value.state)
    }

    @Test
    fun `守护设置写入后可由同一存储重新读取`() = runBlocking {
        val store = FakeSettingsStore()
        val first = newRepository(store = store)

        first.setRiskReminderEnabled(false)
        first.setVoiceReminderEnabled(false)
        first.setReminderVolume(ReminderVolume.HIGH)
        first.setStatusHintEnabled(false)
        first.grantScreenAuthorization()

        // 新建仓库模拟应用重新进入：应读取到之前保存的设置。
        val second = newRepository(store = store)
        val restored = second.preferences.first()

        assertEquals(false, restored.riskReminderEnabled)
        assertEquals(false, restored.voiceReminderEnabled)
        assertEquals(ReminderVolume.HIGH, restored.reminderVolume)
        assertEquals(false, restored.statusHintEnabled)
        assertEquals(ScreenAuthorizationState.GRANTED, restored.screenAuthorization)
    }
}
