package com.xiaode.community.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun RedeemScreen(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<VipInfo?>(null) }
    var history by remember { mutableStateOf<List<RedeemRecord>>(emptyList()) }

    suspend fun load() {
        (safeCall { api.vipMe() }).let { if (it is ApiResult.Ok) info = it.data }
        (safeCall { api.redeemHistory() }).let { if (it is ApiResult.Ok) history = it.data }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("兑换中心", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(0.10f)), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Redeem, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("我的余额：${info?.balance ?: 0} 金币", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val vi = info
                        Text(if (vi != null && vi.isVip) "会员到期：${vi.vipExpire.take(10)}" else "尚未开通会员", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("输入卡密", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().trim() },
                label = { Text("卡密") },
                placeholder = { Text("例如 XD-XXXX-XXXX") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val c = code.trim()
                    if (c.isEmpty()) { scope.launch { snackbar.showSnackbar("请输入卡密") }; return@Button }
                    scope.launch {
                        busy = true
                        when (val r = safeCall { api.redeemCode(mapOf("code" to c)) }) {
                            is ApiResult.Ok -> {
                                snackbar.showSnackbar(r.data.detail.ifBlank { "兑换成功" })
                                code = ""
                                load()
                            }
                            is ApiResult.Err -> snackbar.showSnackbar(r.message)
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (busy) "兑换中…" else "立即兑换", fontWeight = FontWeight.Bold) }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    try {
                        val ctx = nav.context
                        val open = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://manfaka.com/shop/level")
                        )
                        ctx.startActivity(open)
                    } catch (e: Exception) {
                        scope.launch { snackbar.showSnackbar("无法打开购买页面，请手动访问 manfaka.com") }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBlue),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("购买卡密（1 元 = 10 金币）", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(6.dp))
            Text(
                "前往官方商城购买，买完回到这里输入卡密兑换",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Text("兑换记录", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            if (history.isEmpty()) {
                Text("暂无兑换记录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                history.forEach { r ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text(r.detail.ifBlank { r.kind }, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(r.code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r.createdAt.replace("T", " ").take(19), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var msgs by remember { mutableStateOf<List<DirectMsg>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        (safeCall { api.directMessages() }).let { if (it is ApiResult.Ok) msgs = it.data }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("消息中心", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
                msgs.isEmpty() -> Text("暂无消息", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    items(msgs, key = { it.id }) { m ->
                        var expanded by remember { mutableStateOf(false) }
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (m.isRead) MaterialTheme.colorScheme.surface else BrandBlue.copy(0.08f)
                            ),
                        ) {
                            Column(Modifier.padding(15.dp).clickable {
                                expanded = !expanded
                                if (!m.isRead) scope.launch {
                                    safeCall { api.readMessage(m.id) }
                                    msgs = msgs.map { if (it.id == m.id) it.copy(isRead = true) else it }
                                }
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!m.isRead) {
                                        Box(Modifier.size(8.dp).background(Color(0xFFF44336), androidx.compose.foundation.shape.CircleShape))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(m.title.ifBlank { "(无标题)" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(m.createdAt.replace("T", " ").take(16), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    m.content,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (expanded) 100 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (expanded) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("—— ${m.sender.ifBlank { "官方团队" }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
