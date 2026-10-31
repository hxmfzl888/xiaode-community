package com.xiaode.community.ui.appstore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.launch
import com.xiaode.community.ui.common.SkeletonPostList
import com.xiaode.community.ui.common.EmptyState
import com.xiaode.community.ui.common.IconEmptyState
import androidx.compose.material.icons.filled.Android

/** 应用商店主页：列表 + 搜索 + 上传入口 */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun AppStoreTab(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var keyword by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    suspend fun load() {
        loading = true
        when (val r = safeCall { api.apps(keyword.trim(), category) }) {
            is ApiResult.Ok -> apps = r.data
            is ApiResult.Err -> apps = emptyList()
        }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f).height(40.dp)
                            .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, "搜索", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = keyword,
                            onValueChange = { keyword = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(BrandBlue),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { scope.launch { load() } }),
                            decorationBox = { inner ->
                                if (keyword.isEmpty()) Text("搜索应用", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                inner()
                            },
                        )
                        if (keyword.isNotEmpty()) {
                            Icon(Icons.Filled.Close, "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp).clickable { keyword = ""; scope.launch { load() } })
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { scope.launch { load() } }) { Text("搜索", color = MaterialTheme.colorScheme.primary) }
                }
            }
        },
        floatingActionButton = {
            SmallFloatingActionButton(
                onClick = { nav.navigate("app_upload") },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = androidx.compose.foundation.shape.CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
            ) {
                Icon(Icons.Filled.Add, "上传应用", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    "我的上传",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { nav.navigate("app_mine") }.padding(horizontal = 4.dp, vertical = 2.dp),
                )
                Spacer(Modifier.weight(1f))
            }
            val cats = listOf("" to "全部", "游戏" to "游戏", "工具" to "工具", "社交" to "社交", "影音" to "影音", "阅读" to "阅读", "其他" to "其他")
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cats.size) { i ->
                    val (key, label) = cats[i]
                    val sel = category == key
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) BrandBlue else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { category = key; scope.launch { load() } }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
            when {
                loading -> SkeletonPostList(4)
                apps.isEmpty() -> IconEmptyState(
                    icon = Icons.Filled.Android,
                    title = "还没有应用",
                    desc = "上传你的第一个应用",
                    actionText = "上传应用",
                    onAction = { nav.navigate("app_upload") },
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(apps, key = { i, it -> "${it.id}_$i" }) { _, a ->
                        AppRow(a) { nav.navigate("app_detail/${a.id}") }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }
}

@Composable
fun AppRow(a: AppOut, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (a.icon.isBlank()) Text(a.name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                else coil.compose.AsyncImage(
                    model = ApiClient.absUrl(a.icon), contentDescription = null,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (a.version.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text("v${a.version}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (a.uploadType == "direct") {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF9800).copy(alpha = 0.14f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text("官方", fontSize = 9.sp, color = Color(0xFFFF9800))
                        }
                    }
                }
                if (a.summary.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(a.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(3.dp))
                Text("${a.downloadCount} 次下载 · ${a.uploaderName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) { Text("查看", fontSize = 12.sp) }
        }
    }
}
