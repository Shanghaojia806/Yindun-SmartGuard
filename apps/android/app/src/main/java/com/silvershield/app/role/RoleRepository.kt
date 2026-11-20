package com.silvershield.app.role

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

/** 角色状态存储文件名。 */
private const val ROLE_DATASTORE_NAME = "silver_shield_role"

private val Context.roleDataStore: DataStore<Preferences> by preferencesDataStore(name = ROLE_DATASTORE_NAME)

/**
 * 本地角色状态仓库（DataStore）。
 *
 * 职责仅限于"记住本机的引导完成状态与上次角色"，用于本地模拟登录与角色切换。
 * 不保存密码、令牌或任何可用于真实鉴权的信息；接入真实认证时应替换为服务端会话。
 */
class RoleRepository(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val ROLE = stringPreferencesKey("role")
    }

    /** 角色状态流；读取异常时降级为空状态，避免启动崩溃。 */
    val state: Flow<LocalRoleState> = context.roleDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            LocalRoleState(
                onboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED] ?: false,
                role = UserRole.fromStorageKey(preferences[Keys.ROLE]),
            )
        }

    /**
     * 记录所选角色（首次引导结束与角色切换共用）。
     *
     * 写入完成引导标记，保证应用重启后直接恢复到该角色端。
     */
    suspend fun saveRole(role: UserRole) {
        context.roleDataStore.edit { preferences ->
            preferences[Keys.ONBOARDING_COMPLETED] = true
            preferences[Keys.ROLE] = role.storageKey
        }
    }
}
