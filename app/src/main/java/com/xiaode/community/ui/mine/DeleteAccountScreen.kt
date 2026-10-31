package com.xiaode.community.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
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

/** 注销账号（危险操作，二次确认 + 密码校验） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var agreed by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    val errColor = Color(0xFFD32F2F)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("注销账号", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = errColor, titleContentColor = Color.White),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = errColor)
                        Spacer(Modifier.width(8.dp))
                        Text("危险操作提醒", color = errColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "注销账号后：\n" +
                            "• 你的帖子、评论、日记、点赞将被永久删除\n" +
                            "• 好友关系、消息记录、任务与等级数据不可恢复\n" +
                            "• 账号无法找回，请谨慎操作",
                        fontSize = 13.sp, color = Color(0xFFB71C1C), lineHeight = 21.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("请输入登录密码以确认身份", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pwd, onValueChange = { pwd = it },
                label = { Text("登录密码") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                Text("我已阅读并理解上述后果，自愿注销账号", fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { confirm = true },
                enabled = agreed && pwd.isNotBlank() && !working,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = errColor),
            ) { Text("确认注销账号") }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("最后确认") },
            text = { Text("真的要注销账号吗？此操作不可撤销，数据将永久删除。") },
            confirmButton = {
                TextButton(
                    enabled = !working,
                    onClick = {
                        scope.launch {
                            working = true
                            when (val r = safeCall { api.deleteAccount(DeleteAccountIn(pwd)) }) {
                                is ApiResult.Ok -> {
                                    confirm = false
                                    XiaodeApp.store.clear(); XiaodeApp.syncLoginState()
                                }
                                is ApiResult.Err -> {
                                    confirm = false
                                    working = false
                                    snackbar.showSnackbar(r.message)
                                }
                            }
                        }
                    },
                ) { Text(if (working) "注销中..." else "确认注销", color = errColor) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        )
    }
}