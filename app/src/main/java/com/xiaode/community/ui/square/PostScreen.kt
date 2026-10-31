package com.xiaode.community.ui.square

import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.graphics.Typeface
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.CODE_LANGS
import com.xiaode.community.ui.common.RichEditField
import com.xiaode.community.ui.common.RichFormat
import com.xiaode.community.ui.common.clickableNoRipple
import com.xiaode.community.ui.common.langLabel
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import com.xiaode.community.ui.common.pageFadeIn

/** v2.1 固定标签（不可自定义） */
val POST_TAGS = listOf("教程", "求助", "分享", "讨论")

/** 颜色选择（VIP 专属） */
private val COLOR_CHOICES: List<Pair<String, Color>> = listOf(
    "红" to Color(0xFFF44336),
    "蓝" to Color(0xFF2196F3),
    "绿" to Color(0xFF4CAF50),
    "橙" to Color(0xFFFF9800),
    "紫" to Color(0xFF9C27B0),
    "粉" to Color(0xFFE91E63),
)

/** 发帖页状态 */
data class PostUiState(
    val isVip: Boolean = false,
    val level: Int = 1,
    val uploading: Boolean = false,
    val submitting: Boolean = false,
    val toast: String? = null,
)

class PostViewModel : ViewModel() {
    private val api = Repo.api
    private val _ui = MutableStateFlow(PostUiState())
    val ui = _ui.asStateFlow()
    private val ctx = XiaodeApp.context

    /** 图片上限：非VIP 3张，VIP 5张 */
    val maxImages get() = if (_ui.value.isVip) 5 else 3
    /** 视频上限：非VIP 0，VIP 2个 */
    val maxVideos get() = if (_ui.value.isVip) 2 else 0

    init {
        try {
            val js = com.xiaode.community.XiaodeApp.store.currentUserJson
            val lv = js?.let { Regex("\"level\"\\s*:\\s*(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 1
            _ui.value = _ui.value.copy(level = lv)
        } catch (_: Throwable) {}
        viewModelScope.launch {
            when (val r = safeCall { api.vipMe() }) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(isVip = r.data.isVip)
                is ApiResult.Err -> {}
            }
        }
    }

    /** 上传单个本地文件（图片或视频），返回远端 URL；失败返回 null */
    suspend fun upload(uri: Uri, isVideo: Boolean): String? {
        return try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val ext = if (isVideo) "mp4" else "jpg"
            val tmp = File(ctx.cacheDir, "up_${System.currentTimeMillis()}.$ext")
            tmp.writeBytes(bytes)
            val mime = if (isVideo) "video/mp4" else "image/jpeg"
            val part = MultipartBody.Part.createFormData(
                "file", tmp.name,
                tmp.asRequestBody(mime.toMediaTypeOrNull()),
            )
            val url = api.uploadImage(part).url
            tmp.delete()
            url
        } catch (e: Exception) { null }
    }

    fun submit(
        title: String,
        content: String,
        images: List<String>,
        videos: List<String>,
        channelId: Int,
        tags: List<String>,
        allowDownload: Boolean = false,
        lang: String = "",
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(submitting = true)
            try {
                val body = MomentCreateIn(
                    title = title, content = content, images = images, videos = videos,
                    channelId = channelId, tags = tags, lang = lang, allowDownload = allowDownload,
                )
                when (val r = safeCall { api.createMoment(body) }) {
                    is ApiResult.Ok -> {
                        _ui.value = _ui.value.copy(submitting = false, toast = "发布成功，等待审核后展示")
                        onDone()
                        return@launch
                    }
                    is ApiResult.Err -> {
                        _ui.value = _ui.value.copy(submitting = false, toast = r.message)
                        return@launch
                    }
                    else -> {}
                }
            } catch (e: Throwable) {
                _ui.value = _ui.value.copy(submitting = false, toast = (e.message ?: "发布失败"))
            } finally {
                // 无论如何都复位，避免按钮永久禁用
                if (_ui.value.submitting) _ui.value = _ui.value.copy(submitting = false)
            }
        }
    }

    fun consumeToast() { _ui.value = _ui.value.copy(toast = null) }
    fun resetSubmitting() { if (_ui.value.submitting) _ui.value = _ui.value.copy(submitting = false) }
}

