package com.silvershield.app.ui.elder

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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.silvershield.app.family.ElderConsentViewModel
import com.silvershield.app.family.FakeConsentRepository
import com.silvershield.app.family.FakeFamilyRepository
import com.silvershield.app.family.FamilyViewModel
import com.silvershield.app.family.RoomFamilyLocalStore
import com.silvershield.app.guard.DataStoreGuardSettingsStore
import com.silvershield.app.guard.FakeGuardService
import com.silvershield.app.guard.GuardRepository
import com.silvershield.app.guard.GuardViewModel
import com.silvershield.app.guard.capture.MediaProjectionScreenCaptureController
import com.silvershield.app.guard.capture.ScreenCaptureViewModel
import com.silvershield.app.guard.risk.RoomRiskEventRepository
import com.silvershield.app.navigation.AppRoutes
import com.silvershield.app.navigation.ElderSubPage
import com.silvershield.app.navigation.ElderTab
import com.silvershield.app.role.UserRole
import com.silvershield.app.ui.components.LoadingState
import com.silvershield.app.ui.components.rememberVoiceAnnouncer
import com.silvershield.app.ui.navigation.RoleBottomBar
import com.silvershield.app.ui.navigation.toNavItem
import com.silvershield.app.ui.theme.SilverShieldTokens
import com.silvershield.app.ui.theme.Spacing

