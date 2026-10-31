package com.xiaode.community.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xiaode.community.data.ApiClient
import com.xiaode.community.data.ApiResult
import com.xiaode.community.data.Repo
import com.xiaode.community.data.TipSummary
import com.xiaode.community.data.safeCall
import kotlinx.coroutines.launch

private val GOLD = Color(0xFFFFB300)
private val GOLD_DARK = Color(0xFF8D6E00)
private val PRESET = listOf(1, 2, 5, 10, 50)

/**
 * 帖子打赏区。
 * 点整块 → 弹出打赏面板（金额 1/2/5/10/50 + 自定义）。
 */
@Composable
fun TipSection(
    momentId: Int,
    isMine: Boolean = false,
    onTipSuccess: (Int) -> Unit = {},
) {
    val api = Repo.api
    val scope = rememberCoroutineScope()
    var summary by remember(momentId) { mutableStateOf<TipSummary?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(0) }
    var custom by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }

    suspend fun load() {
        val r = safeCall { api.tipSummary(momentId) }
        if (r is ApiResult.Ok) summary = r.data
    }
    LaunchedEffect(momentId) { load() }

    val s = summary
    val count = s?.count ?: 0
    val total = s?.total ?: 0
    val top = s?.top ?: emptyList()

    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GOLD.copy(alpha = 0.08f))
            .border(1.dp, GOLD.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .clickable {
                if (isMine) toast = "\u4e0d\u80fd\u7ed9\u81ea\u5df1\u6253\u8d4f"
                else { picking = 0; custom = ""; showSheet = true }
            }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\u8d4f", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GOLD_DARK)
            Spacer(Modifier.width(10.dp))

            if (top.isNotEmpty()) {
                Row {
                    top.forEachIndexed { i, u ->
                        Box(
                            Modifier.size(if (i == 0) 30.dp else 24.dp)
                                .offset(x = (-i * 6).dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(if (i == 0) 2.dp else 1.dp, GOLD, CircleShape)
                        ) {
                            if (u.avatar.isNotBlank()) {
                                AsyncImage(
                                    model = ApiClient.absUrl(u.avatar),
                                    contentDescription = u.nickname,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            Text(
                if (count > 0) "$count \u4eba\u6253\u8d4f \u00b7 \u5171 $total \u91d1\u5e01" else "\u8fd8\u6ca1\u6709\u4eba\u6253\u8d4f\uff0c\u70b9\u6b64\u6253\u8d4f",
                fontSize = 12.5.sp,
                color = GOLD_DARK,
            )
            Spacer(Modifier.weight(1f))
            if (!isMine) {
                Text("\u6253\u8d4f \u203a", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = GOLD_DARK)
            }
        }
        if (toast.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(toast, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showSheet) {
        AlertDialog(
            onDismissRequest = { if (!busy) showSheet = false },
            title = { Text("\u6253\u8d4f\u91d1\u5e01", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "\u9009\u62e9\u91d1\u989d\uff08\u91d1\u5e01\uff09",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PRESET.forEach { a ->
                            val on = picking == a
                            Box(
                                Modifier.weight(1f).height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (on) GOLD else GOLD.copy(alpha = 0.12f))
                                    .border(
                                        1.dp,
                                        if (on) GOLD else GOLD.copy(alpha = 0.35f),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { picking = a; custom = a.toString() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$a",
                                    fontSize = 14.sp,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                    color = if (on) Color.White else GOLD_DARK,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { v ->
                            val t = v.filter { it.isDigit() }.take(6)
                            custom = t
                            picking = t.toIntOrNull() ?: 0
                        },
                        label = { Text("\u81ea\u5b9a\u4e49\u91d1\u989d") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && picking > 0,
                    onClick = {
                        val amt = picking
                        if (amt <= 0) return@Button
                        scope.launch {
                            busy = true
                            when (val r = safeCall { api.giveTip(momentId, mapOf("amount" to amt)) }) {
                                is ApiResult.Ok -> {
                                    toast = "\u6253\u8d4f\u6210\u529f\uff0c\u9001\u51fa $amt \u91d1\u5e01"
                                    showSheet = false
                                    picking = 0
                                    custom = ""
                                    load()
                                    onTipSuccess(r.data.balance.toInt())
                                }
                                is ApiResult.Err -> toast = r.message
                            }
                            busy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GOLD, contentColor = Color.White),
                ) { Text(if (busy) "\u63d0\u4ea4\u4e2d\u2026" else "\u786e\u8ba4\u6253\u8d4f", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { showSheet = false }) { Text("\u53d6\u6d88") }
            },
        )
    }
}