/**
 * 全屏发帖页（v2.1）。
 * 富文本所见即所得：选中文字点 B/I/U/A 直接变样式，不再显示标记符号。
 * 新增：固定 4 标签多选（最多 3）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(nav: NavController, channelId: Int = 0) {
    val vm: PostViewModel = viewModel()
    // 进入页面时复位提交状态，避免上一次异常导致按钮永远点不动
    LaunchedEffect(Unit) { vm.resetSubmitting() }
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    // 富文本正文：EditText 是唯一真源，这里镜像一份用于提交
    var richText by remember { mutableStateOf<Spannable>(SpannableString("")) }
    var editor by remember { mutableStateOf<EditText?>(null) }
    var codeText by remember { mutableStateOf("") }

    var title by remember { mutableStateOf("") }
    val uploadedImages = remember { mutableStateListOf<String>() }
    val uploadedVideos = remember { mutableStateListOf<String>() }
    var allowDownload by remember { mutableStateOf(false) }
    val uploading = remember { mutableStateOf(false) }
    var selChannel by remember { mutableStateOf(channelId) }
    var chanMenu by remember { mutableStateOf(false) }
    var joinedChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    // 文字 / 代码
    var isCode by remember { mutableStateOf(false) }
    var codeLang by remember { mutableStateOf("js") }
    var langMenu by remember { mutableStateOf(false) }
    // 标签（最多 3）
    val selectedTags = remember { mutableStateListOf<String>() }
    // 颜色弹层
    var colorMenu by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val r = safeCall { Repo.api.joinedChannels() }) {
            is ApiResult.Ok -> joinedChannels = r.data
            is ApiResult.Err -> {}
        }
    }

    LaunchedEffect(ui.toast) {
        ui.toast?.let { snackbar.showSnackbar(it); vm.consumeToast() }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(vm.maxImages)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val canAdd = vm.maxImages - uploadedImages.size
        viewModelScopeUpload(vm, uris.take(canAdd), false, uploadedImages)
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModelScopeUpload(vm, listOf(uri), true, uploadedVideos)
    }

    // 提交用的最终正文：文字模式序列化为标记，代码模式原样
    // 提交时实时从编辑器读取最新内容（setSpan 不会触发 TextWatcher，richText 镜像会滞后）
    fun buildFinalContent(): String {
        if (isCode) return codeText
        val live = editor?.text
        return if (live is Spanned) RichFormat.toMarkup(live) else RichFormat.toMarkup(richText)
    }
    // 是否可提交：轻量判断，绝不在重组期调用 toMarkup
    val canSubmit by remember {
        derivedStateOf {
            when {
                ui.submitting -> false
                uploadedImages.isNotEmpty() || uploadedVideos.isNotEmpty() -> true
                isCode -> codeText.isNotBlank()
                else -> richText.isNotBlank() || (editor?.text?.isNotBlank() == true)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (selChannel > 0) "发到频道" else "发帖", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.Close, "取消", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val finalContent = buildFinalContent()
                            if (finalContent.isNotBlank() ||
                                uploadedImages.isNotEmpty() ||
                                uploadedVideos.isNotEmpty()
                            ) {
                                vm.submit(
                                    title.trim(), finalContent, uploadedImages, uploadedVideos,
                                    selChannel, selectedTags.toList(), allowDownload,
                                    if (isCode) codeLang else "",
                                ) { nav.popBackStack() }
                            }
                        },
                        enabled = canSubmit,
                        shape = RoundedCornerShape(50),
                        // STARFREE_POST: 主色实心胶囊按钮
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                            disabledContentColor = Color.White.copy(alpha = 0.85f),
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                    ) {
                        Text("发布", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                },
                // STARFREE_POST: 白色顶栏 + 主色文字（替代原紫色）
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)
                .pageFadeIn()
                .verticalScroll(rememberScrollState()),
        ) {
            // 发布位置：广场 or 某频道
            Row(
                Modifier
            .pageFadeIn().fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Public, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("发布到", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box {
                    // STARFREE_POST: 主色胶囊
                    Row(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable { chanMenu = true }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (selChannel == 0) "广场" else (joinedChannels.firstOrNull { it.id == selChannel }?.name ?: "频道"),
                            fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
                        )
                        Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    }
                    DropdownMenu(expanded = chanMenu, onDismissRequest = { chanMenu = false }) {
                        DropdownMenuItem(text = { Text("广场", fontSize = 14.sp) }, onClick = { selChannel = 0; chanMenu = false })
                        joinedChannels.forEach { ch ->
                            DropdownMenuItem(text = { Text("#${ch.name}", fontSize = 14.sp) }, onClick = { selChannel = ch.id; chanMenu = false })
                        }
                    }
                }
            }
            // 标题
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 50) title = it },
                placeholder = { Text("标题（至少3个字）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
            )
            // 标签选择（固定 4 个，最多选 3）
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.LocalOffer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                POST_TAGS.forEach { tag ->
                    val on = selectedTags.contains(tag)
                    FilterChip(
                        selected = on,
                        onClick = {
                            if (on) selectedTags.remove(tag)
                            else if (selectedTags.size >= 3) {
                                android.widget.Toast.makeText(ctx, "最多选择 3 个标签", android.widget.Toast.LENGTH_SHORT).show()
                            } else selectedTags.add(tag)
                        },
                        shape = RoundedCornerShape(50),
                        label = { Text(tag, fontSize = 12.sp) },
                    )
                }
            }
            // 文字 / 代码 切换 + 语言
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(selected = !isCode, onClick = { if (isCode) isCode = false }, label = { Text("文字", fontSize = 12.sp) })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = isCode, onClick = { if (!isCode) isCode = true }, label = { Text("代码", fontSize = 12.sp) })
                if (isCode) {
                    Spacer(Modifier.width(8.dp))
                    Box {
                        Row(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable { langMenu = true }.padding(horizontal = 11.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(langLabel(codeLang), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                            CODE_LANGS.forEach { (key, label) ->
                                DropdownMenuItem(text = { Text(label, fontSize = 13.sp) }, onClick = { codeLang = key; langMenu = false })
                            }
                        }
                    }
                }
            }
            // v2.1 富文本工具栏（所见即所得：直接改选区样式）
            if (!isCode) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FormatBtn("B", "加粗", FontWeight.Bold, true) { applyStyle(editor, STYLE_BOLD) }
                    FormatBtn("I", "斜体", FontWeight.Normal, true, italic = true) { applyStyle(editor, STYLE_ITALIC) }
                    FormatBtn("U", "下划线", FontWeight.Normal, ui.level >= 3, underline = true) { applyStyle(editor, STYLE_UNDERLINE) }
                    FormatBtn("A", "颜色", FontWeight.Bold, ui.isVip, color = COLOR_CHOICES[0].second, forceColor = true) { colorMenu = true }
                    FormatBtn("☺", "表情", FontWeight.Normal, ui.level >= 5 || ui.isVip) { showEmojiPanel = !showEmojiPanel }
                    FormatBtn("⌫", "清除格式", FontWeight.Normal, true) { clearStyle(editor) }
                    // 表情面板（OwO）
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showEmojiPanel,
                        enter = androidx.compose.animation.expandVertically(
                            animationSpec = androidx.compose.animation.core.tween(200)
                        ) + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically(
                            animationSpec = androidx.compose.animation.core.tween(160)
                        ) + androidx.compose.animation.fadeOut(),
                    ) {
                        com.xiaode.community.ui.common.EmojiPanel(
                            onPick = { e -> insertAtCursor(editor, e) },
                        )
                    }
                    // 颜色选择弹层锚点
                    Box {
                        Spacer(Modifier.size(0.dp))
                        DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                            COLOR_CHOICES.forEach { (name, c) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(c))
                                            Spacer(Modifier.width(8.dp))
                                            Text(name, fontSize = 13.sp)
                                        }
                                    },
                                    onClick = { applyColor(editor, c); colorMenu = false },
                                )
                            }
                            DropdownMenuItem(text = { Text("取消颜色", fontSize = 13.sp) }, onClick = { clearColor(editor); colorMenu = false })
                        }
                    }
                }
                // 门槛提示
                if (ui.level < 3 || !ui.isVip) {
                    Text(
                        buildString {
                            if (ui.level < 3) append("· 下划线需 Lv.3")
                            if (!ui.isVip) { if (isNotEmpty()) append("　"); append("· 颜色/表情需大会员") }
                        },
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // 正文区
            if (isCode) {
                OutlinedTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    placeholder = { Text("在此粘贴代码…") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                )
            } else {
                RichEditField(
                    initial = richText,
                    onValueChange = { richText = it },
                    onSelection = { _, _ -> },
                    editorHolder = { editor = it },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    hint = "这一刻的想法…",
                    textSizeSp = 16f,
                )
            }

            // 图片/视频预览
            if (uploadedImages.isNotEmpty() || uploadedVideos.isNotEmpty()) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(uploadedImages) { i, url -> MediaThumb(url, false) { uploadedImages.removeAt(i) } }
                    itemsIndexed(uploadedVideos) { i, url -> MediaThumb(url, true) { uploadedVideos.removeAt(i) } }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 工具栏
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                MediaButton(
                    icon = Icons.Filled.Image,
                    label = "图片 ${uploadedImages.size}/${vm.maxImages}",
                    enabled = uploadedImages.size < vm.maxImages,
                ) {
                    imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Spacer(Modifier.width(20.dp))
                MediaButton(
                    icon = Icons.Filled.Videocam,
                    label = if (vm.maxVideos == 0) "视频（会员）" else "视频 ${uploadedVideos.size}/${vm.maxVideos}",
                    enabled = vm.maxVideos > 0 && uploadedVideos.size < vm.maxVideos,
                ) {
                    if (vm.maxVideos == 0) return@MediaButton
                    videoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }
            }

            // 图片可下载开关
            if (uploadedImages.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.FileDownload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("允许他人下载图片", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = allowDownload, onCheckedChange = { allowDownload = it })
                }
            }

            if (uploading.value || ui.submitting) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (ui.submitting) "发布中…" else "上传中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    if (ui.isVip) "大会员：最多 5 张图片 + 2 个视频" else "普通用户：最多 3 张图片（开通大会员可发 5 图 + 2 视频）",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("发布后需管理员审核，通过后展示", fontSize = 12.sp, color = Color(0xFFFF9800))
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ---------- 富文本编辑操作（直接作用于原生 EditText 的选区，所见即所得） ----------

private const val STYLE_BOLD = 1
private const val STYLE_ITALIC = 2
private const val STYLE_UNDERLINE = 3

private fun toastHint(et: EditText?, msg: String) {
    val c = et?.context ?: return
    android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_SHORT).show()
}

/** 施加/移除 粗体/斜体/下划线 */
private fun applyStyle(et: EditText?, kind: Int) {
    if (et == null) return
    val sp = et.text as Spannable
    val r = effectiveRange(et, sp)
    if (r == null) { toastHint(et, "请先选中要设置的文字"); return }
    val lo = r.first; val hi = r.second
    when (kind) {
        STYLE_BOLD -> toggleSpan(sp, lo, hi, Typeface.BOLD)
        STYLE_ITALIC -> toggleSpan(sp, lo, hi, Typeface.ITALIC)
        STYLE_UNDERLINE -> toggleUnderline(sp, lo, hi)
    }
}

