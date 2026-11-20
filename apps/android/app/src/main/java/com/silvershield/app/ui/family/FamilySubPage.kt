package com.silvershield.app.ui.family

import com.silvershield.app.navigation.AppRoutes

/**
 * 家属端二级页面（P07 / P08）。
 *
 * F02 / F04 / F09 / F06 / F08 / F11 不占用底部导航，从首页或列表进入；
 * 二级页隐藏底部导航并显示返回按钮。带 `{shareId}` 的路由按前缀匹配。
 */
enum class FamilySubPage(
    val route: String,
    val title: String,
    val pageCode: String,
) {
    Bind(
        route = AppRoutes.FAMILY_BIND,
        title = "绑定老人",
        pageCode = "F02",
    ),
    Relation(
        route = AppRoutes.FAMILY_RELATION,
        title = "关联关系",
        pageCode = "F04",
    ),
    Consent(
        route = AppRoutes.FAMILY_CONSENT,
        title = "共享授权",
        pageCode = "F09",
    ),
    RiskDetail(
        route = AppRoutes.FAMILY_RISK_DETAIL,
        title = "风险详情",
        pageCode = "F06",
    ),
    Cooperate(
        route = AppRoutes.FAMILY_COOPERATE,
        title = "协同处理",
        pageCode = "F07",
    ),
    HandlingDetail(
        route = AppRoutes.FAMILY_HANDLING,
        title = "处理详情",
        pageCode = "F08",
    ),
    Messages(
        route = AppRoutes.FAMILY_MESSAGES,
        title = "消息中心",
        pageCode = "F11",
    ),
    ;

    companion object {
        fun fromRoute(route: String?): FamilySubPage? {
            if (route == null) return null
            return entries.firstOrNull { entry ->
                entry.route == route ||
                    (entry.route.contains("{") &&
                        route.startsWith(entry.route.substringBefore("{")))
            }
        }
    }
}
