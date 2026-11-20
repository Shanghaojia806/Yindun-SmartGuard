package com.silvershield.app.navigation

/**
 * 老人端二级页面（P04 起）。
 *
 * 二级页面位于老人端内部导航图，底部导航隐藏、顶部显示返回按钮，
 * 但状态仍由同一个 GuardViewModel 提供，避免各页面维护独立状态。
 */
enum class ElderSubPage(
    val route: String,
    val title: String,
    val pageCode: String,
    /** 路由是否带路径参数（例如 elder/risk/{riskId}） */
    val hasPathArguments: Boolean = false,
) {
    Authorization(
        route = AppRoutes.ELDER_GUARD_AUTHORIZATION,
        title = "授权说明",
        pageCode = "E04",
    ),
    RunStatus(
        route = AppRoutes.ELDER_GUARD_STATUS,
        title = "运行状态",
        pageCode = "E05",
    ),
    GuardSettings(
        route = AppRoutes.ELDER_GUARD_SETTINGS,
        title = "守护设置",
        pageCode = "E13",
    ),
    Privacy(
        route = AppRoutes.ELDER_PRIVACY,
        title = "隐私与授权",
        pageCode = "E14",
    ),
    ScreenCapturePoc(
        route = AppRoutes.ELDER_SCREEN_CAPTURE,
        title = "真实采集验证",
        pageCode = "P05",
    ),
    RiskDetail(
        route = AppRoutes.ELDER_RISK_DETAIL,
        title = "风险详情",
        pageCode = "E07",
        hasPathArguments = true,
    ),
    FamilyManagement(
        route = AppRoutes.ELDER_FAMILY,
        title = "家庭关系",
        pageCode = "E12",
    ),
    FamilyRelationManagement(
        route = AppRoutes.ELDER_FAMILY_RELATION,
        title = "家庭关系管理",
        pageCode = "E12",
    ),
    FamilyConsentManagement(
        route = AppRoutes.ELDER_FAMILY_CONSENT,
        title = "风险共享授权",
        pageCode = "E12",
    ),
    ;

    companion object {
        /** 根据路由还原二级页面；带 query 参数或路径参数的页面按基础路径匹配。 */
        fun fromRoute(route: String?): ElderSubPage? {
            val baseRoute = route?.substringBefore("?") ?: return null
            return entries.firstOrNull { page ->
                page.route == baseRoute ||
                    (page.hasPathArguments && baseRoute.startsWith("${page.route}/"))
            }
        }
    }
}
