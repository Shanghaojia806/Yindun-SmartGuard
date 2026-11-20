package com.silvershield.app.navigation

/**
 * 家属端一级入口（底部导航）。
 *
 * 与老人端完全独立：路由前缀、入口数量与选中态逻辑均不共用。
 */
enum class FamilyTab(
    val route: String,
    val label: String,
    val pageCode: String,
    val title: String,
    val description: String,
) {
    Home(
        route = AppRoutes.FAMILY_HOME,
        label = "家庭",
        pageCode = "F03",
        title = "家庭首页",
        description = "这里汇总您绑定的家人与需要关注的待处理事项。",
    ),
    Risks(
        route = AppRoutes.FAMILY_RISKS,
        label = "风险",
        pageCode = "F05",
        title = "风险列表",
        description = "仅展示老人授权共享的风险事件，未授权的内容不会出现在这里。",
    ),
    Cooperation(
        route = AppRoutes.FAMILY_COOPERATION,
        label = "协同",
        pageCode = "F07",
        title = "协同处理",
        description = "查看需要协助处理的事件，并记录联系与处理进展。",
    ),
    Profile(
        route = AppRoutes.FAMILY_PROFILE,
        label = "我的",
        pageCode = "F12",
        title = "个人中心",
        description = "在这里查看当前使用身份、绑定关系与偏好设置。",
    ),
    ;

    companion object {
        /** 家属端起始页面 */
        val START: FamilyTab = Home

        /** 根据路由还原一级入口，用于底部导航选中态；未知路由回到起始页。 */
        fun fromRoute(route: String?): FamilyTab = entries.firstOrNull { it.route == route } ?: START
    }
}
