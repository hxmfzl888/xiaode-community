package com.xiaode.community.ui.resource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.EmptyBox
import com.xiaode.community.ui.common.EntranceAnimation
import com.xiaode.community.ui.common.pressScale
import kotlinx.coroutines.launch
import com.xiaode.community.ui.common.SkeletonPostList
import com.xiaode.community.ui.common.EmptyState
import com.xiaode.community.ui.common.IconEmptyState
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search

/**
 * 资源中心：卡片式列表 + 搜索 + 分类筛选 + 排序
 * 布局参照 StarFree「分类」卡片网格思路，与广场频道页风格统一
 */
@Composable
fun ResourcesTab(nav: NavController) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var items by remember { mutableStateOf<List<ResourceOut>>(emptyList()) }
    var cats by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var keyword by remember { mutableStateOf("") }
    var activeCat by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("new") }
    var searchOpen by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        when (val r = safeCall {
            Repo.api.resources(q = keyword, category = activeCat, sort = sort)
        }) {
            is ApiResult.Ok -> items = r.data.items
            is ApiResult.Err -> snackbar.showSnackbar(r.message)
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        when (val r = safeCall { Repo.api.resCategories() }) {
            is ApiResult.Ok -> cats = r.data.items
            is ApiResult.Err -> {}
        }
    }
    LaunchedEffect(keyword, activeCat, sort) { load() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { nav.navigate("resource_upload") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(2.dp),
            ) {
                Icon(Icons.Filled.Add, "上传资源", modifier = Modifier.size(22.dp))
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ===== 顶部：标题 + 搜索 =====
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "资源",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.4).sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${items.size} 个",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                // 我的上传
                Row(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable { nav.navigate("resource_mine") }
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Upload, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "我的",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // ===== 搜索框 =====
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search, "搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(7.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (keyword.isEmpty()) {
                            Text(
                                "搜索资源名称 / 简介",
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                if (keyword.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close, "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp).clickable { keyword = "" },
                    )
                }
            }

            Spacer(Modifier.height(9.dp))

            // ===== 分类横条 =====
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item {
                    CatChip("全部", activeCat.isEmpty()) { activeCat = "" }
                }
                items(cats) { c ->
                    CatChip(c, activeCat == c) { activeCat = if (activeCat == c) "" else c }
                }
            }

            Spacer(Modifier.height(7.dp))

            // ===== 排序条 =====
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                listOf(
                    "new" to "最新",
                    "hot" to "最热",
                    "download" to "下载最多",
                ).forEach { (k, label) ->
                    val on = sort == k
                    Text(
                        label,
                        fontSize = if (on) 13.sp else 12.5.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { sort = k },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ===== 列表 =====
            if (loading && items.isEmpty()) {
                SkeletonPostList(4)
            } else if (items.isEmpty()) {
                IconEmptyState(
                    icon = if (keyword.isNotBlank()) Icons.Filled.Search else Icons.Filled.FolderOpen,
                    title = if (keyword.isNotBlank()) "没有找到相关资源" else "还没有资源",
                    desc = if (keyword.isNotBlank()) "换个关键词试试" else "分享你的第一个资源",
                    actionText = if (keyword.isNotBlank()) null else "上传资源",
                    onAction = if (keyword.isNotBlank()) null else ({ nav.navigate("resource_upload") }),
                )
            } else {
                val ls = rememberLazyListState()
                LazyColumn(state = ls, modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { r ->
                        EntranceAnimation(key = r.id, offsetY = -16) {
                            ResourceCard(r) { nav.navigate("resource_detail/" + r.id) }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

/** 分类胶囊 */
@Composable
private fun CatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * 资源卡片：左侧封面 + 右侧信息（与应用页统一的紧凑布局）
 * 圆角 10dp，符合 StarFree 源码 20rpx 规格
 */
@Composable
fun ResourceCard(r: ResourceOut, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .pressScale(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.Top) {
            // 封面
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (r.cover.isBlank()) {
                    Text(
                        r.name.take(1),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    AsyncImage(
                        model = ApiClient.absUrl(r.cover),
                        contentDescription = r.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                // 名称 + 付费标记
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (r.isPaid) {
                        Spacer(Modifier.width(5.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFF9800).copy(alpha = 0.14f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                "${r.price}币",
                                fontSize = 9.sp,
                                color = Color(0xFFF57C00),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Spacer(Modifier.width(5.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                "免费",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                // 简介
                if (r.summary.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        r.summary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                // 底部：上传者 + 数据
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 上传者头像（小圆）
                    Box(
                        Modifier.size(15.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (r.avatar.isBlank()) {
                            Text(
                                (r.uploaderName.ifBlank { "?" }).take(1),
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            AsyncImage(
                                model = ApiClient.absUrl(r.avatar),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(15.dp).clip(CircleShape),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        r.uploaderName.ifBlank { "匿名" },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Download, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "${r.downloadCount}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (r.category.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "#${r.category}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}
