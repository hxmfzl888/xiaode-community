package com.xiaode.community.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xiaode.community.CrashCatcher
import com.xiaode.community.XiaodeApp
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 诊断日志页：查看 / 导出 / 分享 / 清空 崩溃日志 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DiagLogScreen(nav: NavController) {
    val ctx = XiaodeApp.context
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var logText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    fun reload() {
        scope.launch {
            loading = true
            logText = withContext(Dispatchers.IO) { CrashCatcher.readLog(ctx) }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("诊断日志", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background),
        ) {
            // ---- 操作栏 ----
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (logText.isBlank()) "暂无崩溃日志，应用运行正常 ✅"
                        else "已捕获崩溃日志（${logText.length} 字符），可提交给开发者排查",
                        fontSize = 13.sp,
                        color = if (logText.isBlank()) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        runCatching { CrashCatcher.exportLog(ctx); true }.getOrDefault(false)
                                    }
                                    snackbar.showSnackbar(if (ok) "已导出到：Android/data/${ctx.packageName}/files/logs/" else "导出失败或暂无日志")
                                }
                            },
                            enabled = logText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Filled.Download, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导出", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { CrashCatcher.shareLog(ctx) },
                            enabled = logText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("分享", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                CrashCatcher.clearLog(ctx)
                                reload()
                                scope.launch { snackbar.showSnackbar("日志已清空") }
                            },
                            enabled = logText.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF44336)),
                        ) {
                            Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("清空", fontSize = 13.sp)
                        }
                    }
                }
            }

            // ---- 日志内容 ----
            Card(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    if (loading) {
                        CircularProgressIndicator(
                            Modifier.align(Alignment.Center),
                            color = Color.White,
                        )
                    } else if (logText.isBlank()) {
                        Text(
                            "（无日志）",
                            color = Color(0xFF888888),
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                logText,
                                color = Color(0xFFB8E986),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .horizontalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }

            Text(
                "提示：崩溃日志保存在应用私有目录，仅本机可见。把「分享」发给开发者即可定位闪退原因。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}