package com.xiaode.community.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import coil.compose.AsyncImage
import com.xiaode.community.data.ApiResult
import com.xiaode.community.data.CardUserOut
import com.xiaode.community.data.Repo
import com.xiaode.community.data.safeCall
import com.xiaode.community.ui.common.EmptyBox
import com.xiaode.community.ui.common.LoadingBox
import com.xiaode.community.ui.theme.BrandBlue

@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun FollowScreen(nav: NavController, type: String) {
    val isFollowers = type == "followers"
    var list by remember { mutableStateOf<List<CardUserOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(type) {
        loading = true
        val r = if (isFollowers) safeCall { Repo.api.myFollowers() } else safeCall { Repo.api.myFollowing() }
        when (r) {
            is ApiResult.Ok -> list = r.data
            is ApiResult.Err -> {}
        }
        loading = false
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isFollowers) "我的粉丝" else "我的关注", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface),
            )
        },
    ) { pad ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(pad)) { LoadingBox() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad)) { EmptyBox(if (isFollowers) "还没有粉丝" else "还没有关注任何人") }
            else -> LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(list) { u ->
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate("user/${u.id}") }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(model = com.xiaode.community.data.ApiClient.absUrl(u.avatar), contentDescription = null, modifier = Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.nickname.ifBlank { u.username }, fontWeight = FontWeight.Medium)
                            Text("Lv.${u.level}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                }
            }
        }
    }
}
