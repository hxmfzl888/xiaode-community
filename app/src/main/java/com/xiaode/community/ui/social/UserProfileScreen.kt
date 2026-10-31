package com.xiaode.community.ui.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.xiaode.community.data.ApiClient
import com.xiaode.community.data.ApiResult
import com.xiaode.community.data.AppOut
import com.xiaode.community.data.MomentOut
import com.xiaode.community.data.Repo
import com.xiaode.community.data.ResourceOut
import com.xiaode.community.data.safeCall
import com.xiaode.community.ui.appstore.AppRow
import com.xiaode.community.ui.common.LoadingBox
import com.xiaode.community.ui.common.XiaodeAvatar
import com.xiaode.community.ui.resource.ResourceCard
import com.xiaode.community.ui.square.MomentCard
import com.xiaode.community.ui.theme.BrandBlue
import com.xiaode.community.ui.common.IconEmptyState
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Android

/**
 * 个人主页 v2：头部资料 + 内容 Tab（帖子 / 资源 / 应用）
 * UI_ANIM_MERGE: 头部折叠、Tab 指示器滑动、内容淡入、关注按钮弹性
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(nav: NavController, userId: Int) {
    var u by remember { mutableStateOf<com.xiaode.community.data.SocialUserOut?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 内容数据
    var moments by remember { mutableStateOf<List<MomentOut>>(emptyList()) }
    var resources by remember { mutableStateOf<List<ResourceOut>>(emptyList()) }
    var apps by remember { mutableStateOf<List<AppOut>>(emptyList()) }
    var contentLoading by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var myId by remember { mutableIntStateOf(0) }

    // ---- 加载用户资料 ----
    LaunchedEffect(userId, reload) {
        loading = true
        errMsg = null
        when (val r = safeCall { Repo.api.socialUser(userId) }) {
            is ApiResult.Ok -> u = r.data
            is ApiResult.Err -> if (u == null) errMsg = r.message
        }
        loading = false
    }

    // ---- 加载用户内容（三个接口并行）----
    LaunchedEffect(userId, reload) {
        contentLoading = true
        val m = safeCall { Repo.api.userMoments(userId) }
        val r = safeCall { Repo.api.userResources(userId) }
        val a = safeCall { Repo.api.userApps(userId) }
        if (m is ApiResult.Ok) moments = m.data.items
        if (r is ApiResult.Ok) resources = r.data.items
        if (a is ApiResult.Ok) apps = a.data.items
        contentLoading = false
    }

    // ---- 自己的 id（判断能否删除自己的帖子）----
    LaunchedEffect(Unit) {
        when (val me = safeCall { Repo.api.me() }) {
            is ApiResult.Ok -> myId = me.data.id
            is ApiResult.Err -> {}
        }
    }

    val listState = rememberLazyListState()
    // 头部随滚动轻微缩放 + 淡出（滚动融合）
    val headerScale by animateFloatAsState(
        targetValue = if (listState.firstVisibleItemIndex > 0) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "headerScale",
    )
    val headerAlpha by animateFloatAsState(
        targetValue = if (listState.firstVisibleItemScrollOffset > 120) 0.6f else 1f,
        animationSpec = tween(220),
        label = "headerAlpha",
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("个人主页", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { pad ->
        val usr = u
        if (loading && usr == null) {
            Box(Modifier.fillMaxSize().padding(pad)) { LoadingBox() }
        } else if (usr == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(errMsg ?: "加载失败", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Button(onClick = { reload++ }) { Text("重新加载") }
                        Spacer(Modifier.width(10.dp))
                        TextButton(onClick = { nav.popBackStack() }) { Text("返回") }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(pad)) {
                if (usr.pageBg.isNotBlank()) {
                    AsyncImage(
                        model = ApiClient.absUrl(usr.pageBg),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.86f)))
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    // ========== 头部 ==========
                    item(key = "header") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer(scaleX = headerScale, scaleY = headerScale, alpha = headerAlpha)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                XiaodeAvatar(usr.avatar, 72.dp, usr.levelEffect, usr.isVip)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        usr.nickname.ifBlank { usr.username },
                                        fontSize = 19.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Lv.${usr.level}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (usr.isVip) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(
                                                Modifier.clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                                        listOf(Color(0xFFF2AD5C), Color(0xFFE6216D), Color(0xFF901CCB))
                                                    )
                                                )
                                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                            ) { Text("VIP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                                        }
                                    }
                                    if (usr.bio.isNotBlank()) {
                                        Spacer(Modifier.height(5.dp))
                                        Box(
                                            Modifier.clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                                        ) { Text(usr.bio, fontSize = 11.sp, color = Color(0xFF7C4DFF)) }
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            // 统计条（带圆角卡片 + 分割线）
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(vertical = 10.dp),
                            ) {
                                StatCol("帖子", usr.momentCount, Modifier.weight(1f)) { tab = 0 }
                                VDivider()
                                StatCol("资源", resources.size, Modifier.weight(1f)) { tab = 1 }
                                VDivider()
                                StatCol("应用", usr.appCount, Modifier.weight(1f)) { tab = 2 }
                                VDivider()
                                StatCol("粉丝", usr.followerCount, Modifier.weight(1f), enabled = false)
                                VDivider()
                                StatCol("关注", usr.followingCount, Modifier.weight(1f), enabled = false)
                            }
                            // PROFILE_FAV_ENTRY_V1: 看自己主页时，显示「我的收藏」入口
                            if (usr.isSelf) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                                        )
                                        .clickable { nav.navigate("my_favorites") }
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Bookmark,
                                        null,
                                        tint = BrandBlue,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        "我的收藏",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "查看全部 ›",
                                        fontSize = 12.sp,
                                        color = BrandBlue.copy(alpha = 0.85f),
                                    )
                                }
                            }
                            if (!usr.isSelf) {
                                Spacer(Modifier.height(14.dp))
                                Row {
                                    val sc by animateFloatAsState(
                                        targetValue = if (busy) 0.94f else 1f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                        label = "followScale",
                                    )
                                    Button(
                                        onClick = {
                                            if (!busy) {
                                                busy = true
                                                scope.launch {
                                                    when (val r = safeCall { Repo.api.toggleFollow(userId) }) {
                                                        is ApiResult.Ok -> reload++
                                                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                                    }
                                                    busy = false
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (usr.followed) MaterialTheme.colorScheme.surfaceVariant else BrandBlue,
                                            contentColor = if (usr.followed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                        ),
                                        modifier = Modifier.weight(1f).height(44.dp).graphicsLayer(scaleX = sc, scaleY = sc),
                                    ) { Text(if (usr.followed) "已关注" else "关注") }
                                    Spacer(Modifier.width(10.dp))
                                    OutlinedButton(
                                        onClick = { nav.navigate("pm/${usr.id}?name=${usr.nickname.ifBlank { usr.username }}") },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                    ) { Text("发私信") }
                                }
                            }
                        }
                    }

                    // ========== Tab 栏（吸顶）==========
                    item(key = "tabs") {
                        ContentTabs(
                            tabs = listOf("帖子 ${moments.size}", "资源 ${resources.size}", "应用 ${apps.size}"),
                            selected = tab,
                            onSelect = { tab = it },
                        )
                    }

                    // ========== 内容区 ==========
                    if (contentLoading) {
                        item(key = "cloading") {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp), color = BrandBlue)
                                    Spacer(Modifier.height(10.dp))
                                    Text("正在加载…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        when (tab) {
                            0 -> {
                                if (moments.isEmpty()) {
                                    item(key = "empty_m") {
                                        IconEmptyState(
                                            icon = Icons.Filled.EditNote,
                                            title = "还没有发过帖子",
                                            verticalPadding = 40.dp,
                                        )
                                    }
                                } else {
                                    items(moments, key = { "m_${it.id}" }) { m ->
                                        MomentCard(
                                            m = m,
                                            myId = myId,
                                            onLike = {
                                                scope.launch {
                                                    when (val r = safeCall { Repo.api.likeMoment(m.id) }) {
                                                        is ApiResult.Ok -> reload++
                                                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                                    }
                                                }
                                            },
                                            onDelete = {
                                                scope.launch {
                                                    when (val r = safeCall { Repo.api.deleteMoment(m.id) }) {
                                                        is ApiResult.Ok -> { moments = moments.filter { it.id != m.id }; snackbar.showSnackbar("已删除") }
                                                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                                    }
                                                }
                                            },
                                            onOpenDetail = { nav.navigate("detail/${m.id}") },
                                            onOpenUser = { nav.navigate("user/${m.userId}") },
                                        )
                                        Spacer(Modifier.height(6.dp))
                                    }
                                }
                            }
                            1 -> {
                                if (resources.isEmpty()) {
                                    item(key = "empty_r") {
                                        IconEmptyState(
                                            icon = Icons.Filled.FolderOpen,
                                            title = "还没有上传资源",
                                            verticalPadding = 40.dp,
                                        )
                                    }
                                } else {
                                    items(resources, key = { "r_${it.id}" }) { r ->
                                        ResourceCard(r) { nav.navigate("resource_detail/${r.id}") }
                                    }
                                }
                            }
                            else -> {
                                if (apps.isEmpty()) {
                                    item(key = "empty_a") {
                                        IconEmptyState(
                                            icon = Icons.Filled.Android,
                                            title = "还没有上传应用",
                                            verticalPadding = 40.dp,
                                        )
                                    }
                                } else {
                                    items(apps, key = { "a_${it.id}" }) { a ->
                                        AppRow(a) { nav.navigate("app_detail/${a.id}") }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/** 滑动指示器 Tab 栏 */
