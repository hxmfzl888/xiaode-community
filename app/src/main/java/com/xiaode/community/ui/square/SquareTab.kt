package com.xiaode.community.ui.square
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.ApiResult
import com.xiaode.community.data.Repo
import com.xiaode.community.data.ReportIn
import com.xiaode.community.data.safeCall
import com.xiaode.community.data.MomentOut
import com.xiaode.community.data.Channel
import com.xiaode.community.ui.common.pressScale
import com.xiaode.community.ui.common.EntranceAnimation
import com.xiaode.community.ui.common.DotBadge
import com.xiaode.community.ui.common.EmptyBox
import com.xiaode.community.ui.common.LoadingBox
import com.xiaode.community.ui.common.PullRefreshBox
import com.xiaode.community.ui.common.XiaodeAvatar
import com.xiaode.community.ui.common.clickableNoRipple
import com.xiaode.community.ui.theme.BrandBlue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import coil.compose.AsyncImage
import com.xiaode.community.data.AdOut
import com.xiaode.community.data.ApiClient
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaode.community.data.AnnouncementOut
import com.xiaode.community.data.AppOut
import com.xiaode.community.data.ToolOut
import com.xiaode.community.ui.common.SkeletonPostList
import com.xiaode.community.ui.common.EmptyState
import com.xiaode.community.ui.common.IconEmptyState
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquareTab(nav: NavController) {
    val vm: SquareViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var reportTarget by remember { mutableStateOf<Int?>(null) }
    val reportScope = rememberCoroutineScope()
    var annDetail by remember { mutableStateOf<AnnouncementOut?>(null) }
    var board by remember { mutableIntStateOf(0) }
    // 可折叠头部：1e6=展开，0=收起；默认收起（用略小于 header 的值触发首帧动画）
    var collapsePx by remember { mutableFloatStateOf(1e6f) }
    // 原地搜索状态
    var searchOpen by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var searchMoments by remember { mutableStateOf<List<MomentOut>>(emptyList()) }
    var searchChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val searchScope = rememberCoroutineScope()
    val focusReq = remember { FocusRequester() }
    // ── 在线人数：定时上报心跳 + 拉取在线数 ──
    var onlineCount by remember { mutableIntStateOf(0) }
    // ── 应用推荐：随机拉取 10 个应用，可折叠展示 ──
    var recommendApps by remember { mutableStateOf<List<AppOut>>(emptyList()) }
    var recommendCollapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val r = safeCall { Repo.api.apps(limit = 60) }
        if (r is ApiResult.Ok && r.data.isNotEmpty()) {
            recommendApps = r.data.shuffled().take(10)
        }
    }
    // ── 推荐工具：随机拉取若干工具 ──
    var recommendTools by remember { mutableStateOf<List<ToolOut>>(emptyList()) }
    var toolCollapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val r = safeCall { Repo.api.tools() }
        if (r is ApiResult.Ok && r.data.isNotEmpty()) {
            recommendTools = r.data.shuffled().take(10)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val r = safeCall { Repo.api.heartbeat() }
            if (r is ApiResult.Ok) {
                val n = (r.data["online"] as? Number)?.toInt() ?: 0
                onlineCount = n
            }
            kotlinx.coroutines.delay(60_000)
        }
    }
    suspend fun runSearch() {
        val kw = keyword.trim()
        if (kw.isEmpty()) { searched = false; searchMoments = emptyList(); searchChannels = emptyList(); return }
        searching = true; searched = true
        when (val r = safeCall { Repo.api.searchMoments(kw) }) {
            is ApiResult.Ok -> searchMoments = r.data
            is ApiResult.Err -> searchMoments = emptyList()
        }
        when (val r = safeCall { Repo.api.searchChannels(kw) }) {
            is ApiResult.Ok -> searchChannels = r.data
            is ApiResult.Err -> searchChannels = emptyList()
        }
        searching = false
    }
    LaunchedEffect(ui.toast, ui.error) {
        (ui.toast ?: ui.error)?.let { msg ->
            snackbar.showSnackbar(msg)
            vm.consumeToast()
        }
    }
    val momentsDirty = XiaodeApp.momentsDirty.value
    LaunchedEffect(momentsDirty) { if (momentsDirty > 0) vm.refresh() }
    val notifyDirty = XiaodeApp.notifyDirty.value
    LaunchedEffect(notifyDirty) { if (notifyDirty > 0) vm.loadNotifyUnread() }
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= ui.moments.size - 3 && ui.moments.isNotEmpty()
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) vm.loadMore() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { nav.navigate("post") },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
            ) {
                Icon(Icons.Filled.Add, "发帖", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        },
    ) { pad ->
        val densityH = androidx.compose.ui.platform.LocalDensity.current
        val headerPx = with(densityH) { 152.dp.toPx() }
        // collapsePx 表示“头部露出的高度”（0=收起，headerPx=完全展开）
        // v9.7: 头部高度直接跟随手势（不再用 200ms 动画，避免拖影/卡顿感）
        val headerH = collapsePx.coerceIn(0f, headerPx)
        // 头部是否已完全展开（决定下拉刷新是否可用）
        val headerExpanded = collapsePx >= headerPx - 2f
        // v2.0: 上滑看帖时头部自动收起；回到顶部则展开
        val firstVisible = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        LaunchedEffect(firstVisible, firstOffset) {
            val scrolled = if (firstVisible > 0) headerPx else firstOffset.toFloat()
            val target = (headerPx - scrolled).coerceIn(0f, headerPx)
            collapsePx = target
        }
        Column(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // 头部：默认收起，下拉露出。搜索态时不显示头部（直接显示搜索栏）
            if (!searchOpen) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(with(densityH) { headerH.toDp() })
                        .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Column {
                        BoardTabs(board, online = onlineCount, onSwitch = { board = it }, onRank = { nav.navigate("rank") })
                        // v2.1 标签筛选行（仅广场页显示）
                        if (board == 0) {
                            TagFilterRow(ui.activeTag, onPick = { vm.switchTag(it) })
                        }
                        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Row(
                                Modifier.fillMaxWidth().height(40.dp)
                                    .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { searchOpen = true }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Search, "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("搜索帖子 / 频道", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                // 原地搜索栏（展开态）
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f).height(42.dp)
                            .clip(RoundedCornerShape(21.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = keyword,
                            onValueChange = { keyword = it; searchScope.launch { runSearch() } },
                            singleLine = true,
                            modifier = Modifier.weight(1f).focusRequester(focusReq),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { searchScope.launch { runSearch() } }),
                            decorationBox = { inner ->
                                if (keyword.isEmpty()) Text("搜索帖子 / 频道", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            },
                        )
                        if (keyword.isNotEmpty()) {
                            Icon(Icons.Filled.Close, "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).clickable { keyword = ""; searched = false; searchMoments = emptyList(); searchChannels = emptyList() })
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { searchOpen = false; keyword = ""; searched = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                LaunchedEffect(Unit) { focusReq.requestFocus() }
            }
            AnimatedContent(
                targetState = if (searchOpen) -1 else board,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(320)) { it } + androidx.compose.animation.fadeIn(tween(260)))
                            .togetherWith(slideOutHorizontally(tween(320)) { -it } + androidx.compose.animation.fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(320)) { -it } + androidx.compose.animation.fadeIn(tween(260)))
                            .togetherWith(slideOutHorizontally(tween(320)) { it } + androidx.compose.animation.fadeOut(tween(200)))
                    }
                },
                label = "board-switch",
                modifier = Modifier.fillMaxSize(),
            ) { b ->
            if (b == -1) {
                // 原地搜索结果
                if (searching) SkeletonPostList(3)
                else if (!searched) EmptyBox("输入关键词，搜索帖子 / 频道")
                else if (searchMoments.isEmpty() && searchChannels.isEmpty()) IconEmptyState(
                    icon = Icons.Filled.Search,
                    title = "没有找到相关内容",
                    desc = "换个关键词试试",
                )
                else LazyColumn(Modifier.fillMaxSize()) {
                    if (searchChannels.isNotEmpty()) {
                        item(key = "ch_head") { SectionTitle("频道") }
                        itemsIndexed(searchChannels, key = { i, it -> "sch_${it.id}_$i" }) { _, ch ->
                            SearchChannelRow(ch) { searchOpen = false; nav.navigate("channel_detail/${ch.id}") }
                        }
                    }
                    if (searchMoments.isNotEmpty()) {
                        item(key = "mo_head") { SectionTitle("帖子") }
                        itemsIndexed(searchMoments, key = { i, it -> "smo_${it.id}_$i" }) { _, m ->
                            MomentCard(
                                m = m,
                                myId = XiaodeApp.store.currentUserJson?.let { extractId(it) } ?: 0,
                                onLike = { searchScope.launch { safeCall { Repo.api.likeMoment(m.id) }; runSearch() } },
                                onDelete = {},
                                onOpenDetail = { searchOpen = false; nav.navigate("detail/" + m.id) },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else if (b == 0) {
            if (ui.refreshing && ui.moments.isEmpty()) {
                SkeletonPostList(4)
            } else if (ui.moments.isEmpty() && ui.loading == false) {
                IconEmptyState(
                    icon = Icons.Filled.EditNote,
                    title = "还没有帖子",
                    desc = "成为第一个发声的人",
                    actionText = "发一条帖子",
                    onAction = { nav.navigate("post") },
                )
            } else {
                PullRefreshBox(
                    refreshing = ui.refreshing,
                    onRefresh = { vm.refresh() },
                    enabled = headerExpanded,
                    headerMaxPx = headerPx,
                    onPullChange = { v ->
                        // 下拉中实时跟随手指展开头部
                        collapsePx = v.coerceIn(0f, headerPx)
                    },
                    onSettle = { v ->
                        // 松手定格：0=收起，headerPx=完全展开（修复“卡住/只收一半”）
                        collapsePx = v.coerceIn(0f, headerPx)
                    },
                ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (ui.announcements.isNotEmpty()) {
                        item(key = "announce_section") {
                            AnnouncementSection(ui.announcements) { annDetail = it }
                        }
                    }
                    if (ui.ads.isNotEmpty()) {
                        item(key = "ad_banner") { AdBanner(ui.ads) }
                    }
                    // SF_POLISH_V2: 广告下方「频道 / 工具」入口，升级为品牌色卡片
                    item(key = "ad_entries") {
                        EntranceAnimation(key = "ad_entries", offsetY = -24) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                EntryCard(
                                    icon = Icons.Filled.Forum,
                                    title = "频道",
                                    sub = "按板块逛帖子",
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                ) { nav.navigate("channels_tab") }
                                EntryCard(
                                    icon = Icons.Filled.Build,
                                    title = "工具",
                                    sub = "社区小工具",
                                    accent = Color(0xFF6366F1),
                                    modifier = Modifier.weight(1f),
                                ) { nav.navigate("tools_tab") }
                            }
                        }
                    }
                    if (recommendApps.isNotEmpty()) {
                        item(key = "app_recommend") {
                            AppRecommendSection(recommendApps, recommendCollapsed, onToggle = { recommendCollapsed = !recommendCollapsed }) { app ->
                                nav.navigate("app_detail/" + app.id)
                            }
                        }
                    }
                    if (recommendTools.isNotEmpty()) {
                        item(key = "tool_recommend") {
                            ToolRecommendSection(recommendTools, toolCollapsed, onToggle = { toolCollapsed = !toolCollapsed }) { t ->
                                nav.navigate("tool_use/" + t.id)
                            }
                        }
                    }
                    itemsIndexed(ui.moments, key = { _, it -> "moment_" + it.id }) { index, m ->
                        FadeInItem(index = index) {
                        MomentCard(
                            m = m,
                            myId = XiaodeApp.store.currentUserJson?.let { extractId(it) } ?: 0,
                            onLike = { vm.toggleLike(m) },
                            onDelete = { vm.deleteMoment(m.id) },
                            onOpenDetail = { nav.navigate("detail/" + m.id) },
                            onOpenUser = { uid -> nav.navigate("user/" + uid) },
                            onReport = { reportTarget = m.id },
                        )
                        }
                    }
                    if (ui.loading) item { SkeletonPostList(2) }
                    if (ui.hasMore == false && ui.moments.isNotEmpty()) item { EmptyBox("没有更多了") }
                    item { Spacer(Modifier.height(80.dp)) }
                }
                }
            }
            } else if (b == 1) {
                ChannelScreen(nav, embedded = true)
            } else {
                ToolsTab(nav)
            }
            }
        }
    }
    annDetail?.let { ann ->
        AnnouncementDialog(ann) { annDetail = null }
    }
    reportTarget?.let { tid ->
        ReportDialog(
            onDismiss = { reportTarget = null },
            onSubmit = { reason, detail ->
                reportScope.launch {
                    when (val r = safeCall { Repo.api.report(ReportIn("moment", tid, reason, detail)) }) {
                        is ApiResult.Ok -> snackbar.showSnackbar("举报已提交")
                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                    }
                }
                reportTarget = null
            },
        )
    }
}

