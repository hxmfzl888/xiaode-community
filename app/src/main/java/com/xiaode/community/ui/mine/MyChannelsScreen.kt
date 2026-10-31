package com.xiaode.community.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xiaode.community.ui.common.pageFadeIn
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.EmptyBox
import com.xiaode.community.ui.common.LoadingBox
import com.xiaode.community.ui.theme.BrandBlue
import coil.compose.AsyncImage

/** 我的频道：我加入的 / 我创建的 */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun MyChannelsScreen(nav: NavController) {
    val api = Repo.api
    var tab by remember { mutableIntStateOf(0) }
    var joined by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var mine by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        (safeCall { api.joinedChannels() }).let { if (it is ApiResult.Ok) joined = it.data }
        (safeCall { api.myChannels() }).let { if (it is ApiResult.Ok) mine = it.data }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的频道", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("我加入的") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("我创建的") })
            }
            val list = if (tab == 0) joined else mine
            when {
                loading -> LoadingBox()
                list.isEmpty() -> EmptyBox(if (tab == 0) "还没有加入任何频道" else "还没有创建频道")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { ch ->
                        Row(
                            Modifier.fillMaxWidth().clickable { nav.navigate("channel_detail/${ch.id}") }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (ch.image.isNotBlank()) {
                                AsyncImage(
                                    model = ApiClient.absUrl(ch.image), contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(CircleShape),
                                )
                            } else {
                                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                    Text(ch.name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(ch.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                                    if (ch.status == "pending") {
                                        Spacer(Modifier.width(6.dp))
                                        Text("审核中", fontSize = 10.sp, color = Color(0xFFFF9800))
                                    } else if (ch.status == "rejected") {
                                        Spacer(Modifier.width(6.dp))
                                        Text("已驳回", fontSize = 10.sp, color = Color(0xFFF44336))
                                    }
                                }
                                if (ch.intro.isNotBlank()) {
                                    Text(ch.intro, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                Text("${ch.memberCount} 成员 · 创建者 ${ch.ownerName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
}
