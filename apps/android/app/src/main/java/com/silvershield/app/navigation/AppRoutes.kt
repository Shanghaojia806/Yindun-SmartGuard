package com.silvershield.app.navigation

/**
 * 全局路由常量。
 *
 * 结构：
 * - 应用级路由：首次引导 E01、角色选择 E02；
 * - 老人端导航图 [ELDER_GRAPH]：守护 E03 / 记录 E08 / 我的 E12；
 * - 家属端导航图 [FAMILY_GRAPH]：家庭 F03 / 风险 F05 / 协同 F07 / 我的 F12。
 *
 * 老人端与家属端使用各自独立的导航图，不共用底部导航与返回栈。
 */
object AppRoutes {

    /** 首次引导页（E01） */
    const val ONBOARDING = "onboarding"

    /** 角色选择 / 本地模拟登录页（E02） */
    const val ROLE_SELECT = "role/select"

    /** 老人端导航图根路由 */
    const val ELDER_GRAPH = "elder"

    /** 家属端导航图根路由 */
    const val FAMILY_GRAPH = "family"

    /** 老人端 · 守护首页（E03） */
    const val ELDER_GUARD = "elder/guard"

    /** 老人端 · 风险记录（E08） */
    const val ELDER_RECORDS = "elder/records"

    /** 老人端 · 我的（E12） */
    const val ELDER_PROFILE = "elder/profile"

    /** 老人端 · 授权说明（E04，二级页） */
    const val ELDER_GUARD_AUTHORIZATION = "elder/guard/authorization"

    /** 老人端 · 运行状态（E05，二级页） */
    const val ELDER_GUARD_STATUS = "elder/guard/status"

    /** 老人端 · 守护设置（E13，二级页） */
    const val ELDER_GUARD_SETTINGS = "elder/guard/settings"

    /** 老人端 · 隐私与授权（E14，二级页） */
    const val ELDER_PRIVACY = "elder/privacy"

    /** 老人端 · P05 真实屏幕采集技术验证（PoC，二级页，仅本地验证） */
    const val ELDER_SCREEN_CAPTURE = "elder/guard/capture"

    /** 老人端 · 风险详情（E07，二级页），实际路由为 elder/risk/{riskId} */
    const val ELDER_RISK_DETAIL = "elder/risk"

    /** 老人端 · 家庭关系管理（E12 二级页，P07） */
    const val ELDER_FAMILY = "elder/family"

    /** 老人端 · 家庭关系详情（生成邀请 / 输入邀请码 / 解除，P07） */
    const val ELDER_FAMILY_RELATION = "elder/family/relation"

    /** 老人端 · 风险共享授权管理（E12 二级页，P07） */
    const val ELDER_FAMILY_CONSENT = "elder/family/consent"

    /** 授权说明页参数：同意后是否直接启动守护 */
    const val ARG_AUTO_START = "autoStart"

    /** 风险详情页参数：风险事件 id */
    const val ARG_RISK_ID = "riskId"

    /** 构造风险详情页路由。 */
    fun elderRiskDetailRoute(riskId: String): String = "$ELDER_RISK_DETAIL/$riskId"

    /** 构造授权说明页路由。 */
    fun elderAuthorizationRoute(autoStart: Boolean): String =
        "$ELDER_GUARD_AUTHORIZATION?$ARG_AUTO_START=$autoStart"

    /** 家属端 · 家庭首页（F03） */
    const val FAMILY_HOME = "family/home"

    /** 家属端 · 风险列表（F05） */
    const val FAMILY_RISKS = "family/risks"

    /** 家属端 · 协同处理（F07） */
    const val FAMILY_COOPERATION = "family/cooperation"

    /** 家属端 · 个人中心（F12） */
    const val FAMILY_PROFILE = "family/profile"

    /** 家属端 · 绑定老人（F02，二级页，P07） */
    const val FAMILY_BIND = "family/bind"

    /** 家属端 · 关联关系（F04，二级页，P07） */
    const val FAMILY_RELATION = "family/relation"

    /** 家属端 · 共享授权（F09，二级页，P07） */
    const val FAMILY_CONSENT = "family/consent"

    /** 家属端 · 风险详情（F06，P08），实际路由为 family/risk/{shareId} */
    const val FAMILY_RISK_DETAIL = "family/risk/{shareId}"

    /** 家属端 · 协同处理动作页（F07，P08） */
    const val FAMILY_COOPERATE = "family/cooperate/{shareId}"

    /** 家属端 · 处理详情（F08，P08） */
    const val FAMILY_HANDLING = "family/handling/{shareId}"

    /** 家属端 · 消息中心（F11，P08） */
    const val FAMILY_MESSAGES = "family/messages"

    /** 共享事件参数：分享记录 id */
    const val ARG_SHARE_ID = "shareId"

    /** 构造家属端风险详情路由。 */
    fun familyRiskDetailRoute(shareId: String): String = "family/risk/$shareId"

    /** 构造家属端协同处理动作页路由。 */
    fun familyCooperateRoute(shareId: String): String = "family/cooperate/$shareId"

    /** 构造家属端处理详情路由。 */
    fun familyHandlingRoute(shareId: String): String = "family/handling/$shareId"

    /**
     * 设计系统组件预览页（P02）。
     *
     * 仅 Debug 构建注册，用于开发阶段集中检查通用组件；Release 构建不注册该路由，
     * 启动页也不会显示入口，确保开发预览不会出现在正式包中。
     */
    const val DEV_GALLERY = "dev/gallery"
}
