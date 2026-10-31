package com.xiaode.community.ui.appstore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

/** 我上传的应用 */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun AppMineScreen(nav: NavController) {
    val api = Repo.api
    var apps by remember { mutableStateOf<List<AppOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        (safeCall { api.myApps() }).let { if (it is ApiResult.Ok) apps = it.data }
        loading = false
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的上传", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background)) {
            when {
                loading -> LoadingBox()
                apps.isEmpty() -> EmptyBox("你还没有上传过应用")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(apps, key = { i, it -> "${it.id}_$i" }) { _, a ->
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(a.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${a.downloadCount} 次下载", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val (txt, col) = when (a.status) {
                                    "approved" -> "已通过" to Color(0xFF4CAF50)
                                    "rejected" -> ("已驳回：${a.rejectReason}") to Color(0xFFF44336)
                                    else -> "审核中" to Color(0xFFFF9800)
                                }
                                Text(txt, fontSize = 12.sp, color = col)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
