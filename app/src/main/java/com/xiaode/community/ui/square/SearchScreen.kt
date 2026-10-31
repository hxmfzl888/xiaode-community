package com.xiaode.community.ui.square

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
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    suspend fun doSearch() {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        loading = true; searched = true
        if (tab == 0) {
            when (val r = safeCall { api.searchMoments(kw) }) {
                is ApiResult.Ok -> moments = r.data
                is ApiResult.Err -> moments = emptyList()
            }
        } else {
            when (val r = safeCall { api.searchChannels(kw) }) {
                is ApiResult.Ok -> channels = r.data
                is ApiResult.Err -> channels = emptyList()
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
            }
            when {
                loading -> LoadingBox()
                !searched -> EmptyBox("输入关键词，搜索${if (tab == 0) "帖子" else "频道"}")
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
                else -> {
                    if (channels.isEmpty()) EmptyBox("没有找到相关频道")
                    else LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(channels, key = { i, it -> "${it.id}_$i" }) { _, ch ->
                            SearchChannelRow(ch) { nav.navigate("channel_detail/${ch.id}") }
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