/**
 * 老人端导航容器（独立导航图）。
 *
 * 结构与家属端完全独立：
 * - 底部导航：守护 / 记录 / 我的；
 * - P04 起在内部导航图注册 E04/E05/E13/E14 二级页，隐藏底部导航并显示返回按钮；
 * - 五個守护页面共用同一份 [GuardViewModel] 状态，避免各页面状态不一致。
 *
 * @param onSwitchRole 切换使用身份；由应用级导航清空返回栈
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderAppScreen(
    onSwitchRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = ElderTab.fromRoute(currentRoute)
    val currentSubPage = ElderSubPage.fromRoute(currentRoute)
    val tokens = SilverShieldTokens.colors

    // 统一守护状态：FakeGuardService → GuardRepository → GuardViewModel → 五个页面。
    val guardViewModel: GuardViewModel = viewModel(
        factory = remember {
            GuardViewModel.factory(
                GuardRepository(
                    service = FakeGuardService(),
                    settingsStore = DataStoreGuardSettingsStore(context.applicationContext),
                ),
            )
        },
    )
    val guardUiState by guardViewModel.uiState.collectAsStateWithLifecycle()

    // 风险记录仓库：Room 单例，记录页与详情页共享同一数据源。
    val riskRepository = remember { RoomRiskEventRepository(context.applicationContext) }

    // P07：家庭关系与共享授权。与风险记录共用同一个数据库（version 2）。
    val familyStore = remember { RoomFamilyLocalStore(context.applicationContext) }
    val familyRepository = remember(familyStore) { FakeFamilyRepository(familyStore) }
    val consentRepository = remember(familyStore) { FakeConsentRepository(familyStore) }
    val familyViewModel: FamilyViewModel = viewModel(
        factory = remember(familyRepository) {
            FamilyViewModel.factory(UserRole.Elder, familyRepository)
        },
    )
    val elderConsentViewModel: ElderConsentViewModel = viewModel(
        factory = remember(consentRepository, familyRepository) {
            ElderConsentViewModel.factory(consentRepository, familyRepository)
        },
    )
    val familyUiState by familyViewModel.uiState.collectAsStateWithLifecycle()
    val consentUiState by elderConsentViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(guardUiState.message) {
        val message = guardUiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        guardViewModel.consumeMessage()
    }

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BackHandler(enabled = currentSubPage == null && currentTab != ElderTab.START) {
        navigateToTab(ElderTab.START.route)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                            text = "老人端",
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
            // 二级页面隐藏底部导航，避免老人误以为可以在一级入口间跳转。
            if (currentSubPage == null) {
                RoleBottomBar(
                    items = ElderTab.entries.map { it.toNavItem() },
                    selectedRoute = currentTab.route,
                    onSelect = { item -> navigateToTab(item.route) },
                )
            }
        },
    ) { innerPadding ->
        if (guardUiState.loading) {
            LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                message = "正在读取守护状态…",
            )
            return@Scaffold
        }

        NavHost(
            navController = navController,
            startDestination = ElderTab.START.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            // 一级入口之间不使用转场动画，避免老人误以为页面"闪了一下"
        ) {
            composable(ElderTab.Guard.route) {
                ElderGuardScreen(
                    uiState = guardUiState,
                    onStartGuard = {
                        navController.navigate(AppRoutes.elderAuthorizationRoute(autoStart = true))
                    },
                    onStopGuard = guardViewModel::stopGuard,
                    onOpenAuthorization = {
                        navController.navigate(AppRoutes.elderAuthorizationRoute(autoStart = false))
                    },
                    onOpenRunStatus = { navController.navigate(AppRoutes.ELDER_GUARD_STATUS) },
                    onOpenSettings = { navController.navigate(AppRoutes.ELDER_GUARD_SETTINGS) },
                    onOpenPrivacy = { navController.navigate(AppRoutes.ELDER_PRIVACY) },
                )
            }

            composable(ElderTab.Records.route) {
                val recordsViewModel: RiskRecordsViewModel = viewModel(
                    factory = remember(riskRepository) {
                        RiskRecordsViewModel.factory(riskRepository)
                    },
                )
                val recordsUiState by recordsViewModel.uiState.collectAsStateWithLifecycle()
                val speak = rememberVoiceAnnouncer(
                    onUnavailable = { recordsViewModel.notify("当前设备没有可用的中文语音引擎") },
                    onFailed = { recordsViewModel.notify("语音播报失败，请检查系统语音包后重试") },
                )
                ElderRecordsScreen(
                    uiState = recordsUiState,
                    reminderEnabled = guardUiState.preferences.riskReminderEnabled,
                    voiceEnabled = guardUiState.preferences.voiceReminderEnabled,
                    onLevelFilterChange = recordsViewModel::setLevelFilter,
                    onTypeFilterChange = recordsViewModel::setTypeFilter,
                    onOpenDetail = { riskId ->
                        navController.navigate(AppRoutes.elderRiskDetailRoute(riskId))
                    },
                    onAddMockEvent = recordsViewModel::addMockEvent,
                    onDelete = recordsViewModel::delete,
                    onClearAll = recordsViewModel::clearAll,
                    onSpeak = {
                        recordsUiState.latestAlertEvent?.let { event ->
                            speak("${event.title}。${event.description}")
                        }
                    },
                    onConsumeMessage = recordsViewModel::consumeMessage,
                    onRetryLoad = recordsViewModel::retryLoad,
                )
            }

            composable(ElderTab.Profile.route) {
                ElderProfileScreen(
                    familyUiState = familyUiState,
                    onOpenFamily = { navController.navigate(AppRoutes.ELDER_FAMILY) },
                    onSwitchRole = onSwitchRole,
                )
            }

            composable(
                route = "${AppRoutes.ELDER_GUARD_AUTHORIZATION}?" +
                    "${AppRoutes.ARG_AUTO_START}={${AppRoutes.ARG_AUTO_START}}",
                arguments = listOf(
                    navArgument(AppRoutes.ARG_AUTO_START) {
                        type = NavType.BoolType
                        defaultValue = true
                    },
                ),
            ) { entry ->
                val autoStart = entry.arguments?.getBoolean(AppRoutes.ARG_AUTO_START) ?: true
                ElderAuthorizationScreen(
                    autoStart = autoStart,
                    onAgree = {
                        if (autoStart) {
                            guardViewModel.grantAuthorizationAndStart()
                        } else {
                            guardViewModel.grantAuthorizationOnly()
                        }
                        navController.popBackStack()
                    },
                    onDecline = {
                        guardViewModel.showMessage("已取消，未开启守护")
                        navController.popBackStack()
                    },
                )
            }

            composable(AppRoutes.ELDER_GUARD_STATUS) {
                ElderRunStatusScreen(
                    uiState = guardUiState,
                    onStartGuard = {
                        navController.navigate(AppRoutes.elderAuthorizationRoute(autoStart = true))
                    },
                    onStopGuard = guardViewModel::stopGuard,
                    onSelectScenario = guardViewModel::selectScenario,
                    onOpenCapturePoc = { navController.navigate(AppRoutes.ELDER_SCREEN_CAPTURE) },
                )
            }

            composable(AppRoutes.ELDER_GUARD_SETTINGS) {
                ElderGuardSettingsScreen(
                    preferences = guardUiState.preferences,
                    onRiskReminderChange = guardViewModel::setRiskReminderEnabled,
                    onVoiceReminderChange = guardViewModel::setVoiceReminderEnabled,
                    onVolumeChange = guardViewModel::setReminderVolume,
                    onStatusHintChange = guardViewModel::setStatusHintEnabled,
                )
            }

            composable(AppRoutes.ELDER_PRIVACY) {
                ElderPrivacyScreen(
                    preferences = guardUiState.preferences,
                    onOpenAuthorization = {
                        navController.navigate(AppRoutes.elderAuthorizationRoute(autoStart = false))
                    },
                    onRevokeAuthorization = guardViewModel::revokeScreenAuthorization,
                    onOpenRecords = { navigateToTab(ElderTab.Records.route) },
                )
            }

            composable(AppRoutes.ELDER_SCREEN_CAPTURE) {
                // P05：真实采集拥有独立 ViewModel 与独立状态源，不复用 FakeGuardService。
                val captureViewModel: ScreenCaptureViewModel = viewModel(
                    factory = remember {
                        ScreenCaptureViewModel.factory(
                            MediaProjectionScreenCaptureController(context.applicationContext),
                        )
                    },
                )
                ScreenCapturePocScreen(viewModel = captureViewModel)
            }

            composable(AppRoutes.ELDER_FAMILY) {
                ElderFamilyScreen(
                    familyUiState = familyUiState,
                    consentUiState = consentUiState,
                    onOpenRelation = { navController.navigate(AppRoutes.ELDER_FAMILY_RELATION) },
                    onOpenConsent = { navController.navigate(AppRoutes.ELDER_FAMILY_CONSENT) },
                )
            }

            composable(AppRoutes.ELDER_FAMILY_RELATION) {
                ElderFamilyRelationScreen(
                    uiState = familyUiState,
                    onGenerateInvite = familyViewModel::generateInvite,
                    onLookupInvite = familyViewModel::lookupInvite,
                    onAcceptInvite = familyViewModel::acceptInvite,
                    onRejectInvite = familyViewModel::rejectInvite,
                    onCancelInvite = familyViewModel::cancelInvite,
                    onRevokeRelation = familyViewModel::revokeRelation,
                    onConsumeMessage = familyViewModel::consumeMessage,
                    onRetry = familyViewModel::retry,
                )
            }

            composable(AppRoutes.ELDER_FAMILY_CONSENT) {
                ElderConsentScreen(
                    uiState = consentUiState,
                    onGrantScope = elderConsentViewModel::grantScope,
                    onRevoke = elderConsentViewModel::revoke,
                    onRefresh = elderConsentViewModel::refresh,
                    onConsumeMessage = elderConsentViewModel::consumeMessage,
                    onOpenRelation = { navController.navigate(AppRoutes.ELDER_FAMILY_RELATION) },
                )
            }

            composable(
                route = "${AppRoutes.ELDER_RISK_DETAIL}/{${AppRoutes.ARG_RISK_ID}}",
                arguments = listOf(
                    navArgument(AppRoutes.ARG_RISK_ID) { type = NavType.StringType },
                ),
            ) { entry ->
                val riskId = entry.arguments?.getString(AppRoutes.ARG_RISK_ID).orEmpty()
                val detailViewModel: RiskDetailViewModel = viewModel(
                    factory = remember(riskRepository, riskId, consentRepository, familyRepository) {
                        RiskDetailViewModel.factory(
                            repository = riskRepository,
                            eventId = riskId,
                            consentRepository = consentRepository,
                            familyRepository = familyRepository,
                        )
                    },
                )
                val detailUiState by detailViewModel.uiState.collectAsStateWithLifecycle()
                ElderRiskDetailScreen(
                    uiState = detailUiState,
                    onMarkHandled = detailViewModel::markHandled,
                    onShareEvent = detailViewModel::shareEvent,
                    onRevokeShare = detailViewModel::revokeShare,
                    onConsumeMessage = detailViewModel::consumeMessage,
                )
            }
        }
    }
}
