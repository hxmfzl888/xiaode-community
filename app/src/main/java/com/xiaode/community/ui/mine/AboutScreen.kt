package com.xiaode.community.ui.mine

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xiaode.community.BuildConfig
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.launch

/** 关于与更新 */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun AboutScreen(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<VersionInfo?>(null) }
    var checking by remember { mutableStateOf(false) }

    suspend fun check() {
        checking = true
        when (val r = safeCall { api.appVersion() }) {
            is ApiResult.Ok -> info = r.data
            is ApiResult.Err -> snackbar.showSnackbar(r.message)
        }
        checking = false
    }
    LaunchedEffect(Unit) { check() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("关于与更新", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(14.dp)) {
            Text("小德社区", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("当前版本 v${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            val latest = info?.latestVersion ?: "-"
            val hasNew = isNewer(latest, BuildConfig.VERSION_NAME)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (hasNew) "发现新版本 v$latest" else "已是最新版本", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        color = if (hasNew) Color(0xFFF44336) else Color(0xFF4CAF50))
                    val cl = info?.changelog
                    if (!cl.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("更新内容", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(cl, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Button(
                            onClick = { scope.launch { check() } },
                            enabled = !checking,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A86A1)),
                        ) { Text(if (checking) "检查中..." else "检查更新") }
                        Spacer(Modifier.width(10.dp))
                        val dl = info?.downloadUrl
                        if (!dl.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    catch (e: Exception) { }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            ) { Text(if (hasNew) "立即更新" else "下载页") }
                        }
                    }
                }
            }
            // TERMS_ENTRY_V1: 用户规范与免责声明入口
            Spacer(Modifier.height(18.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 2.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://api.delai.fun/terms"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                } catch (e: Exception) { }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Description, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(13.dp))
                        Text(
                            "用户使用规范与免责声明",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Filled.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(19.dp),
                        )
                    }

                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                        modifier = Modifier.padding(start = 48.dp, end = 16.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://api.delai.fun/web"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                } catch (e: Exception) { }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Public, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(13.dp))
                        Text(
                            "网页版社区",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Filled.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "小德社区由个人开发者独立运营",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(30.dp))
        }
    }
}

/** 版本号比较：a > b 返回 true */
fun isNewer(a: String, b: String): Boolean {
    val pa = a.split(".").mapNotNull { it.toIntOrNull() }
    val pb = b.split(".").mapNotNull { it.toIntOrNull() }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** v9.0 强制更新弹窗：全屏遮罩，阻止继续使用，必须更新 */
@Composable
fun ForceUpdateDialog(info: VersionInfo?, currentVersion: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { /* 不可取消 */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false,
        ),
    ) {
        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.padding(22.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.ArrowBack, null,
                    tint = Color(0xFFF44336),
                    modifier = androidx.compose.ui.Modifier.size(0.dp),
                )
                Text("需要更新", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFF44336))
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(10.dp))
                Text(
                    "当前版本 v$currentVersion 已停止服务，请更新到 v${info?.latestVersion ?: ""} 后继续使用。",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val cl = info?.changelog
                if (!cl.isNullOrBlank()) {
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(10.dp))
                    Text("更新内容", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(4.dp))
                    Text(cl, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(18.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        val url = info?.downloadUrl
                        if (!url.isNullOrBlank()) {
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            catch (e: Exception) { }
                        }
                    },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth().height(46.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Text("立即更新") }
            }
        }
    }
}
