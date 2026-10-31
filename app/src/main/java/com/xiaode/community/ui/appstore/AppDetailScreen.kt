package com.xiaode.community.ui.appstore

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue
import com.xiaode.community.ui.square.ReportDialog
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Flag
import kotlinx.coroutines.launch

/** 应用详情页：截图、描述、下载、评论 */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun AppDetailScreen(nav: NavController, appId: Int) {
    var shotViewer by remember { mutableStateOf<List<String>>(emptyList()) }
    var shotIndex by remember { mutableIntStateOf(0) }
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    var app by remember { mutableStateOf<AppOut?>(null) }
    var comments by remember { mutableStateOf<List<AppCommentOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var commentText by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(5) }
    // UNIFIED_COMMENT_UI_V1: 当前用户 ID（判断能否删自己的评论）
    val myId = XiaodeApp.store.currentUserJson?.let {
        com.xiaode.community.ui.square.extractId(it)
    } ?: 0
    var reportOpen by remember { mutableStateOf(false) }
    var tipOpen by remember { mutableStateOf(false) }

    suspend fun reload() {
        when (val r = safeCall { api.appDetail(appId) }) {
            is ApiResult.Ok -> app = r.data
            is ApiResult.Err -> snackbar.showSnackbar(r.message)
        }
        (safeCall { api.appComments(appId) }).let { if (it is ApiResult.Ok) comments = it.data }
        loading = false
    }
    LaunchedEffect(appId) { reload() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(app?.name ?: "应用详情", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                actions = {
                    app?.let {
                        IconButton(onClick = { reportOpen = true }) {
                            Icon(Icons.Filled.Flag, "举报", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
        bottomBar = {
            app?.let { a ->
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                scope.launch {
                                    if (a.isPaid) {
                                        when (val pr = safeCall { api.purchaseApp(a.id) }) {
                                            is ApiResult.Ok -> if (pr.data.message == "insufficient") { snackbar.showSnackbar("金币不足，请先充值"); return@launch }
                                            is ApiResult.Err -> { snackbar.showSnackbar(pr.message); return@launch }
                                        }
                                    }
                                    val r = safeCall { api.recordDownload(a.id) }
                                    if (r is ApiResult.Ok) {
                                        val url = ApiClient.absUrl(r.data.url)
                                        if (url.isNotBlank()) {
                                            try {
                                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            } catch (e: Exception) { snackbar.showSnackbar("无法打开下载链接") }
                                        }
                                        reload()
                                    } else if (r is ApiResult.Err) snackbar.showSnackbar(r.message)
                                }
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) { Text(if (a.isPaid) "付费 " + a.price + " 金币下载" else "下载应用（" + a.downloadCount + "）") }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = { tipOpen = true },
                            modifier = Modifier.height(46.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB300)),
                        ) {
                            Icon(Icons.Filled.Star, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("打赏")
                        }
                    }
                }
            }
        },
    ) { pad ->
        if (loading) { Box(Modifier.padding(pad).fillMaxSize()) { LoadingBox() }; return@Scaffold }
        val a = app ?: run { Box(Modifier.padding(pad).fillMaxSize()) { EmptyBox("应用不存在") }; return@Scaffold }
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            item {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            if (a.icon.isBlank()) Text(a.name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                            else AsyncImage(model = ApiClient.absUrl(a.icon), contentDescription = null,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface)
                            if (a.version.isNotBlank()) Text("版本 ${a.version}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (a.category.isNotBlank()) Text(a.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${a.viewCount} 次浏览 · ${a.downloadCount} 次下载", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (a.summary.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(a.summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (a.uploadType == "direct") {
                        Spacer(Modifier.height(6.dp))
                        Text("✓ 直传服务器（官方）", fontSize = 11.sp, color = Color(0xFF4CAF50))
                    }
                }
            }
            // 截图
            if (a.screenshots.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Text("应用截图", fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp))
                    Spacer(Modifier.height(8.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(a.screenshots.size) { i ->
                            AsyncImage(model = ApiClient.absUrl(a.screenshots[i]), contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.height(240.dp).width(150.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background)
                                    .clickable { shotIndex = i; shotViewer = a.screenshots })
                        }
                    }
                }
            }
            // 描述
            if (a.description.isNotBlank()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("应用介绍", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(a.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                        }
                    }
                }
            }
            // UNIFIED_COMMENT_UI_V1: 统一评论组件（应用带星级评分）
            CommentSection(
                comments = comments.map {
                    UiComment(
                        id = it.id,
                        userId = it.userId,
                        nickname = it.nickname,
                        avatar = it.avatar,
                        content = it.content,
                        rating = it.rating,
                        createdAt = it.createdAt,
                    )
                },
                showRating = true,
                inputText = commentText,
                onInputChange = { commentText = it },
                rating = rating,
                onRatingChange = { rating = it },
                onSubmit = {
                    if (commentText.isNotBlank()) {
                        val txt = commentText.trim()
                        scope.launch {
                            when (val r = safeCall { api.addAppComment(appId, AppCommentIn(txt, rating)) }) {
                                is ApiResult.Ok -> {
                                    commentText = ""
                                    snackbar.showSnackbar("评价已发布")
                                    reload()
                                }
                                is ApiResult.Err -> snackbar.showSnackbar(r.message)
                            }
                        }
                    }
                },
                onDelete = { c ->
                    scope.launch {
                        val r = safeCall { api.deleteAppComment(c.id) }
                        if (r is ApiResult.Err) {
                            snackbar.showSnackbar(r.message)
                        } else {
                            snackbar.showSnackbar("已删除")
                            reload()
                        }
                    }
                },
                myId = myId,
                title = "全部评价",
            )
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
    if (tipOpen) {
        var amount by remember { mutableStateOf("10") }
        AlertDialog(
            onDismissRequest = { tipOpen = false },
            title = { Text("打赏应用", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("感谢支持开发者，选择打赏金币数：", fontSize = 13.sp, color = Color(0xFF8A8FA3))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 50, 100, 500).forEach { v ->
                            val sel = amount == v.toString()
                            Box(
                                Modifier.clip(RoundedCornerShape(50))
                                    .background(if (sel) Color(0xFFFFB300) else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { amount = v.toString() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { Text(v.toString(), fontSize = 13.sp, color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() } },
                        label = { Text("金币数") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        tipOpen = false
                        val amt = amount.toIntOrNull() ?: 0
                        if (amt <= 0) return@Button
                        scope.launch {
                            when (val r = safeCall { Repo.api.tipApp(appId, AppTipIn(amt)) }) {
                                is ApiResult.Ok -> snackbar.showSnackbar("打赏成功，感谢支持！")
                                is ApiResult.Err -> snackbar.showSnackbar(r.message)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                ) { Text("确认打赏") }
            },
            dismissButton = { TextButton(onClick = { tipOpen = false }) { Text("取消") } },
        )
    }

    // 应用截图全屏查看（点第几幅就从第几幅开始）
    if (shotViewer.isNotEmpty()) {
        com.xiaode.community.ui.common.ImageViewerDialog(
            shotViewer, shotIndex, { shotViewer = emptyList() }, allowDownload = true,
        )
    }
    if (reportOpen) {
        ReportDialog(
            onDismiss = { reportOpen = false },
            onSubmit = { reason, detail ->
                reportOpen = false
                scope.launch {
                    when (val r = safeCall { Repo.api.report(ReportIn("app", appId, reason, detail)) }) {
                        is ApiResult.Ok -> snackbar.showSnackbar("举报已提交")
                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                    }
                }
            },
        )
    }
}
