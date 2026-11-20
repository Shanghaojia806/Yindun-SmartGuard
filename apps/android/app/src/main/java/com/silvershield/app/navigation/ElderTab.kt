package com.silvershield.app.navigation

/**
 * 老人端一级入口（底部导航）。
 *
 * 路由、文案与页面说明集中在本枚举，页面与导航栏均从此读取，
 * 避免在 Composable 中散落硬编码字符串与路由判断。
 */
enum class ElderTab(
    val route: String,
    val label: String,
    val pageCode: String,
    val title: String,
    val description: String,
) {
    Guard(
        route = AppRoutes.ELDER_GUARD,
        label = "守护",
        pageCode = "E03",
        title = "守护首页",
        description = "开启守护后，本机将识别短视频中的诈骗风险，并及时提醒您。",
    ),
    Records(
        route = AppRoutes.ELDER_RECORDS,
        label = "记录",
        pageCode = "E08",
        title = "风险记录",
        description = "这里会保存本机识别到的风险事件，方便您随时回看与处理。",
    ),
    Profile(
        route = AppRoutes.ELDER_PROFILE,
        label = "我的",
        pageCode = "E12",
        title = "我的",
        description = "在这里查看当前使用身份、家人共享情况与常用设置。",
    ),
    ;

    companion object {
        /** 老人端起始页面 */
        val START: ElderTab = Guard

        /** 根据路由还原一级入口，用于底部导航选中态；未知路由回到起始页。 */
        fun fromRoute(route: String?): ElderTab = entries.firstOrNull { it.route == route } ?: START
    }
}
