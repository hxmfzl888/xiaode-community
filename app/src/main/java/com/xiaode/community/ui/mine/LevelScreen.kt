package com.xiaode.community.ui.mine

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.common.LevelBadge
import com.xiaode.community.ui.common.XiaodeAvatar
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.launch

/** 等级 / 经验 / 签到 / 任务中心 */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun LevelScreen(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var data by remember { mutableStateOf<TaskListOut?>(null) }
    var signing by remember { mutableStateOf(false) }

    suspend fun load() {
        when (val r = safeCall { api.tasks() }) {
            is ApiResult.Ok -> data = r.data
            is ApiResult.Err -> snackbar.showSnackbar(r.message)
        }
    }
    LaunchedEffect(Unit) { load() }

    val user = remember { XiaodeApp.store.currentUserJson?.let { runCatching { Gson().fromJson(it, UserOut::class.java) }.getOrNull() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("等级中心", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
            val d = data
            // ---- 等级卡片 ----
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        XiaodeAvatar(user?.avatar, 62.dp, d?.levelEffect ?: "normal", user?.isVip ?: false)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(user?.nickname?.ifBlank { user.username } ?: "我", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Spacer(Modifier.width(6.dp))
                                if (d != null) LevelBadge(d.level, d.levelEffect)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("经验值：${d?.exp ?: 0}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val cur = d?.curLevelExp ?: 0
                    val nxt = d?.nextExp ?: 0
                    val exp = d?.exp ?: 0
                    val frac = if (nxt > cur) ((exp - cur).toFloat() / (nxt - cur)).coerceIn(0f, 1f) else 1f
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Lv.${d?.level ?: 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(if (nxt > cur) "距下一级还需 ${(nxt - exp).coerceAtLeast(0)}" else "已满级", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0xFFE8ECF4),
                    )
                    Spacer(Modifier.height(12.dp))
                    val effName = when (d?.levelEffect) {
                        "aurora" -> "极光特效 ✨"
                        "gold" -> "金色特效 🌟"
                        "blue" -> "蓝色特效 💙"
                        else -> "普通样式"
                    }
                    Text("当前等级特效：$effName", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- 签到 ----
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("每日签到", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFB26A00))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (d?.signedToday == true) "今天已签到，明天再来～" else "签到可得经验，会员双倍哦！",
                        fontSize = 12.sp, color = Color(0xFFB26A00).copy(0.8f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                signing = true
                                when (val r = safeCall { api.sign() }) {
                                    is ApiResult.Ok -> {
                                        snackbar.showSnackbar(r.data.msg.ifBlank { "签到成功" })
                                        load()
                                    }
                                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                }
                                signing = false
                            }
                        },
                        enabled = !signing && d?.signedToday != true,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.White),
                    ) {
                        Text(if (d?.signedToday == true) "已签到" else if (signing) "签到中..." else "立即签到")
                    }
                }
            }

            // ---- 任务 ----
            Spacer(Modifier.height(6.dp))
            Text("每日任务", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 4.dp))
            val tasks = d?.tasks ?: emptyList()
            if (tasks.isEmpty()) {
                Text("暂无任务", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp))
            } else {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column {
                        tasks.forEachIndexed { i, t ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(BrandBlue.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center,
                                ) { Text("+${t.exp}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(t.desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val remain = if (t.limit > 0) (t.limit - t.done).coerceAtLeast(0) else 99
                                Text(
                                    if (remain <= 0) "已完成" else "剩余 $remain 次",
                                    fontSize = 12.sp,
                                    color = if (remain <= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (i < tasks.size - 1) HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}