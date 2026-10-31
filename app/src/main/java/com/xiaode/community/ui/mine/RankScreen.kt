package com.xiaode.community.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.ApiResult
import com.xiaode.community.data.RankItemOut
import com.xiaode.community.data.RankOut
import com.xiaode.community.data.Repo
import com.xiaode.community.data.safeCall
import com.xiaode.community.ui.common.EmptyBox
import com.xiaode.community.ui.common.LevelBadge
import com.xiaode.community.ui.common.LoadingBox
import com.xiaode.community.ui.common.VipBadge
import com.xiaode.community.ui.common.XiaodeAvatar
import com.xiaode.community.ui.common.clickableNoRipple
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * v2.1 排行榜。
 * - 三套榜：签到榜 / 发帖榜 / 富豪榜
 * - 三个周期：总榜 / 日榜 / 周榜
 */
data class RankUiState(
    val loading: Boolean = false,
    val type: String = "sign",   // sign | post | rich
    val range: String = "all",   // all | day | week
    val data: RankOut? = null,
    val error: String? = null,
)

class RankViewModel : ViewModel() {
    private val _ui = MutableStateFlow(RankUiState())
    val ui = _ui.asStateFlow()

    fun load(type: String = _ui.value.type, range: String = _ui.value.range) {
        _ui.value = _ui.value.copy(type = type, range = range, loading = true, error = null)
        viewModelScope.launch {
            when (val r = safeCall { Repo.api.taskRank(type, range, 50) }) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(loading = false, data = r.data)
                is ApiResult.Err -> _ui.value = _ui.value.copy(loading = false, error = r.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun RankScreen(nav: NavController) {
    val vm: RankViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("排行榜", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // 榜单类型：签到 / 发帖
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SegChip("签到榜", ui.type == "sign") { vm.load(type = "sign") }
                SegChip("发帖榜", ui.type == "post") { vm.load(type = "post") }
                SegChip("富豪榜", ui.type == "rich") { vm.load(type = "rich") }
            }
            // 周期：总榜 / 日榜 / 周榜
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SegChip("总榜", ui.range == "all") { vm.load(range = "all") }
                SegChip("日榜", ui.range == "day") { vm.load(range = "day") }
                SegChip("周榜", ui.range == "week") { vm.load(range = "week") }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val data = ui.data
            when {
                ui.loading && data == null -> LoadingBox()
                data == null -> EmptyBox(ui.error ?: "暂无数据")
                data.items.isEmpty() -> EmptyBox("暂时还没有人上榜～")
                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        // 我的排名（若在榜外也提示）
                        item(key = "me") {
                            MyRankBar(data, isRich = ui.type == "rich")
                        }
                        itemsIndexed(data.items, key = { _, it -> "rk_" + it.userId }) { _, it ->
                            RankRow(it, isRich = ui.type == "rich") { nav.navigate("user/" + it.userId) }
                        }
                        item { Spacer(Modifier.height(30.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegChip(text: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) BrandBlue else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.clip(RoundedCornerShape(18.dp)).background(bg)
            .clickableNoRipple(onClick).padding(horizontal = 16.dp, vertical = 7.dp),
    ) { Text(text, fontSize = 13.sp, color = fg, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) }
}

/** 我的排名条 */
@Composable
private fun MyRankBar(data: RankOut, isRich: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("我的排名", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            val label = if (data.myRank > 0) "第 ${data.myRank} 名" else "未上榜"
            Text(
                if (isRich) "$label　·　${fmtScore(data.myScore, true)} 金币"
                else "$label　·　${data.myScore.toInt()} 次",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * STARFREE_V3_FIX: 安全格式化分数
 * 原代码对 Int 使用 "%.2f" 会抛 IllegalFormatConversionException 导致崩溃
 */
private fun fmtScore(v: Any?, decimals: Boolean): String {
    if (v == null) return "0"
    val d: Double = try {
        when (v) {
            is Double -> v
            is Float -> v.toDouble()
            is Long -> v.toDouble()
            is Int -> v.toDouble()
            is Number -> v.toDouble()
            else -> v.toString().toDoubleOrNull() ?: 0.0
        }
    } catch (e: Throwable) {
        0.0
    }
    if (d.isNaN() || d.isInfinite()) return "0"
    if (!decimals) return d.toLong().toString()
    return try {
        if (d == d.toLong().toDouble()) d.toLong().toString()
        else String.format(java.util.Locale.US, "%.2f", d)
    } catch (e: Throwable) {
        d.toLong().toString()
    }
}

@Composable
private fun RankRow(it: RankItemOut, isRich: Boolean = false, onClick: () -> Unit) {
    // 前三名特殊色
    val medal = when (it.rank) {
        1 -> Color(0xFFFFC107)
        2 -> Color(0xFFB0BEC5)
        3 -> Color(0xFFD7A06B)
        else -> Color.Transparent
    }
    Row(
        Modifier.fillMaxWidth().clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 名次
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            if (medal != Color.Transparent) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(medal), contentAlignment = Alignment.Center) {
                    Text("${it.rank}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                Text("${it.rank}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        XiaodeAvatar(it.avatar, 42.dp, it.levelEffect, it.isVip)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    it.nickname.ifBlank { it.username },
                    fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (it.isMe) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(BrandBlue).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("我", fontSize = 10.sp, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(it.level, it.levelEffect)
                if (it.isVip) { Spacer(Modifier.width(4.dp)); VipBadge() }
            }
        }
        Text(
            // STARFREE_V3_FIX: score 为 Int，需先转 Double 再格式化，避免 IllegalFormatConversionException
            fmtScore(it.score, isRich),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
