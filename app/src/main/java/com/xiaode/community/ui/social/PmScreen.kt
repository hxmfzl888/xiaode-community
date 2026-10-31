package com.xiaode.community.ui.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import kotlinx.coroutines.launch
import com.xiaode.community.XiaodeApp
import com.xiaode.community.ui.common.pageFadeIn
import com.xiaode.community.data.ApiResult
import com.xiaode.community.data.PrivateMsgIn
import com.xiaode.community.data.PrivateMsgOut
import com.xiaode.community.data.Repo
import com.xiaode.community.data.safeCall
import com.xiaode.community.ui.common.LoadingBox
import com.xiaode.community.ui.square.extractId
import com.xiaode.community.ui.theme.BrandBlue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun PmScreen(nav: NavController, peerId: Int, peerName: String) {
    var list by remember { mutableStateOf<List<PrivateMsgOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val myId = remember { XiaodeApp.store.currentUserJson?.let { extractId(it) } ?: 0 }
    LaunchedEffect(peerId) {
        loading = true
        when (val r = safeCall { Repo.api.pmHistory(peerId) }) {
            is ApiResult.Ok -> list = r.data
            is ApiResult.Err -> {}
        }
        loading = false
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(peerName.ifBlank { "私信" }, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().pageFadeIn().padding(pad)) {
            Box(Modifier.weight(1f)) {
                if (loading) LoadingBox()
                else LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(list) { m ->
                        val mine = m.senderId == myId
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Box(Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(12.dp)).background(if (mine) BrandBlue else Color(0xFFEDEFF3)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                                val ctxP = LocalContext.current
                                Text(
                                    m.content,
                                    color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { copyPmText(ctxP, m.content) }),
                                )
                            }
                        }
                    }
                }
            }
            // 底部输入栏
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    placeholder = { Text("发送消息…", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        val t = input.trim()
                        if (t.isNotEmpty() && !sending) {
                            sending = true
                            scope.launch {
                                when (val r = safeCall { Repo.api.sendPm(peerId, PrivateMsgIn(t)) }) {
                                    is ApiResult.Ok -> { input = ""; list = list + r.data }
                                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                }
                                sending = false
                            }
                        }
                    },
                    enabled = !sending,
                ) { Icon(Icons.AutoMirrored.Filled.Send, "发送") }
            }
        }
    }
}

/** 复制私信文字 */
internal fun copyPmText(ctx: android.content.Context, text: String) {
    try {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("xiaode", text))
        android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
    } catch (_: Throwable) {}
}
