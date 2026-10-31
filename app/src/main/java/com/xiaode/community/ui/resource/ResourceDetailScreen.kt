package com.xiaode.community.ui.resource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.common.EntranceAnimation
import kotlinx.coroutines.launch

/** 资源详情：封面 / 截图 / 说明 / 下载 / 打赏 / 购买 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ResourceDetailScreen(nav: NavController, rid: Int) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var r by remember { mutableStateOf<ResourceOut?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showTip by remember { mutableStateOf(false) }
    var tipAmount by remember { mutableStateOf("5") }
    var viewing by remember { mutableStateOf<List<String>>(emptyList()) }
    var viewingIndex by remember { mutableIntStateOf(0) }
    // RES_COMMENT_V1: 评论
    var comments by remember { mutableStateOf<List<ResourceCommentOut>>(emptyList()) }
    var commentText by remember { mutableStateOf("") }
    val myId = XiaodeApp.store.currentUserJson?.let {
        com.xiaode.community.ui.square.extractId(it)
    } ?: 0
    suspend fun loadComments() {
        val cr = safeCall { Repo.api.resourceComments(rid) }
        if (cr is ApiResult.Ok) comments = cr.data
    }

    suspend fun reload() {
        when (val res = safeCall { Repo.api.resourceDetail(rid) }) {
            is ApiResult.Ok -> r = res.data
            is ApiResult.Err -> snackbar.showSnackbar(res.message)
        }
        loading = false
    }
    LaunchedEffect(rid) {
        reload()
        loadComments()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("资源详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            Icons.Filled.ArrowBack, "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            val res = r
            if (res != null) {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showTip = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Favorite, null,
                                tint = Color(0xFFF06292),
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("打赏", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.width(9.dp))
                        val needBuy = res.isPaid && !res.unlocked
                        Button(
                            onClick = {
                                scope.launch {
                                    if (needBuy) {
                                        when (val x = safeCall { Repo.api.resUnlock(rid) }) {
                                            is ApiResult.Ok -> {
                                                snackbar.showSnackbar(x.data.msg.ifBlank { "购买成功" })
                                                reload()
                                            }
                                            is ApiResult.Err -> snackbar.showSnackbar(x.message)
                                        }
                                    } else {
                                        when (val x = safeCall { Repo.api.resDownload(rid) }) {
                                            is ApiResult.Ok -> {
                                                val u = x.data.url
                                                if (u.isBlank()) {
                                                    snackbar.showSnackbar("该资源暂无下载地址")
                                                } else {
                                                    try {
                                                        ctx.startActivity(
                                                            android.content.Intent(
                                                                android.content.Intent.ACTION_VIEW,
                                                                android.net.Uri.parse(u),
                                                            )
                                                        )
                                                    } catch (e: Exception) {
                                                        copyText(ctx, u)
                                                    }
                                                }
                                            }
                                            is ApiResult.Err -> snackbar.showSnackbar(x.message)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(vertical = 11.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp),
                        ) {
                            Icon(
                                if (needBuy) Icons.Filled.ShoppingCart else Icons.Filled.Download,
                                null, modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (needBuy) "支付 ${res.price} 金币下载" else "立即下载",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        if (loading) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                )
            }
            return@Scaffold
        }
        val res = r
        if (res == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("资源不存在")
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(pad).fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 10.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                EntranceAnimation(key = "res_head", offsetY = -20) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(13.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.size(62.dp).clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (res.cover.isBlank()) {
                                    Text(
                                        res.name.take(1),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    AsyncImage(
                                        model = ApiClient.absUrl(res.cover),
                                        contentDescription = res.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    res.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 23.sp,
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (res.isPaid) {
                                        Box(
                                            Modifier.clip(RoundedCornerShape(5.dp))
                                                .background(Color(0xFFFF9800).copy(alpha = 0.14f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                "${res.price} 金币",
                                                fontSize = 10.sp,
                                                color = Color(0xFFF57C00),
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    } else {
                                        Box(
                                            Modifier.clip(RoundedCornerShape(5.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                "免费",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    if (res.category.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            res.category,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (res.summary.isNotBlank()) {
                            Spacer(Modifier.height(9.dp))
                            Text(
                                res.summary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(11.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (res.avatar.isBlank()) {
                                    Text(
                                        res.uploaderName.ifBlank { "?" }.take(1),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    AsyncImage(
                                        model = ApiClient.absUrl(res.avatar),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(26.dp).clip(CircleShape),
                                    )
                                }
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(
                                res.uploaderName.ifBlank { "匿名" },
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clickable {
                                    if (res.uploaderId > 0) nav.navigate("user/" + res.uploaderId)
                                },
                            )
                            Spacer(Modifier.weight(1f))
                            StatText(Icons.Filled.Download, "${res.downloadCount}")
                            Spacer(Modifier.width(10.dp))
                            StatText(Icons.Filled.Visibility, "${res.viewCount}")
                            if (res.tipTotal > 0) {
                                Spacer(Modifier.width(10.dp))
                                StatText(Icons.Filled.Favorite, "${res.tipTotal}", Color(0xFFF06292))
                            }
                        }
                    }
                }
            }

            if (res.screenshots.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(13.dp)
                    ) {
                        Text(
                            "演示截图",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(9.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(res.screenshots) { idx, s ->
                                AsyncImage(
                                    model = ApiClient.absUrl(s),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(130.dp).height(200.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewingIndex = idx
                                            viewing = res.screenshots
                                        },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(13.dp)
                ) {
                    Text(
                        "详细说明",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        res.description.ifBlank { "作者未填写详细说明" },
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // RES_COMMENT_V1: 评论区（复用统一组件，资源无星级）
            CommentSection(
                comments = comments.map {
                    UiComment(
                        id = it.id,
                        userId = it.userId,
                        nickname = it.nickname,
                        avatar = it.avatar,
                        content = it.content,
                        createdAt = it.createdAt,
                    )
                },
                showRating = false,
                inputText = commentText,
                onInputChange = { commentText = it },
                onSubmit = {
                    if (commentText.isNotBlank()) {
                        val txt = commentText.trim()
                        scope.launch {
                            when (val r2 = safeCall { Repo.api.addResourceComment(rid, ResourceCommentIn(txt)) }) {
                                is ApiResult.Ok -> {
                                    commentText = ""
                                    snackbar.showSnackbar("评论已发布")
                                    loadComments()
                                }
                                is ApiResult.Err -> snackbar.showSnackbar(r2.message)
                            }
                        }
                    }
                },
                onDelete = { c ->
                    scope.launch {
                        val r3 = safeCall { Repo.api.deleteResourceComment(c.id) }
                        if (r3 is ApiResult.Err) {
                            snackbar.showSnackbar(r3.message)
                        } else {
                            snackbar.showSnackbar("已删除")
                            loadComments()
                        }
                    }
                },
                myId = myId,
                title = "全部评论",
            )

            item {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                res.status == "pending" -> "该资源正在审核中，仅自己可见"
                                res.status == "rejected" -> "审核未通过：" + res.rejectReason.ifBlank { "违反社区规则" }
                                res.isPaid && !res.unlocked -> "付费资源，购买后可下载"
                                else -> "资源已通过审核，可正常下载"
                            },
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (res.fileSize > 0) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "文件大小：${res.fileSize / 1024 / 1024} MB",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showTip) {
        AlertDialog(
            onDismissRequest = { showTip = false },
            title = { Text("打赏作者", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "感谢作者分享，打赏会全额转给作者",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("5", "10", "20", "50").forEach { a ->
                            val on = tipAmount == a
                            Box(
                                Modifier.clip(RoundedCornerShape(50))
                                    .background(
                                        if (on) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { tipAmount = a }
                                    .padding(horizontal = 13.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    "$a 币",
                                    fontSize = 12.sp,
                                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tipAmount,
                        onValueChange = { tipAmount = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("自定义金额", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = tipAmount.toIntOrNull() ?: 0
                    if (amt > 0) {
                        scope.launch {
                            when (val x = safeCall { Repo.api.resTip(rid, ResTipIn(amt)) }) {
                                is ApiResult.Ok -> {
                                    snackbar.showSnackbar("打赏成功，感谢支持！")
                                    showTip = false
                                    reload()
                                }
                                is ApiResult.Err -> snackbar.showSnackbar(x.message)
                            }
                        }
                    }
                }) { Text("确认打赏", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showTip = false }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    if (viewing.isNotEmpty()) {
        com.xiaode.community.ui.common.ImageViewerDialog(
            viewing, viewingIndex, { viewing = emptyList() }, allowDownload = true,
        )
    }
}

@Composable
private fun StatText(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon, null,
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun copyText(ctx: android.content.Context, text: String) {
    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
    android.widget.Toast.makeText(ctx, "链接已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
}