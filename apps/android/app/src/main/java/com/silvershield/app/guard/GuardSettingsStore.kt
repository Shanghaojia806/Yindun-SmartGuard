package com.silvershield.app.guard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** 守护偏好存储文件名。 */
private const val GUARD_DATASTORE_NAME = "silver_shield_guard"

private val Context.guardDataStore: DataStore<Preferences> by preferencesDataStore(name = GUARD_DATASTORE_NAME)

/**
 * 守护偏好存储接口。
 *
 * 抽象出接口的目的：
 * - 页面与 ViewModel 不直接接触 DataStore；
 * - 单元测试可用内存实现验证设置读写，不依赖 Android 运行时。
 */
interface GuardSettingsStore {

    /** 当前偏好流。 */
    val preferences: Flow<GuardPreferences>

    /**
     * 原子更新整份偏好。
     *
     * 在 DataStore 的 edit 事务内基于最新值变换，避免连续修改时互相覆盖。
     */
    suspend fun update(transform: (GuardPreferences) -> GuardPreferences)
}

/**
 * DataStore 实现：守护设置与模拟授权状态持久化在本机。
 *
 * 仅保存提醒偏好与模拟授权标记，不保存任何屏幕内容、音频或识别结果。
 */
class DataStoreGuardSettingsStore(private val context: Context) : GuardSettingsStore {

    private object Keys {
        val RISK_REMINDER = booleanPreferencesKey("risk_reminder_enabled")
        val VOICE_REMINDER = booleanPreferencesKey("voice_reminder_enabled")
        val REMINDER_VOLUME = stringPreferencesKey("reminder_volume")
        val STATUS_HINT = booleanPreferencesKey("status_hint_enabled")
        val SCREEN_AUTHORIZATION = stringPreferencesKey("screen_authorization")
        val FAMILY_SHARING = booleanPreferencesKey("family_sharing_enabled")
    }

    override val preferences: Flow<GuardPreferences> = context.guardDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { stored -> stored.toGuardPreferences() }

    override suspend fun update(transform: (GuardPreferences) -> GuardPreferences) {
        context.guardDataStore.edit { stored ->
            val next = transform(stored.toGuardPreferences())
            stored[Keys.RISK_REMINDER] = next.riskReminderEnabled
            stored[Keys.VOICE_REMINDER] = next.voiceReminderEnabled
            stored[Keys.REMINDER_VOLUME] = next.reminderVolume.name
            stored[Keys.STATUS_HINT] = next.statusHintEnabled
            stored[Keys.SCREEN_AUTHORIZATION] = next.screenAuthorization.name
            stored[Keys.FAMILY_SHARING] = next.familySharingEnabled
        }
    }

    private fun Preferences.toGuardPreferences(): GuardPreferences {
        val defaults = GuardPreferences()
        return GuardPreferences(
            riskReminderEnabled = this[Keys.RISK_REMINDER] ?: defaults.riskReminderEnabled,
            voiceReminderEnabled = this[Keys.VOICE_REMINDER] ?: defaults.voiceReminderEnabled,
            reminderVolume = ReminderVolume.entries
                .firstOrNull { it.name == this[Keys.REMINDER_VOLUME] }
                ?: defaults.reminderVolume,
            statusHintEnabled = this[Keys.STATUS_HINT] ?: defaults.statusHintEnabled,
            screenAuthorization = ScreenAuthorizationState.entries
                .firstOrNull { it.name == this[Keys.SCREEN_AUTHORIZATION] }
                ?: defaults.screenAuthorization,
            familySharingEnabled = this[Keys.FAMILY_SHARING] ?: defaults.familySharingEnabled,
        )
    }
}
