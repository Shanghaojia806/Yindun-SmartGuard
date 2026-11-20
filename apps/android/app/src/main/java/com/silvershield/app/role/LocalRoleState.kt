package com.silvershield.app.role

/**
 * 本地角色状态。
 *
 * 仅描述"这台设备上次使用哪个身份"，不包含任何账号凭证；
 * 当前为本地模拟登录，不代表真实身份认证。
 */
data class LocalRoleState(
    /** 是否已完成首次引导 */
    val onboardingCompleted: Boolean = false,
    /** 上次选择的角色；未选择时为 null */
    val role: UserRole? = null,
) {
    /**
     * 应用入口判定（纯逻辑，便于单元测试）：
     * - 未完成引导或没有角色 → 进入首次引导；
     * - 已完成引导且角色有效 → 直接进入对应角色端。
     */
    val entry: AppEntry
        get() = if (onboardingCompleted && role != null) {
            AppEntry.RoleHome(role)
        } else {
            AppEntry.Onboarding
        }
}

/** 应用启动入口。 */
sealed interface AppEntry {
    /** 首次引导（E01） */
    data object Onboarding : AppEntry

    /** 直接进入某个角色端 */
    data class RoleHome(val role: UserRole) : AppEntry
}
