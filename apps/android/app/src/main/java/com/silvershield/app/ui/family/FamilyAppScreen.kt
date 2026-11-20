package com.silvershield.app.ui.family

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.silvershield.app.family.ConsentViewModel
import com.silvershield.app.family.FakeConsentRepository
import com.silvershield.app.family.FakeFamilyMessageRepository
import com.silvershield.app.family.FakeFamilyRepository
import com.silvershield.app.family.FakeHandlingRecordRepository
import com.silvershield.app.family.FakeSharedRiskEventRepository
import com.silvershield.app.family.FamilyEventViewModel
import com.silvershield.app.family.FamilyRiskViewModel
import com.silvershield.app.family.FamilyViewModel
import com.silvershield.app.family.RoomFamilyLocalStore
import com.silvershield.app.family.RoomFamilyRiskLocalStore
import com.silvershield.app.guard.risk.RoomRiskEventRepository
import com.silvershield.app.navigation.AppRoutes
import com.silvershield.app.navigation.FamilyTab
import com.silvershield.app.role.UserRole
import com.silvershield.app.ui.navigation.RoleBottomBar
import com.silvershield.app.ui.navigation.toNavItem
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Spacing

/**
 * 家属端导航容器（独立导航图）。
 *
 * 结构与老人端完全独立：
 * - 底部导航：家庭 / 风险 / 协同 / 我的；
 * - P07 起在「我的」下注册 F02/F04/F09 二级页，二级页隐藏底部导航并显示返回按钮；
 * - 家庭关系与授权仓库由容器统一创建，三个二级页共用同一份本地状态。
 *
 * @param onSwitchRole 切换使用身份；由应用级导航清空返回栈
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyAppScreen(
    onSwitchRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = FamilyTab.fromRoute(backStackEntry?.destination?.route)
    val currentSubPage = FamilySubPage.fromRoute(backStackEntry?.destination?.route)
    val tokens = SilverShieldTokens.colors

    // 家庭关系与授权共用同一 Room 存储；数据随数据库持久化，重启后恢复。
    val familyStore = remember { RoomFamilyLocalStore(context.applicationContext) }
    val familyRepository = remember(familyStore) { FakeFamilyRepository(familyStore) }
    val consentRepository = remember(familyStore) { FakeConsentRepository(familyStore) }

    // P08：共享事件 / 协同处理 / 消息中心共用 P06 风险事件与 P07 家庭数据。
    val riskEventRepository = remember { RoomRiskEventRepository(context.applicationContext) }
    val riskStore = remember { RoomFamilyRiskLocalStore(context.applicationContext) }
    val sharedRiskRepository = remember(riskStore, familyStore, riskEventRepository) {
        FakeSharedRiskEventRepository(riskStore, familyStore, riskEventRepository.events)
    }
    val handlingRepository = remember(riskStore, sharedRiskRepository) {
        FakeHandlingRecordRepository(riskStore, sharedRiskRepository)
    }
    val messageRepository = remember(riskStore, familyStore, riskEventRepository) {
        FakeFamilyMessageRepository(riskStore, familyStore, riskEventRepository.events)
    }
    val riskViewModel: FamilyRiskViewModel = viewModel(
        factory = remember(sharedRiskRepository, handlingRepository, messageRepository) {
            FamilyRiskViewModel.factory(
                sharedRiskRepository,
                handlingRepository,
                messageRepository,
            )
        },
    )
    val riskUiState by riskViewModel.uiState.collectAsStateWithLifecycle()

    val familyViewModel: FamilyViewModel = viewModel(
        factory = remember(familyRepository) {
            FamilyViewModel.factory(UserRole.Family, familyRepository)
        },
    )
    val consentViewModel: ConsentViewModel = viewModel(
        factory = remember(consentRepository, familyRepository) {
            ConsentViewModel.familyFactory(consentRepository, familyRepository)
        },
    )
    val familyUiState by familyViewModel.uiState.collectAsStateWithLifecycle()
    val consentUiState by consentViewModel.uiState.collectAsStateWithLifecycle()

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BackHandler(enabled = currentSubPage != null || currentTab != FamilyTab.START) {
        if (currentSubPage != null) {
            navController.popBackStack()
        } else {
            navigateToTab(FamilyTab.START.route)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (currentSubPage != null) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = currentSubPage?.title ?: currentTab.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(Spacing.xxs),
                        color = tokens.accentLight,
                        modifier = Modifier.padding(end = Spacing.md),
                    ) {
                        Text(
                            text = "家属端",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            // 二级页面隐藏底部导航，避免误认为可以在一级入口之间跳转。
            if (currentSubPage == null) {
                RoleBottomBar(
                    items = FamilyTab.entries.map { it.toNavItem() },
                    selectedRoute = currentTab.route,
                    onSelect = { item -> navigateToTab(item.route) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FamilyTab.START.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(FamilyTab.Home.route) {
                FamilyHomeScreen(
                    uiState = riskUiState,
                    onOpenBind = { navController.navigate(AppRoutes.FAMILY_BIND) },
                    onOpenRelation = { navController.navigate(AppRoutes.FAMILY_RELATION) },
                    onOpenRisks = { navigateToTab(FamilyTab.Risks.route) },
                    onOpenCooperation = { navigateToTab(FamilyTab.Cooperation.route) },
                    onOpenMessages = { navController.navigate(AppRoutes.FAMILY_MESSAGES) },
                    onOpenEvent = { shareId ->
                        navController.navigate(AppRoutes.familyRiskDetailRoute(shareId))
                    },
                    onOpenCooperate = { shareId ->
                        navController.navigate(AppRoutes.familyCooperateRoute(shareId))
                    },
                    onRetry = riskViewModel::retry,
                )
            }
            composable(FamilyTab.Risks.route) {
                FamilyRisksScreen(
                    uiState = riskUiState,
                    onOpenEvent = { shareId ->
                        navController.navigate(AppRoutes.familyRiskDetailRoute(shareId))
                    },
                    onOpenBind = { navController.navigate(AppRoutes.FAMILY_BIND) },
                    onOpenRelation = { navController.navigate(AppRoutes.FAMILY_RELATION) },
                    onRetry = riskViewModel::retry,
                )
            }
            composable(FamilyTab.Cooperation.route) {
                FamilyCooperationScreen(
                    uiState = riskUiState,
                    onOpenCooperate = { shareId ->
                        navController.navigate(AppRoutes.familyCooperateRoute(shareId))
                    },
                    onOpenBind = { navController.navigate(AppRoutes.FAMILY_BIND) },
                    onOpenRisks = { navigateToTab(FamilyTab.Risks.route) },
                    onRetry = riskViewModel::retry,
                )
            }
            composable(FamilyTab.Profile.route) {
                FamilyProfileScreen(
                    uiState = familyUiState,
                    onOpenBind = { navController.navigate(AppRoutes.FAMILY_BIND) },
                    onOpenRelation = { navController.navigate(AppRoutes.FAMILY_RELATION) },
                    onOpenConsent = { navController.navigate(AppRoutes.FAMILY_CONSENT) },
                    onSwitchRole = onSwitchRole,
                )
            }
            composable(AppRoutes.FAMILY_BIND) {
                FamilyBindScreen(
                    uiState = familyUiState,
                    onGenerateInvite = familyViewModel::generateInvite,
                    onLookupInvite = familyViewModel::lookupInvite,
                    onAcceptInvite = familyViewModel::acceptInvite,
                    onRejectInvite = familyViewModel::rejectInvite,
                    onCancelInvite = familyViewModel::cancelInvite,
                    onOpenRelation = { navController.navigate(AppRoutes.FAMILY_RELATION) },
                    onConsumeMessage = familyViewModel::consumeMessage,
                    onClearPreview = familyViewModel::clearPreview,
                    onRetry = familyViewModel::retry,
                )
            }
            composable(AppRoutes.FAMILY_RELATION) {
                FamilyRelationScreen(
                    uiState = familyUiState,
                    onOpenBind = { navController.navigate(AppRoutes.FAMILY_BIND) },
                    onCancelInvite = familyViewModel::cancelInvite,
                    onRevokeRelation = familyViewModel::revokeRelation,
                    onConsumeMessage = familyViewModel::consumeMessage,
                    onRetry = familyViewModel::retry,
                )
            }
            composable(AppRoutes.FAMILY_CONSENT) {
                FamilyConsentScreen(
                    uiState = consentUiState,
                    onRefresh = consentViewModel::refresh,
                    onConsumeMessage = consentViewModel::consumeMessage,
                )
            }

            composable(
                route = AppRoutes.FAMILY_RISK_DETAIL,
                arguments = listOf(navArgument(AppRoutes.ARG_SHARE_ID) { type = NavType.StringType }),
            ) { entry ->
                val shareId = entry.arguments?.getString(AppRoutes.ARG_SHARE_ID).orEmpty()
                val eventViewModel: FamilyEventViewModel = viewModel(
                    key = "risk-$shareId",
                    factory = remember(shareId, sharedRiskRepository, handlingRepository, messageRepository) {
                        FamilyEventViewModel.factory(
                            shareId,
                            sharedRiskRepository,
                            handlingRepository,
                            messageRepository,
                        )
                    },
                )
                val eventUiState by eventViewModel.uiState.collectAsStateWithLifecycle()
                FamilyRiskDetailScreen(
                    uiState = eventUiState,
                    onOpenCooperate = { id ->
                        navController.navigate(AppRoutes.familyCooperateRoute(id))
                    },
                    onOpenHandling = { id ->
                        navController.navigate(AppRoutes.familyHandlingRoute(id))
                    },
                    onRetry = eventViewModel::refresh,
                )
            }

            composable(
                route = AppRoutes.FAMILY_COOPERATE,
                arguments = listOf(navArgument(AppRoutes.ARG_SHARE_ID) { type = NavType.StringType }),
            ) { entry ->
                val shareId = entry.arguments?.getString(AppRoutes.ARG_SHARE_ID).orEmpty()
                val eventViewModel: FamilyEventViewModel = viewModel(
                    key = "cooperate-$shareId",
                    factory = remember(shareId, sharedRiskRepository, handlingRepository, messageRepository) {
                        FamilyEventViewModel.factory(
                            shareId,
                            sharedRiskRepository,
                            handlingRepository,
                            messageRepository,
                        )
                    },
                )
                val eventUiState by eventViewModel.uiState.collectAsStateWithLifecycle()
                FamilyCooperateScreen(
                    uiState = eventUiState,
                    onUpdateStatus = eventViewModel::updateStatus,
                    onOpenHandling = { id ->
                        navController.navigate(AppRoutes.familyHandlingRoute(id))
                    },
                    onRetry = eventViewModel::refresh,
                )
            }

            composable(
                route = AppRoutes.FAMILY_HANDLING,
                arguments = listOf(navArgument(AppRoutes.ARG_SHARE_ID) { type = NavType.StringType }),
            ) { entry ->
                val shareId = entry.arguments?.getString(AppRoutes.ARG_SHARE_ID).orEmpty()
                val eventViewModel: FamilyEventViewModel = viewModel(
                    key = "handling-$shareId",
                    factory = remember(shareId, sharedRiskRepository, handlingRepository, messageRepository) {
                        FamilyEventViewModel.factory(
                            shareId,
                            sharedRiskRepository,
                            handlingRepository,
                            messageRepository,
                        )
                    },
                )
                val eventUiState by eventViewModel.uiState.collectAsStateWithLifecycle()
                FamilyHandlingDetailScreen(
                    uiState = eventUiState,
                    onOpenCooperate = { id ->
                        navController.navigate(AppRoutes.familyCooperateRoute(id))
                    },
                    onRetry = eventViewModel::refresh,
                )
            }

            composable(AppRoutes.FAMILY_MESSAGES) {
                FamilyMessagesScreen(
                    uiState = riskUiState,
                    onMarkRead = riskViewModel::markMessageRead,
                    onMarkAllRead = riskViewModel::markAllMessagesRead,
                    onSetNetworkMode = riskViewModel::setNetworkMode,
                    onOpenEvent = { shareId ->
                        navController.navigate(AppRoutes.familyRiskDetailRoute(shareId))
                    },
                    onRetry = riskViewModel::retry,
                )
            }
        }
    }
}
