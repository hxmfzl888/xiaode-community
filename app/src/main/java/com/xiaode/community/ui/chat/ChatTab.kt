package com.xiaode.community.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.xiaode.community.data.ApiResult
import com.xiaode.community.data.PrivateConvOut
import com.xiaode.community.data.Repo
import com.xiaode.community.data.safeCall
import com.xiaode.community.ui.common.EmptyBox
import com.xiaode.community.ui.common.LoadingBox
import com.xiaode.community.ui.square.timeAgo
import com.xiaode.community.ui.theme.BrandBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTab(nav: NavController) {
    var list by remember { mutableStateOf<List<PrivateConvOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        when (val r = safeCall { Repo.api.pmConversations() }) {
            is ApiResult.Ok -> list = r.data
            is ApiResult.Err -> {}
        }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("私信", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { nav.navigate("notifications") }) {
                    Icon(Icons.Filled.Notifications, "系统消息", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
        )
        when {
            loading -> LoadingBox()
            list.isEmpty() -> EmptyBox("还没有私信，去别人主页发一条吧")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list) { c ->
                    val u = c.user
                    if (u != null) {
                        Row(
                            Modifier.fillMaxWidth().clickable { nav.navigate("pm/${u.id}?name=${u.nickname.ifBlank { u.username }}") }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(model = com.xiaode.community.data.ApiClient.absUrl(u.avatar), contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.nickname.ifBlank { u.username }, fontWeight = FontWeight.Medium)
                                Text(c.lastMsg, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(timeAgo(c.lastTime), fontSize = 11.sp, color = Color(0xFFAAB3C5))
                                if (c.unread > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFF44336)), contentAlignment = Alignment.Center) {
                                        Text(if (c.unread > 99) "99+" else "${c.unread}", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFF0F0F0))
                    }
                }
            }
        }
    }
}
