package com.silvershield.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silvershield.app.BuildConfig
import com.silvershield.app.role.AppEntry
import com.silvershield.app.role.AppViewModel
import com.silvershield.app.role.RoleRepository
import com.silvershield.app.role.UserRole
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.elder.ElderAppScreen
import com.silvershield.app.ui.family.FamilyAppScreen
import com.silvershield.app.ui.gallery.DesignSystemGalleryScreen
import com.silvershield.app.ui.onboarding.OnboardingScreen
import com.silvershield.app.ui.onboarding.RoleSelectScreen

/**
 * 应用级导航宿主。
 *
 * 职责划分：
 * - 本文件负责"引导 → 角色选择 → 某个角色端"的顶层跳转；
 * - 老人端与家属端各自持有独立 NavHost（见 [ElderAppScreen] / [FamilyAppScreen]），
 *   两端路由与返回栈互不共享。
 *
 * 启动入口由 DataStore 中的本地角色状态决定：
 * 未完成引导或没有角色 → 首次引导；否则直接进入上次角色端。
 */
@Composable
fun SilverShieldNavHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appViewModel: AppViewModel = viewModel(
        factory = AppViewModel.factory(RoleRepository(context.applicationContext)),
    )
    val uiState by appViewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.loading) {
        LoadingState(
            modifier = modifier.fillMaxSize(),
            message = "正在读取本机使用身份…",
        )
        return
    }

    RoleAwareNavHost(
        entry = uiState.state.entry,
        currentRole = uiState.state.role,
        onSelectRole = appViewModel::confirmRole,
        modifier = modifier,
    )
}

/**
 * 角色感知的顶层导航图。
 *
 * 起始页只在首次进入时计算一次（[remember]），避免角色状态变化导致导航图被重建。
 */
@Composable
private fun RoleAwareNavHost(
    entry: AppEntry,
    currentRole: UserRole?,
    onSelectRole: (UserRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    // 起始页只在本次进入时计算一次：后续角色切换由显式导航完成，
    // 避免角色状态变化导致导航图被重建、返回栈被意外重置。
    val startDestination = remember {
        when (entry) {
            AppEntry.Onboarding -> AppRoutes.ONBOARDING
            is AppEntry.RoleHome -> entry.role.homeRoute()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppRoutes.ONBOARDING) {
            OnboardingScreen(
                onContinue = { navController.navigate(AppRoutes.ROLE_SELECT) },
                onOpenGallery = if (BuildConfig.DEBUG) {
                    { navController.navigate(AppRoutes.DEV_GALLERY) }
                } else {
                    null
                },
            )
        }

        composable(AppRoutes.ROLE_SELECT) {
            val goHome = { role: UserRole ->
                navController.navigate(role.homeRoute()) {
                    // 清空整个返回栈，确保切换角色后无法返回上一个角色的页面
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }

            // 从"我的"进入切换身份时，返回栈已清空；此时系统返回键回到当前角色首页，
            // 避免把用户直接退出应用。
            BackHandler(enabled = currentRole != null) {
                currentRole?.let(goHome)
            }

            RoleSelectScreen(
                initialRole = currentRole,
                onConfirm = { role ->
                    onSelectRole(role)
                    goHome(role)
                },
                onBack = if (currentRole != null) {
                    { goHome(currentRole) }
                } else {
                    { navController.popBackStack() }
                },
            )
        }

        composable(AppRoutes.ELDER_GRAPH) {
            ElderAppScreen(
                onSwitchRole = {
                    navController.navigate(AppRoutes.ROLE_SELECT) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(AppRoutes.FAMILY_GRAPH) {
            FamilyAppScreen(
                onSwitchRole = {
                    navController.navigate(AppRoutes.ROLE_SELECT) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        if (BuildConfig.DEBUG) {
            composable(AppRoutes.DEV_GALLERY) {
                DesignSystemGalleryScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
