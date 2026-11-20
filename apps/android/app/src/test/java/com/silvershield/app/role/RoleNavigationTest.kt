package com.silvershield.app.role

import com.silvershield.app.navigation.ElderTab
import com.silvershield.app.navigation.FamilyTab
import com.silvershield.app.navigation.homeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P03 角色模型与导航入口单元测试。
 *
 * 仅验证纯逻辑（角色解析、重启入口判定、路由定义），不依赖 Android 运行时。
 */
class RoleNavigationTest {

    @Test
    fun `未完成引导时进入首次引导`() {
        val state = LocalRoleState(onboardingCompleted = false, role = UserRole.Elder)
        assertEquals(AppEntry.Onboarding, state.entry)
    }

    @Test
    fun `没有角色时进入首次引导`() {
        val state = LocalRoleState(onboardingCompleted = true, role = null)
        assertEquals(AppEntry.Onboarding, state.entry)
    }

    @Test
    fun `完成引导且有角色时恢复对应角色端`() {
        val elderState = LocalRoleState(onboardingCompleted = true, role = UserRole.Elder)
        assertEquals(AppEntry.RoleHome(UserRole.Elder), elderState.entry)

        val familyState = LocalRoleState(onboardingCompleted = true, role = UserRole.Family)
        assertEquals(AppEntry.RoleHome(UserRole.Family), familyState.entry)
    }

    @Test
    fun `角色存储键可稳定还原`() {
        UserRole.values().forEach { role ->
            assertEquals(role, UserRole.fromStorageKey(role.storageKey))
        }
        assertNull(UserRole.fromStorageKey(null))
        assertNull(UserRole.fromStorageKey(""))
        assertNull(UserRole.fromStorageKey("unknown"))
    }

    @Test
    fun `角色端根路由互不相同`() {
        assertNotEquals(UserRole.Elder.homeRoute(), UserRole.Family.homeRoute())
        assertTrue(UserRole.Elder.homeRoute().startsWith("elder"))
        assertTrue(UserRole.Family.homeRoute().startsWith("family"))
    }

    @Test
    fun `老人端三个一级入口路由唯一且以老人端前缀开头`() {
        val routes = ElderTab.entries.map { it.route }
        assertEquals(3, routes.size)
        assertEquals(routes.size, routes.toSet().size)
        assertTrue(routes.all { it.startsWith("elder/") })
    }

    @Test
    fun `家属端四个一级入口路由唯一且以家属端前缀开头`() {
        val routes = FamilyTab.entries.map { it.route }
        assertEquals(4, routes.size)
        assertEquals(routes.size, routes.toSet().size)
        assertTrue(routes.all { it.startsWith("family/") })
    }

    @Test
    fun `老人端与家属端路由互不冲突`() {
        val elderRoutes = ElderTab.entries.map { it.route }.toSet()
        val familyRoutes = FamilyTab.entries.map { it.route }.toSet()
        assertTrue(elderRoutes.intersect(familyRoutes).isEmpty())
    }

    @Test
    fun `底部导航选中态按路由还原且未知路由回到起始页`() {
        assertEquals(ElderTab.Records, ElderTab.fromRoute(ElderTab.Records.route))
        assertEquals(ElderTab.START, ElderTab.fromRoute("unknown"))
        assertEquals(FamilyTab.Cooperation, FamilyTab.fromRoute(FamilyTab.Cooperation.route))
        assertEquals(FamilyTab.START, FamilyTab.fromRoute(null))
    }
}
