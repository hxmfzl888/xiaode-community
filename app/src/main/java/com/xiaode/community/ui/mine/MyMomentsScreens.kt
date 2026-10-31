package com.xiaode.community.ui.mine

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.square.MomentCard
import com.xiaode.community.ui.square.extractId
import com.xiaode.community.ui.theme.BrandBlue
import com.xiaode.community.ui.theme.DS
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 我的帖子 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMomentsScreen(nav: NavController) = MomentListScreen(nav, "我的帖子", false)

/** 我的点赞 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyLikesScreen(nav: NavController) = MomentListScreen(nav, "我的点赞", true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MomentListScreen(nav: NavController, title: String, isLiked: Boolean) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var list by remember { mutableStateOf<List<MomentOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val myId = XiaodeApp.store.currentUserJson?.let { extractId(it) } ?: 0

    suspend fun load() {
        loading = true
        val r = if (isLiked) safeCall { api.likedMoments() } else safeCall { api.myMoments() }
        if (r is ApiResult.Ok) list = r.data
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
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
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background)) {
            when {
                loading -> SkeletonPostList(4)
                list.isEmpty() -> IconEmptyState(
                    icon = if (isLiked) Icons.Filled.FavoriteBorder else Icons.Filled.EditNote,
                    title = if (isLiked) "还没有点赞过帖子" else "还没有发布帖子",
                    desc = if (isLiked) "去广场看看有什么好内容" else "分享你的第一个想法",
                    actionText = if (isLiked) "去广场" else "发一条帖子",
                    onAction = {
                        if (isLiked) nav.navigate("square") else nav.navigate("post")
                    },
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { i, it -> "${it.id}_$i" }) { _, m ->
                        // 只有自己发的帖子才支持左滑删除
                        if (!isLiked && m.userId == myId) {
                            SwipeToDelete(
                                onDelete = {
                                    scope.launch {
                                        when (val r = safeCall { api.deleteMoment(m.id) }) {
                                            is ApiResult.Ok -> {
                                                list = list.filter { it.id != m.id }
                                                snackbar.showSnackbar("已删除")
                                            }
                                            is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                        }
                                    }
                                },
                            ) {
                                MomentCard(
                                    m = m, myId = myId,
                                    onLike = { scope.launch { safeCall { api.likeMoment(m.id) }; load() } },
                                    onDelete = {
                                        scope.launch {
                                            when (val r = safeCall { api.deleteMoment(m.id) }) {
                                                is ApiResult.Ok -> {
                                                    list = list.filter { it.id != m.id }
                                                    snackbar.showSnackbar("已删除")
                                                }
                                                is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                            }
                                        }
                                    },
                                    onOpenDetail = { nav.navigate("detail/${m.id}") },
                                )
                            }
                        } else {
                            MomentCard(
                                m = m, myId = myId,
                                onLike = { scope.launch { safeCall { api.likeMoment(m.id) }; load() } },
                                onDelete = {},
                                onOpenDetail = { nav.navigate("detail/${m.id}") },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/**
 * 左滑删除容器
 * 拖过 40% 宽度 → 自动滑出并触发删除
 * 拖一点松手 → 弹回
 */
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var itemWidth by remember { mutableFloatStateOf(1080f) }
    var dragging by remember { mutableStateOf(false) }

    // 松手后弹回的动画
    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = if (dragging) {
            androidx.compose.animation.core.snap()
        } else {
            spring(dampingRatio = 0.72f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
        },
        label = "swipeOffset",
    )

    BoxWithConstraints(modifier.fillMaxWidth()) {
        itemWidth = maxWidth.value
        val threshold = itemWidth * 0.40f

        // 底层：红色删除区
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(DS.Radius.md))
                .background(DS.Colors.danger.copy(alpha = 0.92f))
                .padding(end = 20.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (animatedOffset < -8f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 上层：内容
        Box(
            Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(itemWidth) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            if (offsetX < -threshold) {
                                offsetX = -itemWidth
                                onDelete()
                            } else {
                                offsetX = 0f
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            offsetX = 0f
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        // 只允许左滑（负方向）
                        offsetX = (offsetX + dragAmount).coerceIn(-itemWidth * 0.8f, 0f)
                    }
                },
        ) {
            content()
        }
    }
}