/** 分组小标题 */
@Composable
private fun SectionTitle(t: String) {
    Text(t, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp))
}

/** 原地搜索里的频道行 */
@Composable
private fun SearchChannelRow(ch: Channel, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            XiaodeAvatar(ch.image, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ch.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                if (ch.intro.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(ch.intro, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(3.dp))
                Text("${ch.memberCount} 位成员", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable
private fun BoardTabs(board: Int, online: Int = 0, onSwitch: (Int) -> Unit, onRank: () -> Unit) {
    // SF_POLISH_V2: 去掉「广场/频道/工具」三 Tab（已移到广告下方入口）
    // 顶部只保留：品牌标题 + 排行榜入口 + 在线人数
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "小德社区",
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = (-0.4).sp,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFF22C55E))
        )
        Spacer(Modifier.width(4.dp))
        Text("${online}人在线", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.weight(1f))

        // 排行榜入口：主色淡胶囊
        Row(
            Modifier.clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .clickableNoRipple(onRank)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.EmojiEvents, "排行榜", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("排行榜", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** v2.1 标签筛选行：全部 + 四个固定标签 */
@Composable
private fun TagFilterRow(active: String, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TagChip("全部", active.isEmpty()) { onPick("") }
        POST_TAGS.forEach { tag ->
            TagChip(tag, active == tag) { onPick(tag) }
        }
    }
}

@Composable
private fun TagChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // STARFREE_V2
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun ChannelStrip(
    channels: List<com.xiaode.community.data.Channel>,
    unread: Set<Int>,
    activeId: Int,
    onAll: () -> Unit,
    onChannel: (com.xiaode.community.data.Channel) -> Unit,
    onManage: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 0.dp) {
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item { ChannelDot("全部", activeId == 0, false, onAll) }
            itemsIndexed(channels, key = { i, it -> "${it.id}_${i}" }) { _, ch ->
                ChannelDot(ch.name, activeId == ch.id, ch.id in unread) { onChannel(ch) }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onManage),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.height(3.dp))
                    Text("频道", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChannelDot(label: String, selected: Boolean, hasDot: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.take(2),
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
            if (hasDot) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF44336)).align(Alignment.TopEnd))
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label, fontSize = 10.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, modifier = Modifier.widthIn(max = 52.dp),
        )
    }
}

