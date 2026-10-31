package com.xiaode.community.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SupportAgent
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
import com.xiaode.community.data.*
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue

/** 小德答疑：预设问答（置顶优先），点击展开答案 */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun SupportScreen(nav: NavController) {
    val api = Repo.api
    var list by remember { mutableStateOf<List<FaqOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        when (val r = safeCall { api.faqs() }) {
            is ApiResult.Ok -> list = r.data
            is ApiResult.Err -> {}
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("小德答疑", fontWeight = FontWeight.Bold) },
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
        Column(Modifier.padding(pad).fillMaxSize().pageFadeIn().background(MaterialTheme.colorScheme.background)) {
            if (loading) { LoadingBox() }
            else if (list.isEmpty()) { EmptyBox("暂无可解答的问题") }
            else {
                LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            Icon(Icons.Filled.SupportAgent, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("常见问题", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    items(list, key = { it.id }) { f ->
                        Card(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column(Modifier.fillMaxWidth().clickable { expanded = if (expanded == f.id) null else f.id }.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (f.pinned) {
                                        Text("置顶", fontSize = 10.sp, color = Color.White,
                                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF44336)).padding(horizontal = 5.dp, vertical = 1.dp))
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(f.question, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                    Icon(
                                        if (expanded == f.id) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (expanded == f.id) {
                                    Spacer(Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Text(f.answer, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
}
