package com.xiaode.community.ui.resource

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.xiaode.community.ui.common.EmptyBox
import com.xiaode.community.ui.common.EntranceAnimation

/** 我上传的资源（含审核状态） */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ResourceMineScreen(nav: NavController) {
    val snackbar = remember { SnackbarHostState() }
    var items by remember { mutableStateOf<List<ResourceOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        when (val r = safeCall { Repo.api.myResources() }) {
            is ApiResult.Ok -> items = r.data.items
            is ApiResult.Err -> snackbar.showSnackbar(r.message)
        }
        loading = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("我的资源", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            Icons.Filled.ArrowBack, "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { nav.navigate("resource_upload") }) {
                        Text(
                            "上传",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { pad ->
        Box(
            Modifier.padding(pad).fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                    )
                }
                items.isEmpty() -> EmptyBox("还没有上传过资源")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { r ->
                        EntranceAnimation(key = r.id, offsetY = -14) {
                            Box {
                                ResourceCard(r) { nav.navigate("resource_detail/" + r.id) }
                                // 状态角标
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 16.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            when (r.status) {
                                                "approved" -> Color(0xFF22C55E).copy(alpha = 0.14f)
                                                "rejected" -> Color(0xFFEF4444).copy(alpha = 0.14f)
                                                else -> Color(0xFFF59E0B).copy(alpha = 0.16f)
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        when (r.status) {
                                            "approved" -> "已通过"
                                            "rejected" -> "未通过"
                                            else -> "审核中"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when (r.status) {
                                            "approved" -> Color(0xFF16A34A)
                                            "rejected" -> Color(0xFFDC2626)
                                            else -> Color(0xFFD97706)
                                        },
                                    )
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