package com.xiaode.community.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.launch

/** 金币账户：余额 + 收入/支出明细 */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun AccountScreen(nav: NavController) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<AccountInfo?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        when (val r = safeCall { api.tipAccount() }) {
            is ApiResult.Ok -> info = r.data
            is ApiResult.Err -> {}
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的账户", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background)) {
            // 余额卡片
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("金币余额", color = Color.White.copy(0.9f), fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${info?.balance ?: 0}",
                        color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "1 元 = ${info?.coinPerYuan ?: 10} 金币",
                        color = Color.White.copy(0.85f), fontSize = 12.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("总收入", color = Color.White.copy(0.8f), fontSize = 12.sp)
                            Text("+${info?.totalIn ?: 0}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("总支出", color = Color.White.copy(0.8f), fontSize = 12.sp)
                            Text("-${info?.totalOut ?: 0}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Text(
                "收支明细",
                fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
                }
            } else if ((info?.logs ?: emptyList()).isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("暂无收支记录", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(info?.logs ?: emptyList()) { log ->
                        BalanceRow(log)
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(log: BalanceLogItem) {
    val income = log.delta > 0
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    kindLabel(log.kind),
                    fontWeight = FontWeight.Medium, fontSize = 14.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    log.detail.ifBlank { "-" },
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    log.createdAt.replace("T", " ").take(16),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (income) "+" else "") + log.delta,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (income) Color(0xFF1E8E3E) else Color(0xFFB3261E)
                )
                Text(
                    "余额 ${log.balanceAfter}",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "tip_in" -> "收到打赏"
    "tip_out" -> "打赏支出"
    "redeem" -> "卡密兑换"
    "vip_cost" -> "会员消费"
    "recharge" -> "充值"
    else -> kind.ifBlank { "变动" }
}