/** 若选区内已完整存在同一样式则移除，否则添加 */
private fun toggleSpan(sp: Spannable, lo: Int, hi: Int, style: Int) {
    if (lo >= hi || hi > sp.length) return
    val existing = sp.getSpans(lo, hi, StyleSpan::class.java).filter { it.style == style }
    val fullyCovered = existing.any { sp.getSpanStart(it) <= lo && sp.getSpanEnd(it) >= hi }
    if (fullyCovered) existing.forEach { sp.removeSpan(it) }
    else sp.setSpan(StyleSpan(style), lo, hi, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

private fun toggleUnderline(sp: Spannable, lo: Int, hi: Int) {
    if (lo >= hi || hi > sp.length) return
    val existing = sp.getSpans(lo, hi, UnderlineSpan::class.java)
    val fullyCovered = existing.any { sp.getSpanStart(it) <= lo && sp.getSpanEnd(it) >= hi }
    if (fullyCovered) existing.forEach { sp.removeSpan(it) }
    else sp.setSpan(UnderlineSpan(), lo, hi, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

/** 给选区上色 */
private fun applyColor(et: EditText?, color: Color) {
    if (et == null) return
    val sp = et.text as Spannable
    val r = effectiveRange(et, sp)
    if (r == null) { toastHint(et, "请先选中要设置的文字"); return }
    val lo = r.first; val hi = r.second
    sp.getSpans(lo, hi, ForegroundColorSpan::class.java).forEach { sp.removeSpan(it) }
    sp.setSpan(ForegroundColorSpan(color.toArgbInt()), lo, hi, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

/** 清除选区颜色 */
private fun clearColor(et: EditText?) {
    if (et == null) return
    val s = et.selectionStart; val e = et.selectionEnd
    if (s < 0 || e < 0) { toastHint(et, "请先选中要设置的文字"); return }
    val sp = et.text as Spannable
    val a = minOf(s, e); val b = maxOf(s, e)
    if (a >= b || b > sp.length) { toastHint(et, "请先选中要设置的文字"); return }
    sp.getSpans(a, b, ForegroundColorSpan::class.java).forEach { sp.removeSpan(it) }
}

/** 清除选区所有富文本样式 */
private fun clearStyle(et: EditText?) {
    if (et == null) return
    val s = et.selectionStart; val e = et.selectionEnd
    if (s < 0 || e < 0 || s == e) { toastHint(et, "请先选中要设置的文字"); return }
    val sp = et.text as Spannable
    val lo = minOf(s, e); val hi = maxOf(s, e)
    sp.getSpans(lo, hi, StyleSpan::class.java).forEach { sp.removeSpan(it) }
    sp.getSpans(lo, hi, UnderlineSpan::class.java).forEach { sp.removeSpan(it) }
    sp.getSpans(lo, hi, ForegroundColorSpan::class.java).forEach { sp.removeSpan(it) }
}

/** 在光标处插入文字（表情） */
private fun insertAtCursor(et: EditText?, add: String) {
    if (et == null) return
    val pos = et.selectionStart.coerceIn(0, et.text.length)
    val ed = et.text as android.text.Editable
    ed.insert(pos, add)
    et.setSelection(pos + add.length)
}

/** Compose Color → ARGB Int */
private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)

@Composable
private fun MediaThumb(url: String, isVideo: Boolean, onRemove: () -> Unit) {
    Box(Modifier.size(90.dp)) {
        if (isVideo) {
            Box(
                Modifier.size(90.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White) }
        } else {
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(90.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Filled.Cancel, null, tint = Color(0xFFF44336), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MediaButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.then(if (enabled) Modifier.clickableNoRipple(onClick) else Modifier),
    ) {
        Icon(
            icon, null,
            tint = if (enabled) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 协程上传多个文件 */
private fun viewModelScopeUpload(
    vm: PostViewModel,
    uris: List<Uri>,
    isVideo: Boolean,
    target: MutableList<String>,
) {
    kotlinx.coroutines.MainScope().launch {
        uris.forEach { uri ->
            val url = vm.upload(uri, isVideo)
            if (url != null) target.add(url)
        }
    }
}

/** 工具栏按钮（带解锁状态） */
@Composable
private fun FormatBtn(
    label: String,
    name: String,
    weight: FontWeight,
    unlocked: Boolean,
    italic: Boolean = false,
    underline: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    forceColor: Boolean = false,
    onClick: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Surface(
        color = if (unlocked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(34.dp).clickable {
            if (unlocked) onClick()
            else android.widget.Toast.makeText(ctx, "「" + name + "」需解锁后使用", android.widget.Toast.LENGTH_SHORT).show()
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontWeight = weight,
                fontSize = 15.sp,
                color = if (unlocked) color else color.copy(alpha = 0.35f),
                style = androidx.compose.ui.text.TextStyle(
                    textDecoration = if (underline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                    fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                ),
            )
        }
    }
}

/** 获取有效选区：优先 EditText 实时选区；失焦塌陷时回退到最后一次有效选区。 */
private fun effectiveRange(et: EditText?, sp: Spannable): Pair<Int, Int>? {
    if (et != null) {
        val a = minOf(et.selectionStart, et.selectionEnd)
        val b = maxOf(et.selectionStart, et.selectionEnd)
        if (b > a && b <= sp.length) return a to b
    }
    val cached = com.xiaode.community.ui.common.LastSelection.range()
    if (cached != null && cached.second <= sp.length) return cached
    return null
}
