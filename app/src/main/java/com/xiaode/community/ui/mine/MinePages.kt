package com.xiaode.community.ui.mine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
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
import com.google.gson.Gson
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
private fun SimpleScaffold(nav: NavController, title: String, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
        content = content,
    )
}

/** 编辑资料（换头像 / 改昵称） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ctx = XiaodeApp.context
    var user by remember { mutableStateOf<UserOut?>(null) }
    var nickname by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        (safeCall { api.profile() }).let { if (it is ApiResult.Ok) { user = it.data; nickname = it.data.nickname; bio = it.data.bio } }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uriRes ->
        val uri = uriRes ?: return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val tmp = File(ctx.cacheDir, "av_${System.currentTimeMillis()}.jpg").apply { writeBytes(bytes) }
                val part = MultipartBody.Part.createFormData("file", tmp.name, tmp.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                when (val r = safeCall { api.uploadAvatar(part) }) {
                    is ApiResult.Ok -> {
                        user = r.data
                        XiaodeApp.store.currentUserJson = Gson().toJson(r.data)
                        snackbar.showSnackbar("头像已更新")
                    }
                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                }
                tmp.delete()
            }
            uploading = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("编辑资料", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                XiaodeAvatar(user?.avatar, 96.dp)
                if (uploading) CircularProgressIndicator(Modifier.size(96.dp).padding(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text("点击头像更换", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = nickname, onValueChange = { nickname = it },
                label = { Text("昵称") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = bio, onValueChange = { if (it.length <= 80) bio = it },
                label = { Text("个性标签（最多80字）") },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
                supportingText = { Text("${bio.length}/80", fontSize = 11.sp) },
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    scope.launch {
                        when (val r = safeCall { api.updateProfile(ProfileUpdateIn(nickname.trim(), bio.trim())) }) {
                            is ApiResult.Ok -> {
                                XiaodeApp.store.currentUserJson = Gson().toJson(r.data)
                                snackbar.showSnackbar("已保存")
                            }
                            is ApiResult.Err -> snackbar.showSnackbar(r.message)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("保存") }
        }
    }
}

/** 切换账号 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(nav: NavController) {
    var accounts by remember { mutableStateOf<List<UserOut>>(emptyList()) }
    var currentId by remember { mutableIntStateOf(-1) }
    var pending by remember { mutableStateOf<UserOut?>(null) }

    fun reload() {
        val json = XiaodeApp.store.accountListJson
        accounts = if (json.isNullOrBlank()) emptyList() else
            runCatching {
                val arr = Gson().fromJson(json, com.google.gson.JsonArray::class.java)
                arr.mapNotNull { el ->
                    val o = el.asJsonObject
                    runCatching { Gson().fromJson(o.get("user"), UserOut::class.java) }.getOrNull()
                }
            }.getOrDefault(emptyList())
        val cur = XiaodeApp.store.currentUserJson
        currentId = cur?.let { runCatching { Gson().fromJson(it, UserOut::class.java).id }.getOrDefault(-1) } ?: -1
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("切换账号", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Spacer(Modifier.height(8.dp))
            if (accounts.isEmpty()) {
                EmptyBox("暂无已保存的其他账号")
            } else {
                accounts.forEach { acc ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
                            .clickable { if (acc.id != currentId) pending = acc },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            XiaodeAvatar(acc.avatar, 44.dp, isVip = acc.isVip)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(acc.nickname.ifBlank { acc.username }, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("@" + acc.username, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (acc.id == currentId) Icon(Icons.Filled.Check, null, tint = Color(0xFF4CAF50))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    // 清 token 后由 loginState 驱动导航图自动切到登录页，
                    // 不要再手动 navigate("login")，否则会因目标不在当前图中而崩溃
                    XiaodeApp.store.clear()
                    XiaodeApp.syncLoginState()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text("登录新账号") }
        }
    }

    pending?.let { acc ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("切换账号") },
            text = { Text("确定切换到 @" + acc.username + " 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    val json = XiaodeApp.store.accountListJson
                    val tok = runCatching {
                        Gson().fromJson(json, com.google.gson.JsonArray::class.java)
                            .firstOrNull { it.asJsonObject.get("user")?.asJsonObject?.get("id")?.asInt == acc.id }
                            ?.asJsonObject?.get("token")?.asString
                    }.getOrNull()
                    if (!tok.isNullOrBlank()) {
                        XiaodeApp.store.token = tok
                        XiaodeApp.store.currentUserJson = Gson().toJson(acc)
                        pending = null
                        XiaodeApp.syncLoginState()
                        nav.navigate("square") { popUpTo(0) }
                    }
                }) { Text("切换") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } },
        )
    }
}
