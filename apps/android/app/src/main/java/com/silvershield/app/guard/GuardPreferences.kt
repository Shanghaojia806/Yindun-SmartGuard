package com.silvershield.app.guard

/**
 * 屏幕采集授权状态。
 *
 * P04 的授权是**页面内模拟授权**，不会调用 Android 系统 MediaProjection，
 * 也不会申请无障碍权限；真实系统授权留待 P05 验证。
 */
enum class ScreenAuthorizationState {
    NOT_GRANTED,
    GRANTED,
    REVOKED,
}

/** 提醒音量偏好。仅保存用户选择，不控制系统媒体音量。 */
enum class ReminderVolume(val label: String) {
    LOW("轻"),
    STANDARD("标准"),
    HIGH("响亮"),
}

/**
 * 老人端守护相关本地偏好。
 *
 * 由 DataStore 持久化，所有页面共享同一份设置。
 */
data class GuardPreferences(
    /** 风险提醒总开关。关闭后不再弹出风险提示。 */
    val riskReminderEnabled: Boolean = true,
    /** 语音提醒开关。关闭不影响屏幕文字提醒与守护运行。 */
    val voiceReminderEnabled: Boolean = true,
    /** 提醒音量偏好。 */
    val reminderVolume: ReminderVolume = ReminderVolume.STANDARD,
    /** 是否显示守护运行状态提示。 */
    val statusHintEnabled: Boolean = true,
    /** 屏幕采集授权（模拟）。 */
    val screenAuthorization: ScreenAuthorizationState = ScreenAuthorizationState.NOT_GRANTED,
    /** 家庭共享授权。P04 默认关闭，且不提供真实开启能力。 */
    val familySharingEnabled: Boolean = false,
)