internal fun extractId(json: String): Int =
    Regex("\"id\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
/** 举报弹窗 */
@Composable
internal fun ReportDialog(onDismiss: () -> Unit, onSubmit: (reason: String, detail: String) -> Unit) {
    val reasons = listOf("垃圾广告", "色情低俗", "违法违规", "血腥暴力", "人身攻击", "其他")
    var selected by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf("") }
    com.xiaode.community.ui.common.DsBottomSheet(
        visible = true,
        onDismiss = onDismiss,
        title = "举报内容",
        subtitle = "请选择举报原因，我们会尽快处理",
    ) {
        reasons.forEachIndexed { i, r ->
            com.xiaode.community.ui.common.SheetRadioRow(
                text = r,
                selected = selected == i,
                onClick = { selected = i },
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = detail,
            onValueChange = { detail = it },
            label = { Text("补充说明（选填）") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                com.xiaode.community.ui.theme.DS.Radius.sm
            ),
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onSubmit(reasons[selected], detail) },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                com.xiaode.community.ui.theme.DS.Radius.pill
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = com.xiaode.community.ui.theme.DS.Colors.danger,
                contentColor = Color.White,
            ),
        ) {
            Text("提交举报", fontWeight = FontWeight.Medium)
        }
    }
}

/** 应用推荐区（可折叠） */
@Composable
private fun AppRecommendSection(
    apps: List<AppOut>,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onOpen: (AppOut) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Apps, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("应用推荐", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(6.dp))
                Text(apps.size.toString() + " 款", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(if (collapsed) "展开" else "收起", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            if (!collapsed) {
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(apps.size) { i ->
                        val a = apps[i]
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(a) }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (a.icon.isBlank()) {
                                    Text(a.name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                } else {
                                    AsyncImage(
                                        model = ApiClient.absUrl(a.icon), contentDescription = a.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(a.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}




/** 推荐工具区（可折叠） */
@Composable
private fun ToolRecommendSection(
    tools: List<ToolOut>,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onOpen: (ToolOut) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Build, null, tint = Color(0xFF6366F1), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("推荐工具", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(6.dp))
                Text(tools.size.toString() + " 个", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(if (collapsed) "展开" else "收起", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            if (!collapsed) {
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(tools.size) { i ->
                        val t = tools[i]
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpen(t) }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (t.cover.isBlank()) {
                                    Text(t.name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                } else {
                                    AsyncImage(
                                        model = ApiClient.absUrl(t.cover), contentDescription = t.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(t.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** 广告位：自动轮播，点击跳转 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AdBanner(ads: List<AdOut>, modifier: Modifier = Modifier) {
    if (ads.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    val pagerState = rememberPagerState(pageCount = { ads.size })
    if (ads.size > 1) {
        LaunchedEffect(ads.size) {
            while (true) {
                delay(3500)
                val next = (pagerState.currentPage + 1) % ads.size
                pagerState.animateScrollToPage(next)
            }
        }
    }
    Card(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(180.dp)
                .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val ad = ads[page]
                AsyncImage(
                    model = ApiClient.absUrl(ad.image),
                    contentDescription = ad.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (ad.link.isNotBlank()) {
                                try { uriHandler.openUri(ad.link) } catch (_: Exception) {}
                            }
                        },
                )
            }
        }
        if (ads.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(ads.size) { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (i == pagerState.currentPage) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        }
    }
}
}

/** 公告区（折叠）：只露第一条，向下可滚动查看，点击某条全屏 */
@Composable
private fun AnnouncementSection(list: List<AnnouncementOut>, onClick: (AnnouncementOut) -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Campaign, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("公告", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(6.dp))
                Text("${list.size} 条", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().heightIn(max = 96.dp).verticalScroll(rememberScrollState()),
            ) {
                list.forEach { ann ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onClick(ann) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ann.title.ifBlank { "公告" }, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Text(ann.content.replace("\n", " "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** 公告全文（全屏弹窗） */
@Composable
private fun AnnouncementDialog(ann: AnnouncementOut, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("公告详情", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                    Text(ann.title.ifBlank { "公告" }, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(ann.createdAt.take(10), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(ann.content, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 24.sp)
                    Spacer(Modifier.height(40.dp))
                }
            }
            }
        }
    }

@androidx.compose.runtime.Composable
private fun FadeInItem(index: Int, content: @androidx.compose.runtime.Composable () -> Unit) {
    var visible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(((index % 6).toLong()) * 60L)
        visible = true
    }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.62f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "springItem",
    )
    val ty by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 0f else 24f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.62f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "springItem2",
    )
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier
            .graphicsLayer(alpha = alpha, translationY = ty),
    ) { content() }
}


/** SF_POLISH_V2: 广告下方的品牌色入口卡片（大图标 + 渐变角标） */
@Composable
private fun EntryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pressScale(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.ArrowForward,
                null,
                tint = accent.copy(alpha = 0.5f),
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            sub,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** SF_AD_ENTRY: 广告下方的入口方块（频道 / 工具） */
@Composable
private fun EntryTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pressScale(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(primary.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = primary, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                sub, fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            Icons.Filled.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(16.dp),
        )
    }
}
