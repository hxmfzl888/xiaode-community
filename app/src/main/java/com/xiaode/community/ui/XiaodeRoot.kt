package com.xiaode.community.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xiaode.community.XiaodeApp
import com.xiaode.community.ui.auth.LoginScreen
import com.xiaode.community.ui.appstore.*
import com.xiaode.community.ui.legal.LegalScreen
import com.xiaode.community.ui.legal.LegalType
import com.xiaode.community.ui.chat.ChatTab
import com.xiaode.community.ui.mine.*
import com.xiaode.community.ui.social.*
import com.xiaode.community.ui.square.*
import com.xiaode.community.ui.theme.XiaodeTheme
import com.xiaode.community.ui.theme.ThemeState
import kotlinx.coroutines.launch

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

// 底栏保持 4 个（频道/工具从广场页广告下方进入）
private val TABS = listOf(
    TabItem("square", "广场", Icons.Filled.Public),
    TabItem("appstore", "应用", Icons.Filled.Apps),
    TabItem("resource_tab", "资源", Icons.Filled.Folder),
    TabItem("chat", "消息", Icons.Filled.Chat),
    TabItem("mine", "我的", Icons.Filled.Person),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun XiaodeRoot() {
    XiaodeTheme {
        var forceBlock by remember { mutableStateOf(false) }
        var forceInfo by remember { mutableStateOf<com.xiaode.community.data.VersionInfo?>(null) }
        LaunchedEffect(Unit) {
            when (val r = com.xiaode.community.data.safeCall { com.xiaode.community.data.Repo.api.appVersion() }) {
                is com.xiaode.community.data.ApiResult.Ok -> {
                    val info = r.data
                    if (info.force && com.xiaode.community.ui.mine.isNewer(info.minVersion, com.xiaode.community.BuildConfig.VERSION_NAME)) {
                        forceInfo = info; forceBlock = true
                    }
                }
                else -> {}
            }
        }
        if (forceBlock) {
            com.xiaode.community.ui.mine.ForceUpdateDialog(info = forceInfo, currentVersion = com.xiaode.community.BuildConfig.VERSION_NAME)
            return@XiaodeTheme
        }
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val tabRoutes = TABS.map { it.route }
        val showBottomBar = currentRoute in tabRoutes
        val loggedIn by XiaodeApp.loginState
        LaunchedEffect(Unit) { XiaodeApp.syncLoginState() }
        if (loggedIn == false) {
            NavHost(navController = nav, startDestination = "login") {
                composable("login") { LoginScreen(nav) { XiaodeApp.syncLoginState() } }
                composable("legal/privacy") { LegalScreen(onBack = { nav.popBackStack() }, type = LegalType.PRIVACY) }
                composable("legal/disclaimer") { LegalScreen(onBack = { nav.popBackStack() }, type = LegalType.DISCLAIMER) }
            }
            return@XiaodeTheme
        }
        val scope = rememberCoroutineScope()
        val startIndex = TABS.indexOfFirst { it.route == (currentRoute ?: "square") }.coerceAtLeast(0)
        val pagerState = rememberPagerState(initialPage = startIndex) { TABS.size }
        LaunchedEffect(pagerState.settledPage, showBottomBar) {
            if (showBottomBar) {
                val target = TABS[pagerState.settledPage].route
                if (currentRoute != null && currentRoute != target) {
                    nav.navigate(target) {
                        popUpTo("square") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    AnimatedBottomBar(
                        tabs = TABS,
                        currentRoute = currentRoute,
                        pagerPage = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                        onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
                    )
                }
            }
        ) { padding ->
            if (showBottomBar) {
                // TOUCH_FIX_V2: 原 slowFling 把动画拉到 400-550ms，
                // 导致 Pager 在每个页面挂的 drag 探测器长时间处于活跃状态，
                // 手指轻点会被判定为滑动起始，造成"点不动/要连点"。
                // 改回标准 fling，动画时长由系统默认（约 200ms）负责。
                val touchFling = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = androidx.compose.foundation.pager.PagerSnapDistance.atMost(1),
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.padding(padding),
                    userScrollEnabled = true,
                    pageSpacing = 0.dp,
                    beyondBoundsPageCount = 1,  // TOUCH_FIX_V2: 保留相邻页保活（点击响应更快）
                    flingBehavior = touchFling,
                ) { page ->
                    when (TABS[page].route) {
                        "square" -> SquareTab(nav)
                        "appstore" -> AppStoreTab(nav)
                        "resource_tab" -> com.xiaode.community.ui.resource.ResourcesTab(nav)
                        "chat" -> ChatTab(nav)
                        "mine" -> MineTab(nav)
                    }
                }
            } else {
                NavHost(navController = nav, startDestination = "square", modifier = Modifier.padding(padding)) {
                    composable("square") { SquareTab(nav) }
                    composable("appstore") { AppStoreTab(nav) }
                    composable("resource_tab") { com.xiaode.community.ui.resource.ResourcesTab(nav) }
                    composable("resource_upload") { com.xiaode.community.ui.resource.ResourceUploadScreen(nav) }
                    composable("resource_detail/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e -> com.xiaode.community.ui.resource.ResourceDetailScreen(nav, e.arguments?.getInt("id") ?: 0) }
                    composable("resource_mine") { com.xiaode.community.ui.resource.ResourceMineScreen(nav) }
                    composable("channels") { ChannelScreen(nav, embedded = true) }
                    composable("tools") { ToolsTab(nav) }
                    composable("chat") { ChatTab(nav) }
                    composable("mine") { MineTab(nav) }
                    composable("user/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e -> UserProfileScreen(nav, e.arguments?.getInt("id") ?: 0) }
                    composable("pm/{id}?name={name}", arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("name") { type = NavType.StringType; defaultValue = "" })) { e -> PmScreen(nav, e.arguments?.getInt("id") ?: 0, e.arguments?.getString("name") ?: "") }
                    composable("follow_list/{type}") { e -> FollowScreen(nav, e.arguments?.getString("type") ?: "following") }
                    composable("tool_upload") { ToolUploadScreen(nav) }
                    composable("tool_use/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e -> ToolWebScreen(nav, e.arguments?.getInt("id") ?: 0) }
                    composable("post?channelId={channelId}", arguments = listOf(navArgument("channelId") { type = NavType.IntType; defaultValue = 0 })) { e -> PostScreen(nav, e.arguments?.getInt("channelId") ?: 0) }
                    composable("channels") { ChannelScreen(nav) }
                    // SF_AD_ENTRY 路由：独立进入频道 / 工具页
                    composable("channels_tab") {
                        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                            com.xiaode.community.ui.square.ChannelsTab(nav)
                        }
                    }
                    composable("tools_tab") {
                        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                            com.xiaode.community.ui.square.ToolsTab(nav)
                        }
                    }
                    composable("my_channels") { com.xiaode.community.ui.mine.MyChannelsScreen(nav) }
                    composable("notifications") { NotificationsScreen(nav) }
                    composable("search") { SearchScreen(nav) }
                    composable("channel_detail/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry -> ChannelDetailScreen(nav, entry.arguments?.getInt("id") ?: 0) }
                    composable("app_upload") { AppUploadScreen(nav) }
                    composable("app_mine") { AppMineScreen(nav) }
                    composable("my_moments") { com.xiaode.community.ui.mine.MyMomentsScreen(nav) }
                    composable("my_likes") { com.xiaode.community.ui.mine.MyLikesScreen(nav) }
                    composable("my_favorites") { com.xiaode.community.ui.mine.MyFavoritesScreen(nav) }
                    composable("account") { AccountScreen(nav) }
                    composable("security") { com.xiaode.community.ui.mine.SecurityScreen(nav) }
                    composable("settings") { com.xiaode.community.ui.mine.SettingsScreen(nav) }
                    composable("about") { com.xiaode.community.ui.mine.AboutScreen(nav) }
                    composable("diag_log") { com.xiaode.community.ui.mine.DiagLogScreen(nav) }
                    composable("support") { SupportScreen(nav) }
                    composable("edit_profile") { com.xiaode.community.ui.mine.EditProfileScreen(nav) }
                    composable("accounts") { com.xiaode.community.ui.mine.AccountsScreen(nav) }
                    composable("vip") { com.xiaode.community.ui.mine.VipScreenV2(nav) }
                    composable("redeem") { com.xiaode.community.ui.mine.RedeemScreen(nav) }
                    composable("messages") { com.xiaode.community.ui.mine.MessageScreen(nav) }
                    composable("level") { com.xiaode.community.ui.mine.LevelScreen(nav) }
                    composable("delete_account") { com.xiaode.community.ui.mine.DeleteAccountScreen(nav) }
                    composable("app_detail/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry -> AppDetailScreen(nav, entry.arguments?.getInt("id") ?: 0) }
                    composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry -> DetailScreen(nav, entry.arguments?.getInt("id") ?: 0) }
                    composable("rank") { com.xiaode.community.ui.mine.RankScreen(nav) }
                    composable("legal/privacy") { LegalScreen(onBack = { nav.popBackStack() }, type = LegalType.PRIVACY) }
                    composable("legal/disclaimer") { LegalScreen(onBack = { nav.popBackStack() }, type = LegalType.DISCLAIMER) }
                }
            }
        }
    }
}


/** 胶囊悬浮式底部导航条：透明磨砂背景 + 跟随翻页的高亮药丸 + 图标缩放上浮 */
@OptIn(ExperimentalFoundationApi::class)
// 旧的 AnimatedBottomBar 已被替换为高性能版
/**
 * 胶囊悬浮式底部导航条（高性能版）。
 * 优化点：
 *  - 高频的 pagerPage 变化只用 graphicsLayer 驱动，不触发布局/测量；
 *  - 药丸位置用 translationX（graphics 阶段），不再用 offset{}（layout 阶段）；
 *  - 每个 tab 的图标位移/缩放独立用 graphicsLayer，避免整条重组。
 */
/**
 * 底部导航条：只留图标 + 平滑药丸（无文字）。
 * 性能：用 animateFloatAsState 对 pagerPage 做插值，避免每帧在 graphicsLayer
 * 里做多次浮点运算；药丸位移在 graphics 阶段完成，不触发测量/布局。
 */
@Composable
private fun AnimatedBottomBar(
    tabs: List<TabItem>,
    currentRoute: String?,
    pagerPage: Float,
    onSelect: (Int) -> Unit,
) {
    val surf = MaterialTheme.colorScheme.surface
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    val alpha = ThemeState.navAlpha.coerceIn(0.35f, 1f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    // pagerPage 本身已随手势连续变化；点击切换时由 pager 的 animateScrollToPage 负责平滑
    val smoothPage = pagerPage
    val n = tabs.size
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(30.dp),
            shadowElevation = 10.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            surf.copy(alpha = (alpha * 0.92f).coerceIn(0.25f, 1f)),
                            surf.copy(alpha = (alpha * 0.78f).coerceIn(0.18f, 1f)),
                        )
                    )
                )
                .border(
                    width = 0.8.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.6f),
                        )
                    ),
                    shape = RoundedCornerShape(30.dp),
                ),
        ) {
            val barW: androidx.compose.ui.unit.Dp = maxWidth
            Box(Modifier.fillMaxSize()) {
                val segPx = with(density) { (barW / n).toPx() }
                val pillWPx = with(density) { (barW / n * 0.5f).toPx() }
                val pillHPx = with(density) { 34.dp.toPx() }
                val idx = smoothPage.coerceIn(0f, (n - 1).toFloat())
                // 药丸：translationX（graphics），零布局开销
                Box(
                    Modifier
                        .size(barW / n * 0.5f, 34.dp)
                        .graphicsLayer {
                            translationX = (idx + 0.5f) * segPx - pillWPx / 2f
                            translationY = (size.height - pillHPx) / 2f
                        }
                        .clip(RoundedCornerShape(17.dp))
                        .background(active.copy(alpha = 0.14f)),
                )
                Row(Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { i, tab ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickableNoRipple { onSelect(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            // 每个 tab 的选中度用 derivedStateOf 隔离：
                            // 只有自己这一格的 sel 变化时才重组该 Icon，避免整条导航栏每帧全量重组。
                            val selState = androidx.compose.runtime.derivedStateOf {
                                (1f - kotlin.math.abs(smoothPage - i)).coerceIn(0f, 1f)
                            }
                            val sel = selState.value
                            val tint = androidx.compose.ui.graphics.lerp(inactive, active, sel)
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        val s = 1f + 0.14f * sel
                                        scaleX = s; scaleY = s
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}



private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    )
