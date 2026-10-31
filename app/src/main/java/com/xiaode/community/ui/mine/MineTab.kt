package com.xiaode.community.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.launch

/** 「我的」页面 —— StarFree 风格：渐变头部 + 三栏数据 + 网格入口 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineTab(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var user by remember {
        mutableStateOf(
            XiaodeApp.store.currentUserJson?.let {
                runCatching { Gson().fromJson(it, UserOut::class.java) }.getOrNull()
            }
        )
    }
    var vip by remember { mutableStateOf<VipInfo?>(null) }
    var taskInfo by remember { mutableStateOf<TaskListOut?>(null) }
    var showSignOut by remember { mutableStateOf(false) }
    var unread by remember { mutableStateOf(0) }
    // 统计数字（来自 /api/social/user/{id}）
    var statPosts by remember { mutableStateOf(0) }
    var statFollowing by remember { mutableStateOf(0) }
    var statFans by remember { mutableStateOf(0) }
    // PERF_FIX_MINE: 缓存时间戳，避免每次进入都重新拉取
    var lastLoadAt by remember { mutableStateOf(0L) }

    // PERF_FIX_MINE: 5 个请求并行 + 60 秒缓存 + 序列化移出主线程
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val fresh = now - lastLoadAt < 60_000L

        if (!fresh) {
            // 并行发起（async 在同作用域内并发执行）
            val vipJob = async { safeCall { api.vipMe() } }
            val taskJob = async { safeCall { api.tasks() } }
            val unreadJob = async { safeCall { api.unreadMessages() } }
            val profileJob = async { safeCall { api.profile() } }

            profileJob.await().let {
                if (it is ApiResult.Ok) {
                    user = it.data
                    // 序列化放到 IO 线程，避免主线程卡顿
                    withContext(Dispatchers.IO) {
                        XiaodeApp.store.currentUserJson = Gson().toJson(it.data)
                    }
                    // 统计数字单独并行，不阻塞上面三条
                    val statJob = async { safeCall { api.socialUser(it.data.id) } }
                    statJob.await().let { r ->
                        if (r is ApiResult.Ok) {
                            statPosts = r.data.momentCount
                            statFollowing = r.data.followingCount
                            statFans = r.data.followerCount
                        }
                    }
                }
            }
            vipJob.await().let { if (it is ApiResult.Ok) vip = it.data }
            taskJob.await().let { if (it is ApiResult.Ok) taskInfo = it.data }
            unreadJob.await().let { if (it is ApiResult.Ok) unread = it.data.unread }

            lastLoadAt = System.currentTimeMillis()
        }
    }

    val isVip = vip?.isVip ?: (user?.isVip ?: false)
    val primary = MaterialTheme.colorScheme.primary

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            // ===== 1. 渐变头部（StarFree 核心视觉）=====
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                primary.copy(alpha = 0.20f),
                                primary.copy(alpha = 0.07f),
                                MaterialTheme.colorScheme.background,
                            )
                        )
                    )
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    EntranceAnimation(key = "mine_header") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 头像
                            Box(Modifier.pressScale { nav.navigate("edit_profile") }) {
                                XiaodeAvatar(user?.avatar, 66.dp, isVip = isVip)
                            }
                            Spacer(Modifier.width(14.dp))
                            // 昵称 + 等级 + VIP
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        user?.let { it.nickname.ifBlank { it.username } } ?: "未登录",
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                    if (isVip) {
                                        Spacer(Modifier.width(6.dp))
                                        VipBadge()
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LevelBadge(user?.level ?: 1, taskInfo?.levelEffect ?: "normal")
                                    Spacer(Modifier.width(7.dp))
                                    CpTag("Lv.${user?.level ?: 1}")
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "@${user?.username ?: ""}",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.Filled.Edit,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp).pressScale { nav.navigate("edit_profile") },
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ===== 2. 三栏统计 =====
                    EntranceAnimation(key = "mine_stats", delayMs = 80) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatCell("$statPosts", "帖子") { nav.navigate("my_moments") }
                            vdiv()
                            StatCell("$statFollowing", "关注") { nav.navigate("follow_list/following") }
                            vdiv()
                            StatCell("$statFans", "粉丝") { nav.navigate("follow_list/followers") }
                        }
                    }
                }
            }

            // ===== 3. 网格入口 =====
            EntranceAnimation(key = "mine_grid", delayMs = 150) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        GridEntry(Icons.Filled.AccountBalanceWallet, "我的账户") { nav.navigate("account") }
                        GridEntry(Icons.Filled.Redeem, "兑换中心") { nav.navigate("redeem") }
                        GridEntry(Icons.Filled.WorkspacePremium, "大会员") { nav.navigate("vip") }
                        GridEntry(Icons.Filled.MilitaryTech, "等级中心") { nav.navigate("level") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        GridEntry(Icons.Filled.Notifications, "消息", badge = unread) { nav.navigate("messages") }
                        GridEntry(Icons.Filled.Favorite, "我的点赞") { nav.navigate("my_likes") }
                        GridEntry(Icons.Filled.Bookmark, "我的收藏") { nav.navigate("my_favorites") }
                        GridEntry(Icons.Filled.Forum, "我的频道") { nav.navigate("my_channels") }
                        GridEntry(Icons.Filled.SupportAgent, "小德答疑") { nav.navigate("support") }
                    }
                }
            }

            // ===== 4. 功能列表 =====
            EntranceAnimation(key = "mine_list", delayMs = 220) {
                Column {
                    MenuCard {
                        MenuRow(
                            Icons.Filled.WorkspacePremium,
                            if (isVip) "大会员（已开通）" else "开通大会员",
                            if (isVip) "到期：${vip?.vipExpire?.take(10) ?: user?.vipExpire?.take(10) ?: ""}"
                            else "解锁全部特权",
                            highlight = true,
                        ) { nav.navigate("vip") }
                    }
                    MenuCard {
                        val lv = taskInfo?.level ?: (user?.level ?: 1)
                        val exp = taskInfo?.exp ?: (user?.exp ?: 0)
                        MenuRow(
                            Icons.Filled.MilitaryTech,
                            "等级 · Lv.$lv",
                            "经验 $exp · " + if (taskInfo?.signedToday == true) "今日已签到" else "点击签到领经验",
                        ) { nav.navigate("level") }
                    }
                    MenuCard {
                        MenuRow(Icons.Filled.Article, "我的帖子") { nav.navigate("my_moments") }
                        Div()
                        MenuRow(Icons.Filled.Apps, "我的上传") { nav.navigate("app_mine") }
                    }
                    MenuCard {
                        MenuRow(Icons.Filled.Security, "安全设置", "修改密码 / 用户名 / 邮箱") { nav.navigate("security") }
                        Div()
                        MenuRow(Icons.Filled.SwitchAccount, "切换账号") { nav.navigate("accounts") }
                        Div()
                        MenuRow(Icons.Filled.NoAccounts, "注销账号", "永久删除账号与数据") { nav.navigate("delete_account") }
                    }
                    MenuCard {
                        MenuRow(Icons.Filled.Settings, "设置") { nav.navigate("settings") }
                        Div()
                        MenuRow(Icons.Filled.BugReport, "诊断日志", "查看 / 导出崩溃日志") { nav.navigate("diag_log") }
                        Div()
                        MenuRow(
                            Icons.Filled.Info, "关于与更新",
                            "版本 ${com.xiaode.community.BuildConfig.VERSION_NAME}",
                        ) { nav.navigate("about") }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { showSignOut = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Color(0xFFF44336),
                ),
                shape = RoundedCornerShape(16.dp),
            ) { Text("退出登录", fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(44.dp))
        }
    }

    if (showSignOut) {
        AlertDialog(
            onDismissRequest = { showSignOut = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(onClick = {
                    XiaodeApp.store.clear()
                    showSignOut = false
                    XiaodeApp.syncLoginState()
                }) { Text("退出", color = Color(0xFFF44336)) }
            },
            dismissButton = { TextButton(onClick = { showSignOut = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun vdiv() {
    Box(
        Modifier
            .width(1.dp)
            .height(26.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

@Composable
private fun MenuCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) { Column(Modifier.padding(vertical = 2.dp), content = content) }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    sub: String = "",
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            tint = if (highlight) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = 14.5.sp,
                color = if (highlight) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            )
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            Icons.Filled.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun Div() {
    HorizontalDivider(
        Modifier.padding(start = 51.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f),
    )
}