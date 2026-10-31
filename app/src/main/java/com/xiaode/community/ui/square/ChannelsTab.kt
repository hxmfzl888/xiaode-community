package com.xiaode.community.ui.square

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*

/**
 * 频道页 —— 参照 StarFree「分类」逻辑
 * StarFree 结构：分类卡片网格（封面图 + 名称 + 描述），点击进入该分类的帖子列表
 */
@Composable
fun ChannelsTab(nav: NavController) {
    var loading by remember { mutableStateOf(true) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var keyword by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        loading = true
        when (val r = safeCall { Repo.api.channels() }) {
            is ApiResult.Ok -> channels = r.data
            is ApiResult.Err -> channels = emptyList()
        }
        loading = false
    }

    val filtered = remember(channels, keyword) {
        if (keyword.isBlank()) channels
        else channels.filter { it.name.contains(keyword, true) || it.intro.contains(keyword, true) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ===== 头部：标题 + 我的频道 + 搜索 =====
        EntranceAnimation(key = "ch_head") {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "频道", fontSize = 21.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${channels.size} 个", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .pressScale { nav.navigate("my_channels") }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Tune, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "我的频道", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth().height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = keyword, onValueChange = { keyword = it }, singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (keyword.isEmpty()) Text(
                                "搜索频道", fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            inner()
                        },
                    )
                    if (keyword.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp).pressScale { keyword = "" },
                        )
                    }
                }
            }
        }

        // ===== 分类卡片网格（StarFree 核心）=====
        Box(Modifier.fillMaxSize()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(30.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                    )
                }
                filtered.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Tag, null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (keyword.isBlank()) "还没有频道" else "没有找到「$keyword」",
                        fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "去创建一个 →", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
                        modifier = Modifier.pressScale { nav.navigate("my_channels") },
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(filtered, key = { _, it -> "ch_${it.id}" }) { index, ch ->
                        StaggeredItem(index) {
                            ChannelCard(ch) { nav.navigate("channel_detail/" + ch.id) }
                        }
                    }
                }
            }
        }
    }
}

/** 频道卡片 —— StarFree 分类卡片样式：封面 + 名称 + 描述 */
@Composable
private fun ChannelCard(ch: Channel, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .pressScale(onClick = onClick),
    ) {
        // 封面（有图用图，无图用渐变 + 首字）
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
        ) {
            if (ch.image.isNotBlank()) {
                AsyncImage(
                    model = ApiClient.absUrl(ch.image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // 底部渐变遮罩，让文字更清晰
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                            )
                        )
                )
            } else {
                // 无封面：薄荷渐变 + 首字
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(primary.copy(alpha = 0.75f), primary.copy(alpha = 0.45f))
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ch.name.take(1),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
        }

        // 名称 + 描述
        Column(Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            Text(
                ch.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                ch.intro.ifBlank { "暂无简介" },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
            )
        }
    }
}