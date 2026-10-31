package com.xiaode.community.ui.square


import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File




/** 工具列表（嵌入广场的第三个板块） */
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun ToolsTab(nav: NavController) {
    val scope = rememberCoroutineScope()
    var tools by remember { mutableStateOf<List<ToolOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    suspend fun load() {
        loading = true
        when (val r = safeCall { Repo.api.tools() }) {
            is ApiResult.Ok -> tools = r.data
            is ApiResult.Err -> tools = emptyList()
        }
        loading = false
    }
    LaunchedEffect(Unit) { load() }


    Box(Modifier.fillMaxSize()) {
        when {
            loading -> LoadingBox()
            tools.isEmpty() -> EmptyBox("还没有工具，点右下角上传第一个吧～")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(tools, key = { _, it -> "tool_" + it.id }) { _, t ->
                    ToolRow(t) { nav.navigate("tool_use/" + t.id) }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
        SmallFloatingActionButton(
            onClick = { nav.navigate("tool_upload") },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = androidx.compose.foundation.shape.CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(4.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Filled.Add, "上传工具", modifier = Modifier.size(22.dp)) }
    }
}


@Composable
fun ToolRow(t: ToolOut, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (t.cover.isBlank()) Text(t.name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                else AsyncImage(
                    model = ApiClient.absUrl(t.cover), contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    if (t.status == "pending") {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF9800).copy(alpha = 0.14f)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                            Text("待审核", fontSize = 9.sp, color = Color(0xFFFF9800))
                        }
                    }
                }
                if (t.intro.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(t.intro, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(3.dp))
                Text(t.useCount.toString() + " 次使用 · @" + t.uploaderName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.OpenInNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}




/** 工具使用：WebView 内嵌，右上角跳浏览器 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolWebScreen(nav: NavController, toolId: Int) {
    val ctx = LocalContext.current
    var tool by remember { mutableStateOf<ToolOut?>(null) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    LaunchedEffect(toolId) {
        when (val r = safeCall { Repo.api.toolDetail(toolId) }) {
            is ApiResult.Ok -> tool = r.data
            is ApiResult.Err -> {}
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool?.name ?: "工具", fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                actions = {
                    IconButton(onClick = {
                        tool?.url?.let { u ->
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } catch (_: Exception) {}
                        }
                    }) { Icon(Icons.Filled.OpenInBrowser, "在浏览器打开", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn()) {
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            val u = tool?.url
            if (u.isNullOrBlank()) {
                LoadingBox()
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c ->
                        WebView(c).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) { super.onPageFinished(view, url) }
                            }
                            webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                            }
                            loadUrl(u)
                            webRef = this
                        }
                    },
                )
            }
        }
    }
}





/** 上传工具 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolUploadScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var intro by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }


    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val tmp = File(ctx.cacheDir, "tool_" + System.currentTimeMillis() + ".jpg")
                tmp.writeBytes(bytes)
                val part = MultipartBody.Part.createFormData("file", tmp.name, tmp.asRequestBody("image/jpeg".toMediaTypeOrNull()))
                when (val r = safeCall { Repo.api.uploadToolCover(part) }) {
                    is ApiResult.Ok -> cover = r.data.url
                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                }
                tmp.delete()
            } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "上传失败") }
        }
    }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("上传工具", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                actions = {
                    Button(
                        onClick = {
                            if (name.isBlank()) { scope.launch { snackbar.showSnackbar("请填写工具名称") }; return@Button }
                            if (!url.startsWith("http")) { scope.launch { snackbar.showSnackbar("网址需以 http 开头") }; return@Button }
                            submitting = true
                            scope.launch {
                                when (val r = safeCall { Repo.api.createTool(ToolCreateIn(name.trim(), cover, intro.trim(), url.trim())) }) {
                                    is ApiResult.Ok -> { snackbar.showSnackbar("提交成功，等待审核"); nav.popBackStack() }
                                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                }
                                submitting = false
                            }
                        },
                        enabled = !submitting,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandBlue),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    ) { Text(if (submitting) "提交中…" else "提交", fontWeight = FontWeight.Bold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cover.isBlank()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.AddPhotoAlternate, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Text("封面图", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        } else AsyncImage(
                            model = ApiClient.absUrl(cover), contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("工具名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("网站地址（如 https://xxx.com）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = intro, onValueChange = { intro = it }, label = { Text("简介") }, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("提交后需审核，通过后展示在「工具」板块", fontSize = 12.sp, color = Color(0xFF9A9FB0))
        }
    }
}

