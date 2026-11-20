package com.silvershield.app.navigation

import com.silvershield.app.role.UserRole

/**
 * 角色 → 角色端根路由。
 *
 * 导航层通过角色模型决定入口，而不是通过页面名称或界面文案判断；
 * 新增角色时只需在此补充映射与对应导航图。
 */
fun UserRole.homeRoute(): String = when (this) {
    UserRole.Elder -> AppRoutes.ELDER_GRAPH
    UserRole.Family -> AppRoutes.FAMILY_GRAPH
}
