package com.xiaode.community.ui.resource

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.pressScale
import kotlinx.coroutines.launch

/**
 * 上传资源页
 * 字段：名称 / 类别 / 简介 / 详细说明 / 封面 / 演示截图 / 下载链接或文件 / 是否付费 + 价格
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ResourceUploadScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var name by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf("") }
    val shots = remember { mutableStateListOf<String>() }
    var resType by remember { mutableStateOf("link") }
    var link by remember { mutableStateOf("") }
    var fileUrl by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf(0L) }
    var isPaid by remember { mutableStateOf(false) }
    var price by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf(false) }
    var cats by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        when (val r = safeCall { Repo.api.resCategories() }) {
            is ApiResult.Ok -> cats = r.data.items
            is ApiResult.Err -> {}
        }
    }

    // 封面选择
    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val r = safeCall { Repo.uploadResCover(ctx, uri) }) {
                is ApiResult.Ok -> cover = r.data
                is ApiResult.Err -> snackbar.showSnackbar(r.message)
            }
        }
    }

    // 截图选择
    val shotPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            uris.take(6 - shots.size).forEach { u ->
                when (val r = safeCall { Repo.uploadResShot(ctx, u) }) {
                    is ApiResult.Ok -> shots.add(r.data)
                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                }
            }
        }
    }

    val canSubmit by remember {
        derivedStateOf {
            !submitting && name.trim().length >= 2 &&
                (if (resType == "link") link.isNotBlank() else fileUrl.isNotBlank()) &&
                (!isPaid || price.toIntOrNull()?.let { it > 0 } == true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("上传资源", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            Icons.Filled.Close, "取消",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            submitting = true
                            scope.launch {
                                val body = ResourceCreateIn(
                                    name = name.trim(),
                                    summary = summary.trim(),
                                    description = description.trim(),
                                    category = category,
                                    cover = cover,
                                    screenshots = shots.toList(),
                                    resType = resType,
                                    downloadUrl = if (resType == "link") link.trim() else fileUrl.trim(),
                                    fileSize = fileSize,
                                    isPaid = isPaid,
                                    price = if (isPaid) (price.toIntOrNull() ?: 0) else 0,
                                )
                                when (val r = safeCall { Repo.api.createResource(body) }) {
                                    is ApiResult.Ok -> {
                                        snackbar.showSnackbar("提交成功，等待审核")
                                        nav.popBackStack()
                                    }
                                    is ApiResult.Err -> {
                                        snackbar.showSnackbar(r.message)
                                        submitting = false
                                    }
                                }
                            }
                        },
                        enabled = canSubmit,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                            disabledContentColor = Color.White.copy(alpha = 0.85f),
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                    ) {
                        Text("提交审核", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // ===== 名称 =====
            FormCard {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    placeholder = { Text("资源名称（必填，至少 2 个字）", fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            // ===== 类别 + 简介 =====
            FormCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("类别", fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Box {
                        Row(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable { catMenu = true }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                category.ifBlank { "选择类别" },
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                            cats.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c, fontSize = 13.5.sp) },
                                    onClick = { category = c; catMenu = false },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = summary,
                    onValueChange = { if (it.length <= 120) summary = it },
                    placeholder = { Text("一句话简介（选填）", fontSize = 13.5.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.5.sp),
                )
            }

            Spacer(Modifier.height(8.dp))

            // ===== 详细说明 =====
            FormCard {
                Text(
                    "详细说明",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("介绍资源内容、使用方法、注意事项…", fontSize = 13.5.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
                )
            }

            Spacer(Modifier.height(8.dp))

            // ===== 封面 + 截图 =====
            FormCard {
                Text(
                    "封面与演示截图",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 封面
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { coverPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cover.isBlank()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.AddPhotoAlternate, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "封面",
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            AsyncImage(
                                model = ApiClient.absUrl(cover),
                                contentDescription = "封面",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                    // 截图列表
                    shots.forEach { s ->
                        Box(Modifier.size(72.dp)) {
                            AsyncImage(
                                model = ApiClient.absUrl(s),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                            )
                            Box(
                                Modifier.align(Alignment.TopEnd).size(18.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { shots.remove(s) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Close, "移除",
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        }
                    }
                    if (shots.size < 6) {
                        Box(
                            Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { shotPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Add, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "截图",
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ===== 下载方式 =====
            FormCard {
                Text(
                    "资源获取方式",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChip("下载链接", resType == "link") { resType = "link" }
                    TypeChip("上传文件", resType == "direct") { resType = "direct" }
                }
                Spacer(Modifier.height(9.dp))
                if (resType == "link") {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        placeholder = { Text("https:// 网盘或直链地址（必填）", fontSize = 13.5.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.5.sp),
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                // 文件选择
                            }
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.UploadFile, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            if (fileUrl.isBlank()) "选择文件上传（需大会员，最大 200MB）"
                            else "${fileName}（${fileSize / 1024 / 1024}MB）",
                            fontSize = 13.sp,
                            color = if (fileUrl.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ===== 付费设置 =====
            FormCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "设为付费资源",
                            fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "开启后用户需支付金币才能下载",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isPaid,
                        onCheckedChange = { isPaid = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                if (isPaid) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = price,
                        onValueChange = { if (it.length <= 7) price = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("价格（金币），例如 20", fontSize = 13.5.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.5.sp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "提交后进入人工审核，通过后所有人可见",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(13.dp),
        content = content,
    )
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}