package com.xiaode.community.ui.square
import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.navigation.NavController
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue

/**
 * 系统消息中心（广场右上角铃铛进入）。
 * 系统消息分类：全部 / 点赞 / 关注 / 评论 / 审核 / 其他。
 * 私信在底部「消息」Tab（ChatTab）。
 */
private data class NotifyCat(val key: String, val label: String, val types: Set<String>)

private val NOTIFY_CATS = listOf(
    NotifyCat("all", "全部", emptySet()),
    NotifyCat("like", "点赞", setOf("moment_like", "comment_like")),
    NotifyCat("follow", "关注", setOf("follow")),
    NotifyCat("comment", "评论", setOf("comment", "reply")),
    NotifyCat("audit", "审核", setOf("audit")),
    NotifyCat("other", "其他", setOf("report", "friend_request", "friend_accept", "system")),
)

@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun NotificationsScreen(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var list by remember { mutableStateOf<List<NotificationOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showClear by remember { mutableStateOf(false) }
    var cat by remember { mutableStateOf("all") }

    suspend fun reload() {
        when (val r = safeCall { api.notifications() }) {
            is ApiResult.Ok -> list = r.data.items
            is ApiResult.Err -> snackbar.showSnackbar(r.message)
        }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }
    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清空消息") },
            text = { Text("确定要删除全部系统消息吗？此操作不可恢复。（不影响私信）") },
            confirmButton = {
                TextButton(onClick = {
                    showClear = false
                    scope.launch {
                        when (val r = safeCall { api.clearNotifications() }) {
                            is ApiResult.Ok -> { reload(); XiaodeApp.markNotifyDirty() }
                            is ApiResult.Err -> snackbar.showSnackbar(r.message)
                        }
                    }
                }) { Text("确定", color = Color(0xFFF44336)) }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("取消") } },
        )
    }

    val catTypes = NOTIFY_CATS.firstOrNull { it.key == cat }?.types ?: emptySet()
    val filtered = if (cat == "all") list else list.filter { it.type in catTypes }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("系统消息", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            when (val r = safeCall { api.readAll() }) {
                                is ApiResult.Ok -> { reload(); XiaodeApp.markNotifyDirty() }
                                is ApiResult.Err -> snackbar.showSnackbar(r.message)
                            }
                        }
                    }) { Text("全部已读", color = Color.White, fontSize = 13.sp) }
                    TextButton(onClick = { showClear = true }) {
                        Text("清空", color = Color.White, fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background)) {
            // 分类栏
            LazyRow(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(NOTIFY_CATS) { c ->
                    FilterChip(
                        selected = cat == c.key,
                        onClick = { cat = c.key },
                        label = { Text(c.label, fontSize = 12.sp) },
                    )
                }
            }
            when {
                loading -> LoadingBox()
                filtered.isEmpty() -> EmptyBox("暂无消息")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(filtered, key = { i, it -> "${it.id}_${i}" }) { _, n ->
                        NotificationRow(
                            n = n,
                            onOpenUser = { uid -> nav.navigate("user/$uid") },
                            onClick = {
                                scope.launch {
                                    safeCall { api.readOne(n.id) }
                                    XiaodeApp.markNotifyDirty()
                                    if (n.momentId != null && n.momentId > 0) {
                                        nav.navigate("detail/${n.momentId}")
                                    }
                                    reload()
                                }
                            },
                            onPin = {
                                scope.launch {
                                    when (val r = safeCall { api.pinNotification(n.id) }) {
                                        is ApiResult.Ok -> reload()
                                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    when (val r = safeCall { api.deleteNotification(n.id) }) {
                                        is ApiResult.Ok -> { reload(); XiaodeApp.markNotifyDirty() }
                                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                    }
                                }
                            },
                        )
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NotificationRow(
    n: NotificationOut,
    onClick: () -> Unit,
    onPin: () -> Unit = {},
    onDelete: () -> Unit = {},
    // AVATAR_CLICK_PROFILE_V1: 点头像进对方主页
    onOpenUser: (Int) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    val icon = when (n.type) {
        "moment_like" -> Icons.Filled.Favorite
        "comment_like" -> Icons.Filled.ThumbUp
        "comment", "reply" -> Icons.Filled.ChatBubble
        "follow" -> Icons.Filled.PersonAdd
        "audit" -> Icons.Filled.Verified
        "report" -> Icons.Filled.Flag
        "friend_request", "friend_accept" -> Icons.Filled.PersonAdd
        else -> Icons.Filled.Notifications
    }
    val text = when (n.type) {
        "moment_like" -> "赞了你的帖子"
        "comment_like" -> "赞了你的评论"
        "comment" -> "评论了你"
        "reply" -> "回复了你"
        "follow" -> "关注了你"
        "audit" -> n.content.ifBlank { "审核结果" }
        "report" -> n.content.ifBlank { "你的举报已提交" }
        "friend_request" -> "请求加你为好友"
        "friend_accept" -> "通过了你的好友申请"
        else -> n.content
    }
    // 系统自身通知（审核/举报回执）没有第三方 actor，用图标代替头像
    val systemType = n.type == "audit" || n.type == "report"
    Box {
        Surface(
            color = if (n.isRead) Color.White else Color(0xFFF0F4FF),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (systemType) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(BrandBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (n.type == "audit") Icons.Filled.Verified else Icons.Filled.Flag,
                            null, tint = Color.White, modifier = Modifier.size(22.dp),
                        )
                    }
                } else {
                    // AVATAR_CLICK_PROFILE_V1: 点头像进对方主页
                    XiaodeAvatar(
                        n.actorAvatar, 40.dp,
                        onClick = { if (n.actorId != 0) onOpenUser(n.actorId) },
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (systemType) "系统消息" else n.actorNickname.ifBlank { n.actorUsername },
                            fontWeight = FontWeight.Medium, fontSize = 14.sp,
                        )
                        if (n.isPinned) {
                            Spacer(Modifier.width(6.dp))
                            Text("置顶", fontSize = 10.sp, color = Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF44336)).padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(timeAgo(n.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                if (!n.isRead) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFF44336)))
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(if (n.isPinned) "取消置顶" else "置顶") }, onClick = { menu = false; onPin() })
            DropdownMenuItem(text = { Text("删除", color = Color(0xFFF44336)) }, onClick = { menu = false; onDelete() })
        }
    }
}
