package com.xiaode.community.ui.square

import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.launch

/** 搜索页：搜帖子 / 搜频道 */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun SearchScreen(nav: NavController, initialTab: Int = 0) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(initialTab) }   // 0=帖子 1=频道
    var keyword by remember { mutableStateOf("") }
    var moments by remember { mutableStateOf<List<MomentOut>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    // UID_SEARCH_V1: 用户搜索结果
    var users by remember { mutableStateOf<List<UserSearchItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    suspend fun doSearch() {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        loading = true; searched = true
        when (tab) {
            0 -> {
                when (val r = safeCall { api.searchMoments(kw) }) {
                    is ApiResult.Ok -> moments = r.data
                    is ApiResult.Err -> moments = emptyList()
                }
            }
            1 -> {
                when (val r = safeCall { api.searchChannels(kw) }) {
                    is ApiResult.Ok -> channels = r.data
                    is ApiResult.Err -> channels = emptyList()
                }
            }
            else -> {
                // UID_SEARCH_V1: 用户搜索（UID / 昵称 / 用户名）
                when (val r = safeCall { api.searchUsersByUid(kw) }) {
                    is ApiResult.Ok -> users = r.data
                    is ApiResult.Err -> users = emptyList()
                }
            }
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        placeholder = { Text("搜索帖子或频道", fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { doSearch() } }) {
                        Icon(Icons.Filled.Search, "搜索", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0; scope.launch { doSearch() } }, text = { Text("帖子") })
                Tab(selected = tab == 1, onClick = { tab = 1; scope.launch { doSearch() } }, text = { Text("频道") })
                Tab(selected = tab == 2, onClick = { tab = 2; scope.launch { doSearch() } }, text = { Text("用户") })
            }
            when {
                loading -> LoadingBox()
                !searched -> EmptyBox(
                    "输入关键词，搜索" + when (tab) {
                        0 -> "帖子"
                        1 -> "频道"
                        else -> "用户（可直接输入 8 位 UID）"
                    }
                )
                tab == 0 -> {
                    if (moments.isEmpty()) EmptyBox("没有找到相关帖子")
                    else LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(moments, key = { i, it -> "${it.id}_$i" }) { _, m ->
                            MomentCard(
                                m = m,
                                myId = XiaodeApp.store.currentUserJson?.let { extractId(it) } ?: 0,
                                onLike = { scope.launch { safeCall { api.likeMoment(m.id) }; doSearch() } },
                                onDelete = {},
                                onOpenDetail = { nav.navigate("detail/${m.id}") },
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
                tab == 1 -> {
                    if (channels.isEmpty()) EmptyBox("没有找到相关频道")
                    else LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(channels, key = { i, it -> "${it.id}_$i" }) { _, ch ->
                            SearchChannelRow(ch) { nav.navigate("channel_detail/${ch.id}") }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
                else -> {
                    // UID_SEARCH_V1: 用户结果
                    if (users.isEmpty()) EmptyBox("没有找到相关用户")
                    else LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(users, key = { i, it -> "u_${it.id}_$i" }) { _, u ->
                            SearchUserRow(u) { nav.navigate("user/${u.id}") }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

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


// ============================================================
// UID_SEARCH_V1: 用户搜索结果行
// ============================================================
@Composable
private fun SearchUserRow(u: UserSearchItem, onClick: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            XiaodeAvatar(u.avatar, 44.dp, isVip = u.isVip)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        u.nickname.ifBlank { u.username },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (u.isVip) {
                        Spacer(Modifier.width(5.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFFFB300).copy(alpha = 0.18f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text("VIP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB26A00))
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // UID 徽标
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f))
                            .padding(horizontal = 7.dp, vertical = 1.5.dp),
                    ) {
                        Text(
                            "UID " + u.uid.ifBlank { "-" },
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (u.matchType == "uid") {
                        Spacer(Modifier.width(5.dp))
                        Text("精准匹配", fontSize = 10.sp, color = BrandBlue.copy(alpha = 0.8f))
                    }
                }
                if (u.bio.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        u.bio,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
