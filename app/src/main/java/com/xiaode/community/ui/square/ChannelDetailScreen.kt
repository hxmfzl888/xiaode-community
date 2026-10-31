package com.xiaode.community.ui.square

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
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
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/** 频道详情页：头像、频道内搜索、帖子流 */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun ChannelDetailScreen(nav: NavController, cid: Int) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ctx = XiaodeApp.context

    var channel by remember { mutableStateOf<Channel?>(null) }
    var moments by remember { mutableStateOf<List<MomentOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var keyword by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }

    val myId = XiaodeApp.store.currentUserJson?.let { extractId(it) } ?: 0

    suspend fun loadMoments(kw: String = "") {
        searching = true
        val r = if (kw.isBlank()) safeCall { api.channelMoments(cid, 30, 0) }
                else safeCall { api.searchChannelMoments(cid, kw) }
        if (r is ApiResult.Ok) moments = r.data
        searching = false
    }

    LaunchedEffect(cid) {
        (safeCall { api.channelDetail(cid) }).let { if (it is ApiResult.Ok) channel = it.data }
        loadMoments(); loading = false
    }

    // 选图上传频道头像
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uriRes ->
        val uri = uriRes ?: return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val tmp = File(ctx.cacheDir, "ch_${System.currentTimeMillis()}.jpg")
                    tmp.writeBytes(bytes)
                    val part = MultipartBody.Part.createFormData(
                        "file", tmp.name, tmp.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    )
                    when (val r = safeCall { api.uploadChannelAvatar(cid, part) }) {
                        is ApiResult.Ok -> { channel = r.data; snackbar.showSnackbar("头像已更新") }
                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                    }
                    tmp.delete()
                }
            } catch (e: Exception) {
                snackbar.showSnackbar("上传失败：${e.message}")
            }
            uploading = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(channel?.name ?: "频道", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            // SF_CHANNEL_DETAIL_V2: 小圆按钮，不抢视线
            SmallFloatingActionButton(
                onClick = { nav.navigate("post?channelId=" + cid) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = androidx.compose.foundation.shape.CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(2.dp),
            ) {
                Icon(Icons.Filled.Add, "发帖", modifier = Modifier.size(22.dp))
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background)) {
            // ---- SF_CHANNEL_DETAIL_V2: 渐变头部（对齐「我的」页视觉）----
            channel?.let { ch ->
                Box(
                    Modifier.fillMaxWidth().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                MaterialTheme.colorScheme.background,
                            )
                        )
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            XiaodeAvatar(ch.image, 58.dp)
                            if (myId == ch.ownerId) {
                                Box(
                                    Modifier.align(Alignment.BottomEnd).size(21.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.CameraAlt, "换头像",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                ch.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (ch.intro.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    ch.intro,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 成员数胶囊
                                Box(
                                    Modifier.clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        "${ch.memberCount} 位成员",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "创建者 ${ch.ownerName}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            // ---- SF_CHANNEL_DETAIL_V2: 胶囊搜索条 ----
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
                                "在频道内搜索帖子",
                                fontSize = 13.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                if (keyword.isNotBlank()) {
                    Icon(
                        Icons.Filled.Close, "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp).clickable {
                            keyword = ""
                            scope.launch { loadMoments("") }
                        },
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            if (uploading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                loading || searching -> LoadingBox()
                moments.isEmpty() -> EmptyBox(if (keyword.isBlank()) "频道内还没有帖子" else "没有找到相关帖子")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(moments, key = { i, it -> "${it.id}_$i" }) { index, m ->
                        // SF_CHANNEL_DETAIL_V2: 帖子错峰淡入
                        com.xiaode.community.ui.common.EntranceAnimation(
                            key = "cd_" + m.id,
                            offsetY = -16,
                            delayMs = index.coerceAtMost(8) * 38,
                        ) {
                            MomentCard(
                                m = m, myId = myId,
                                onLike = {
                                    scope.launch {
                                        safeCall { api.likeMoment(m.id) }
                                        loadMoments(keyword)
                                    }
                                },
                                onDelete = {},
                                onOpenDetail = { nav.navigate("detail/" + m.id) },
                                onOpenUser = { uid -> nav.navigate("user/" + uid) },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}
