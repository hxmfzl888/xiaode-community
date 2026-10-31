package com.xiaode.community.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.draw.clip
import com.xiaode.community.ui.theme.THEME_PRESETS
import com.xiaode.community.ui.theme.ThemeState
import androidx.compose.material.icons.filled.Check

@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
private fun Scaffold2(nav: NavController, title: String, content: @Composable (PaddingValues) -> Unit) {
    androidx.compose.material3.Scaffold(
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

/** 安全设置：改密码 / 改用户名 / 改邮箱 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var dlg by remember { mutableStateOf(0) }  // 0无 1密码 2用户名 3邮箱

    Scaffold2(nav, "安全设置") { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background)) {
            Spacer(Modifier.height(8.dp))
            SecurityRow("修改密码") { dlg = 1 }
            SecurityRow("修改用户名") { dlg = 2 }
            SecurityRow("换绑邮箱") { dlg = 3 }
        }
    }

    if (dlg == 1) {
        var oldP by remember { mutableStateOf("") }
        var newP by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dlg = 0 },
            title = { Text("修改密码") },
            text = { Column {
                OutlinedTextField(oldP, { oldP = it }, label = { Text("原密码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(newP, { newP = it }, label = { Text("新密码（至少6位）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(enabled = oldP.isNotBlank() && newP.length >= 6, onClick = {
                scope.launch {
                    when (val r = safeCall { api.changePassword(ChangePasswordIn(oldP, newP)) }) {
                        is ApiResult.Ok -> { snackbar.showSnackbar("密码已修改"); dlg = 0 }
                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                    }
                }
            }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { dlg = 0 }) { Text("取消") } },
        )
    }
    if (dlg == 2) {
        var newU by remember { mutableStateOf("") }
        var pwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dlg = 0 },
            title = { Text("修改用户名") },
            text = { Column {
                OutlinedTextField(newU, { newU = it }, label = { Text("新用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pwd, { pwd = it }, label = { Text("密码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(enabled = newU.isNotBlank() && pwd.isNotBlank(), onClick = {
                scope.launch {
                    when (val r = safeCall { api.changeUsername(ChangeUsernameIn(newU, pwd)) }) {
                        is ApiResult.Ok -> { snackbar.showSnackbar("用户名已修改"); dlg = 0 }
                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                    }
                }
            }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { dlg = 0 }) { Text("取消") } },
        )
    }
    if (dlg == 3) {
        var newE by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var cd by remember { mutableIntStateOf(0) }
        LaunchedEffect(cd) { if (cd > 0) { kotlinx.coroutines.delay(1000); cd -= 1 } }
        AlertDialog(
            onDismissRequest = { dlg = 0 },
            title = { Text("换绑邮箱") },
            text = { Column {
                OutlinedTextField(newE, { newE = it }, label = { Text("新邮箱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(code, { code = it }, label = { Text("验证码") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(enabled = cd == 0 && newE.isNotBlank(), onClick = {
                        scope.launch {
                            when (val r = safeCall { api.sendCode(SendCodeIn(newE.trim(), "bind")) }) {
                                is ApiResult.Ok -> { cd = 60; snackbar.showSnackbar("验证码已发送") }
                                is ApiResult.Err -> snackbar.showSnackbar(r.message)
                            }
                        }
                    }) { Text(if (cd > 0) "${cd}s" else "获取", fontSize = 12.sp) }
                }
            } },
            confirmButton = { TextButton(enabled = newE.isNotBlank() && code.isNotBlank(), onClick = {
                scope.launch {
                    when (val r = safeCall { api.changeEmail(ChangeEmailIn(newE.trim(), code.trim())) }) {
                        is ApiResult.Ok -> { snackbar.showSnackbar("邮箱已换绑"); dlg = 0 }
                        is ApiResult.Err -> snackbar.showSnackbar(r.message)
                    }
                }
            }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { dlg = 0 }) { Text("取消") } },
        )
    }
    // snackbar 宿（Simplified）
    LaunchedEffect(snackbar) { }
}

@Composable
private fun SecurityRow(title: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** 设置页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    var notifyOn by remember { mutableStateOf(true) }
    var autoCache by remember { mutableStateOf(XiaodeApp.store.imageCacheEnabled) }
    val curPreset = ThemeState.presetKey
    val curDark = ThemeState.darkMode
    var navAlpha by remember { mutableStateOf(ThemeState.navAlpha) }
    var navCap by remember { mutableStateOf(ThemeState.navCapsule) }
    Scaffold2(nav, "设置") { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("主题色调", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        THEME_PRESETS.take(4).forEach { preset ->
                            val selected = preset.key == curPreset
                            Box(
                                Modifier.weight(1f).height(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(preset.color)
                                    .clickable { ThemeState.applyTheme(preset.key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Filled.Check, null, tint = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        THEME_PRESETS.drop(4).forEach { preset ->
                            val selected = preset.key == curPreset
                            Box(
                                Modifier.weight(1f).height(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(preset.color)
                                    .clickable { ThemeState.applyTheme(preset.key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Filled.Check, null, tint = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(THEME_PRESETS.firstOrNull { it.key == curPreset }?.label ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("暗色模式", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val opts = listOf(0 to ("跟随系统"), 1 to ("浅色"), 2 to ("深色"))
                        opts.forEach { pair ->
                            FilterChip(
                                selected = curDark == pair.first,
                                onClick = { ThemeState.applyDark(pair.first) },
                                label = { Text(pair.second, fontSize = 13.sp) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("个性化", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("悬浮胶囊导航栏", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(checked = navCap, onCheckedChange = { navCap = it; ThemeState.applyNavCapsule(it) })
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("导航栏透明度", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("${(navAlpha * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = navAlpha,
                        onValueChange = { navAlpha = it; ThemeState.applyNavAlpha(it) },
                        valueRange = 0.35f..1f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("拖动滑块预览底部导航栏透明效果", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("消息通知", fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Switch(checked = notifyOn, onCheckedChange = { notifyOn = it })
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("聊天图片自动缓存", fontSize = 15.sp)
                            Text("关闭后可节省流量", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoCache, onCheckedChange = { autoCache = it; XiaodeApp.store.imageCacheEnabled = it })
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
