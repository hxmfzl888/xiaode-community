package com.xiaode.community.ui.appstore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import coil.compose.AsyncImage
import com.xiaode.community.XiaodeApp
import com.xiaode.community.ui.common.pageFadeIn
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.XiaodeAvatar
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.buffer
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/** 上传应用页 */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun AppUploadScreen(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ctx = XiaodeApp.context

    var name by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var iconUrl by remember { mutableStateOf("") }
    var shots by remember { mutableStateOf<List<String>>(emptyList()) }
    var uploadType by remember { mutableStateOf("external") }   // external / direct
    var extLink by remember { mutableStateOf("") }
    var apkUrl by remember { mutableStateOf("") }
    var isPaid by remember { mutableStateOf(false) }
    var price by remember { mutableStateOf("") }
    var uploadProgress by remember { mutableStateOf(0f) }
    var uploadText by remember { mutableStateOf("") }
    var apkSize by remember { mutableStateOf(0L) }
    var isVip by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        (safeCall { api.vipMe() }).let { if (it is ApiResult.Ok) isVip = it.data.isVip }
    }

    // 通用：选图 -> 上传 -> 拿 url
    fun pickAndUpload(isIcon: Boolean, onUrl: (String) -> Unit) {
        // 用 rememberLauncher 不能放这里，用下面 launcher
    }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uriRes ->
        val uri = uriRes ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val tmp = File(ctx.cacheDir, "ic_${System.currentTimeMillis()}.jpg").apply { writeBytes(bytes) }
            val part = MultipartBody.Part.createFormData("file", tmp.name, tmp.asRequestBody("image/jpeg".toMediaTypeOrNull()))
            when (val r = safeCall { api.uploadAppIcon(part) }) {
                is ApiResult.Ok -> iconUrl = r.data.url
                is ApiResult.Err -> snackbar.showSnackbar(r.message)
            }
            tmp.delete()
        }
    }
    val shotPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uriRes ->
        val uri = uriRes ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val tmp = File(ctx.cacheDir, "sh_${System.currentTimeMillis()}.jpg").apply { writeBytes(bytes) }
            val part = MultipartBody.Part.createFormData("file", tmp.name, tmp.asRequestBody("image/jpeg".toMediaTypeOrNull()))
            when (val r = safeCall { api.uploadAppShot(part) }) {
                is ApiResult.Ok -> shots = shots + r.data.url
                is ApiResult.Err -> snackbar.showSnackbar(r.message)
            }
            tmp.delete()
        }
    }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uriRes ->
        val uri = uriRes ?: return@rememberLauncherForActivityResult
        scope.launch {
            submitting = true
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) { submitting = false; return@launch }
            val tmp = File(ctx.cacheDir, "apk_${System.currentTimeMillis()}.apk").apply { writeBytes(bytes) }
            uploadProgress = 0f; uploadText = "上传中…"
            val rawBody = tmp.asRequestBody("application/vnd.android.package-archive".toMediaTypeOrNull())
            val progBody = ProgressRequestBody(rawBody) { sent, total ->
                if (total > 0) uploadProgress = sent.toFloat() / total
                uploadText = "%.1f / %.1f MB".format(sent / 1048576f, total / 1048576f)
            }
            val part = MultipartBody.Part.createFormData("file", tmp.name, progBody)
            when (val r = safeCall { api.uploadAppApk(part) }) {
                is ApiResult.Ok -> { apkUrl = r.data.url; apkSize = bytes.size.toLong(); uploadProgress = 1f; uploadText = "上传完成" }
                is ApiResult.Err -> snackbar.showSnackbar(r.message)
            }
            tmp.delete()
            submitting = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("上传应用", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(14.dp)) {
            Field("应用名称 *", name) { name = it }
            Field("包名（可选）", pkg) { pkg = it }
            Field("版本号（如 1.0.0）", version) { version = it }
            Field("分类（如 工具/游戏）", category) { category = it }
            Field("一句话简介", summary) { summary = it }
            Field("详细描述", description, maxLines = 5) { description = it }

            Spacer(Modifier.height(8.dp))
            Text("应用图标", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconUrl.isNotBlank()) {
                    AsyncImage(model = ApiClient.absUrl(iconUrl), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)))
                    Spacer(Modifier.width(12.dp))
                }
                OutlinedButton(onClick = { iconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (iconUrl.isBlank()) "选择图标" else "重新选择")
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("应用截图（可多张）", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row {
                shots.forEach { s ->
                    Box {
                        AsyncImage(model = ApiClient.absUrl(s), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.padding(end = 8.dp).size(72.dp).clip(RoundedCornerShape(10.dp)))
                        Box(Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(0.5f))
                            .clickable { shots = shots - s }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { shotPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, "添加", tint = MaterialTheme.colorScheme.primary) }
            }

            Spacer(Modifier.height(16.dp))
            Text("下载方式", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = uploadType == "external", onClick = { uploadType = "external" })
                Text("外链上传", fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                RadioButton(selected = uploadType == "direct", onClick = { uploadType = "direct" }, enabled = isVip)
                Text("直传服务器", fontSize = 14.sp, color = if (isVip) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
                if (!isVip) Text("（需大会员）", fontSize = 11.sp, color = Color(0xFFFF9800))
            }
            if (uploadType == "external") {
                Field("外链下载地址 *", extLink) { extLink = it }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { apkPicker.launch("*/*") }, enabled = !submitting) {
                        Text(if (apkUrl.isBlank()) "选择安装包" else "重新选择")
                    }
                    Spacer(Modifier.width(10.dp))
                    if (apkUrl.isNotBlank()) Text("已上传 ${apkSize / 1024 / 1024} MB", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    if (uploadProgress > 0f || uploadText.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { uploadProgress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(uploadText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("收费下载", fontSize = 14.sp)
                Spacer(Modifier.width(10.dp))
                Switch(checked = isPaid, onCheckedChange = { isPaid = it })
                Spacer(Modifier.width(6.dp))
                Text(if (isPaid) "每次下载扣金币" else "免费下载", fontSize = 12.sp, color = Color(0xFF8A8FA3))
            }
            if (isPaid) {
                Spacer(Modifier.height(8.dp))
                Field("价格（金币/次）*", price) { price = it.filter { c -> c.isDigit() } }
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    scope.launch {
                        submitting = true
                        val body = AppCreateIn(
                            name = name.trim(), packageName = pkg.trim(), version = version.trim(),
                            summary = summary.trim(), description = description.trim(), icon = iconUrl,
                            screenshots = shots, category = category.trim(), uploadType = uploadType,
                            extLink = extLink.trim(), apkUrl = apkUrl, apkSize = apkSize,
                            isPaid = isPaid, price = price.toIntOrNull() ?: 0,
                        )
                        when (val r = safeCall { api.createApp(body) }) {
                            is ApiResult.Ok -> { snackbar.showSnackbar("提交成功，等待管理员审核"); nav.popBackStack() }
                            is ApiResult.Err -> snackbar.showSnackbar(r.message)
                        }
                        submitting = false
                    }
                },
                enabled = !submitting && name.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) { Text(if (submitting) "提交中..." else "提交审核") }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, maxLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        maxLines = maxLines, singleLine = maxLines == 1,
    )
}

/** 带进度的 RequestBody 包装 */
private class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (sent: Long, total: Long) -> Unit,
) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun writeTo(sink: okio.BufferedSink) {
        val total = delegate.contentLength()
        val forwarding = object : okio.ForwardingSink(sink) {
            var written = 0L
            override fun write(source: okio.Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                onProgress(written, total)
            }
        }
        val buffered = forwarding.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}
