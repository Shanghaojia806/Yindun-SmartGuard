package com.silvershield.app.role

/**
 * 本地使用身份（角色）模型。
 *
 * 当前版本只有两种使用身份：
 * - [Elder] 老人：被守护对象，是数据主体与授权主体；
 * - [Family] 家属：协同处理者，权限来自老人授权。
 *
 * 角色定义独立于任何页面名称，页面路由不得通过标题或页面名称反推角色；
 * 同时使用 [storageKey] 与 DataStore 解耦，便于后续扩展更多角色或"一个账号多种身份"。
 */
enum class UserRole(
    /** 本地持久化使用的稳定键，禁止随意变更 */
    val storageKey: String,
    /** 界面展示名称 */
    val label: String,
) {
    Elder("elder", "老人"),
    Family("family", "家属"),
    ;

    companion object {
        /** 从本地存储键还原角色；未知或空值返回 null，由调用方决定入口。 */
        fun fromStorageKey(key: String?): UserRole? = values().firstOrNull { it.storageKey == key }
    }
}