@Composable
private fun ContentTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            tabs.forEachIndexed { i, label ->
                val active = i == selected
                val color by androidx.compose.animation.animateColorAsState(
                    targetValue = if (active) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200), label = "tabColor$i",
                )
                // 弹簧动画会过冲（可能 <0 或 >1），必须钳制，否则 Color.copy(alpha<0) 会崩溃
                val rawWeight by animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "tabWeight$i",
                )
                val weight = rawWeight.coerceIn(0f, 1f)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(i) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = color,
                        modifier = Modifier.graphicsLayer(scaleX = 1f + weight * 0.04f, scaleY = 1f + weight * 0.04f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .height(3.dp)
                            .width((20.dp * weight).coerceAtLeast(0.dp))
                            .clip(CircleShape)
                            .background(BrandBlue.copy(alpha = weight.coerceIn(0f, 1f))),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun VDivider() {
    Box(Modifier.width(0.5.dp).height(24.dp).background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
private fun EmptyHint(emoji: String, text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 50.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 34.sp)
            Spacer(Modifier.height(8.dp))
            Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatCol(
    label: String,
    n: Int,
    mod: Modifier,
    // PROFILE_STAT_NOCLICK_V1: 传 false 则整个格子不可点击（如粉丝/关注）
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Column(
        if (enabled) mod.clickable { onClick() } else mod,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$n", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}