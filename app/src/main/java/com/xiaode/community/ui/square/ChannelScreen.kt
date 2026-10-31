package com.xiaode.community.ui.square
import kotlinx.coroutines.launch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.EmptyBox
import com.xiaode.community.ui.common.LoadingBox
import com.xiaode.community.ui.theme.BrandBlue
import coil.compose.AsyncImage
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun ChannelScreen(nav: NavController, embedded: Boolean = false) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    var all by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var pendingAvatar by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        (safeCall { api.channels() }).let { if (it is ApiResult.Ok) all = it.data }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uriRes ->
        val uri = uriRes ?: return@rememberLauncherForActivityResult
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val tmp = File(ctx.cacheDir, "newch_${System.currentTimeMillis()}.jpg")
                tmp.writeBytes(bytes)
                pendingAvatar = tmp.absolutePath
            }
        } catch (_: Exception) {}
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text("频道", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showJoin = true }) {
                            Icon(Icons.Filled.AddCircleOutline, "加入频道", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = { showCreate = true }) {
                            Icon(Icons.Filled.Add, "创建频道", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(40.dp).clickable { showCreate = true },
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("创建频道", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                Surface(
                    color = Color.White, shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, BrandBlue),
                    modifier = Modifier.weight(1f).height(40.dp).clickable { showJoin = true },
                ) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.AddCircleOutline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("加入频道", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            if (loading) { LoadingBox() }
            else if (all.isEmpty()) { EmptyBox("暂无频道，快来创建第一个吧~" ) }
            else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(all, key = { it.id }) { ch ->
                        ChannelCard(
                            ch = ch,
                            onOpen = { nav.navigate("channel_detail/${ch.id}") },
                            onJoin = {
                                scope.launch {
                                    when (val r = safeCall { api.toggleJoin(ch.id) }) {
                                        is ApiResult.Ok -> { snackbar.showSnackbar("已加入「${ch.name}」"); reload() }
                                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                    }
                                }
                            },
                        )
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }

    if (showCreate) {
        CreateChannelDialog(
            avatarPath = pendingAvatar,
            onPickAvatar = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onDismiss = { showCreate = false; pendingAvatar = null },
            onCreate = { name, intro ->
                scope.launch {
                    when (val r = safeCall { api.createChannel(ChannelCreateIn(name, intro, "")) }) {
                        is ApiResult.Ok -> {
                            val newId = r.data.id
                            val ap = pendingAvatar
                            if (ap != null) {
                                try {
                                    val tmp = File(ap)
                                    val part = MultipartBody.Part.createFormData("file", tmp.name, tmp.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                                    safeCall { api.uploadChannelAvatar(newId, part) }
                                } catch (_: Exception) {}
                            }
                            showCreate = false; pendingAvatar = null
                            snackbar.showSnackbar("频道已提交，等待管理员审核")
                            reload()
                        }
                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                    }
                }
            },
        )
    }

    if (showJoin) {
        JoinChannelDialog(
            channels = all.filter { !it.joined && it.status == "approved" },
            onDismiss = { showJoin = false },
            onJoin = { ch ->
                scope.launch {
                    when (val r = safeCall { api.toggleJoin(ch.id) }) {
                        is ApiResult.Ok -> { snackbar.showSnackbar("已加入「${ch.name}」"); showJoin = false; reload() }
                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(ch: Channel, onOpen: () -> Unit, onJoin: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().height(150.dp).combinedClickable(onClick = {}, onDoubleClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(64.dp)) {
                if (ch.image.isNotBlank()) {
                    AsyncImage(
                        model = ApiClient.absUrl(ch.image), contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Box(
                        Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Text(ch.name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 24.sp) }
                }
                if (ch.status == "pending") {
                    Surface(color = Color(0xFFFF9800), shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)) {
                        Text("审", fontSize = 8.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                ch.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text("${ch.memberCount} 人", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (ch.joined) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("已加入", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), fontWeight = FontWeight.Medium)
                }
            } else {
                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onJoin)) {
                    Text("加入", fontSize = 11.sp, color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
@Composable
private fun CreateChannelDialog(
    avatarPath: String?,
    onPickAvatar: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var intro by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建频道") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onPickAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarPath != null) {
                        AsyncImage(
                            model = File(avatarPath), contentDescription = "频道头像",
                            contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(CircleShape),
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AddAPhoto, "选择头像", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("频道头像", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("频道名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = intro, onValueChange = { intro = it },
                    label = { Text("简介（可选）") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                Text("创建后需管理员审核通过才公开", fontSize = 12.sp, color = Color(0xFFFF9800))
            }
        },
        confirmButton = {
            TextButton(enabled = name.trim().length >= 2, onClick = { onCreate(name.trim(), intro.trim()) }) {
                Text("提交")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun JoinChannelDialog(channels: List<Channel>, onDismiss: () -> Unit, onJoin: (Channel) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入频道") },
        text = {
            if (channels.isEmpty()) {
                Text("暂无可加入的频道", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(channels.size) { i ->
                        val ch = channels[i]
                        Row(
                            Modifier.fillMaxWidth().clickable { onJoin(ch) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (ch.image.isNotBlank()) {
                                AsyncImage(
                                    model = ApiClient.absUrl(ch.image), contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp).clip(CircleShape),
                                )
                            } else {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                    Text(ch.name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ch.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text("${ch.memberCount} 成员", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("加入", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
