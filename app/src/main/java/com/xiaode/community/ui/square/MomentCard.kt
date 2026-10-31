package com.xiaode.community.ui.square

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xiaode.community.data.ApiClient
import com.xiaode.community.data.MomentOut
import com.xiaode.community.ui.common.*

@Composable
fun MomentCard(
    m: MomentOut,
    myId: Int,
    onLike: () -> Unit,
    onDelete: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenUser: (Int) -> Unit = {},
    onReport: () -> Unit = {},
    // FAV_VIEW_CARD_V1: 收藏回调（默认空实现，未接的页面不显示收藏按钮）
    onFavorite: (() -> Unit)? = null,
            ) {
    var menuOpen by remember { mutableStateOf(false) }
    var viewerImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var viewerIndex by remember { mutableIntStateOf(0) }
    var showVideo by remember { mutableStateOf(false) }

    // STARFREE_V2: 更圆润的卡片 + 更宽松的内边距
    // SF_CARD_SLIM: 按 StarFree 源码 20rpx≈10dp 圆角、15dp 内外边距重做
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.clickable(onClick = onOpenDetail).padding(13.dp)) {
            // ---- 头部 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                XiaodeAvatar(m.avatar, 36.dp, m.levelEffect, m.isVip, onClick = { onOpenUser(m.userId) })
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            m.nickname.orEmpty().ifBlank { m.username.orEmpty() },
                            fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(6.dp))
                        LevelBadge(m.level, m.levelEffect)
                        if (m.isVip) { Spacer(Modifier.width(4.dp)); VipBadge() }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(timeAgo(m.createdAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (m.channelName.orEmpty().isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            // STARFREE_V2: 薄荷绿胶囊标签
                            Box(
                                Modifier.clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("#${m.channelName}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (m.status == "pending") {
                            Spacer(Modifier.width(6.dp))
                            Text("审核中", fontSize = 10.sp, color = Color(0xFFFF9800))
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreHoriz, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (m.userId == myId) {
                            DropdownMenuItem(
                                text = { Text("删除", color = Color(0xFFF44336)) },
                                onClick = { menuOpen = false; onDelete() },
                            )
                        } else {
                            DropdownMenuItem(text = { Text("举报") }, onClick = { menuOpen = false; onReport() })
                        }
                    }
                }
            }

            // ---- 标题 ----
            if (m.title.orEmpty().isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(m.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            // ---- 正文：代码块 or 普通文本（超45字折叠） ----
            if (m.content.orEmpty().isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                if (m.lang.orEmpty().isNotBlank()) {
                    // 代码块：不折叠，深色框 + 语言标签 + 复制
                    CodeBlock(code = m.content, lang = m.lang)
                } else {
                    val maxLen = 45
                    val isLong = m.content.length > maxLen
                    var expanded by remember(m.id) { mutableStateOf(false) }
                    val rawContent = m.content.orEmpty()
                    // 一次性渲染并缓存，避免滚动时反复计算
                    val renderedCache = remember(rawContent) { RichText.render(rawContent) }
                    val rendered = remember(rawContent, isLong, expanded) {
                        if (isLong && !expanded) {
                            val plain = renderedCache.text.replace("\n", " ")
                            androidx.compose.ui.text.AnnotatedString(
                                if (plain.length > maxLen) plain.take(maxLen) + "…" else plain
                            )
                        } else renderedCache
                    }
                    Text(
                        rendered,
                        fontSize = 14.5.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 21.sp,
                    )
                    if (isLong) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (expanded) "收起" else "查看详情",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { expanded = !expanded },
                        )
                    }
                }
            }

            // ---- 图片 ----
            if (m.images.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ImageGrid(m.images, onImageClick = { idx -> viewerImages = m.images; viewerIndex = idx })
            }
            // ---- 视频 ----
            if (m.videos.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.clickable { showVideo = true }) {
                    VideoPlayer(url = m.videos.first(), height = 200.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            Spacer(Modifier.height(10.dp))
            // SF_POLISH_CARD: 操作栏胶囊化 + 点赞弹跳
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val likeScale by animateFloatAsState(
                    targetValue = if (m.liked) 1.18f else 1f,
                    animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
                    label = "likeBounce",
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (m.liked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )
                        .clickable(onClick = onLike)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (m.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                        tint = if (m.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).graphicsLayer { scaleX = likeScale; scaleY = likeScale },
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${m.likeCount}",
                        fontSize = 12.sp,
                        fontWeight = if (m.liked) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (m.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .clickable(onClick = onOpenDetail)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.ChatBubbleOutline, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${m.commentCount}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // FAV_VIEW_CARD_V1: 浏览量（只读展示）
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Visibility, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        formatCount(m.viewCount),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                // FAV_VIEW_CARD_V1: 收藏按钮（最右侧，仅接了回调才显示）
                if (onFavorite != null) {
                    val favScale by animateFloatAsState(
                        targetValue = if (m.favorited) 1.18f else 1f,
                        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
                        label = "favBounce",
                    )
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (m.favorited) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            )
                            .clickable(onClick = onFavorite)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (m.favorited) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null,
                            tint = if (m.favorited) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { scaleX = favScale; scaleY = favScale },
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (m.favorited) "已收藏" else "收藏",
                            fontSize = 12.sp,
                            fontWeight = if (m.favorited) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (m.favorited) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // ---- 全屏查看 ----
    if (viewerImages.isNotEmpty()) {
        ImageViewerDialog(viewerImages, viewerIndex, { viewerImages = emptyList() }, allowDownload = m.allowDownload)
    }
    if (showVideo && m.videos.isNotEmpty()) {
        VideoViewerDialog(m.videos.first()) { showVideo = false }
    }
}


/** 图片九宫格（旧版，保留兼容；新代码请用 ui.common.ImageGrid） */
@Composable
fun ImageGrid(images: List<String>, onImageClick: ((Int) -> Unit)? = null, allowDownload: Boolean = true) {
    val n = images.size
    when {
        n == 1 -> AsyncImage(
            model = ApiClient.absUrl(images[0]), contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))
                .then(if (onImageClick != null) Modifier.clickable { onImageClick(0) } else Modifier),
        )
        n == 2 -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            images.forEachIndexed { i, it ->
                AsyncImage(
                    model = ApiClient.absUrl(it), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).height(130.dp).clip(RoundedCornerShape(16.dp))
                        .then(if (onImageClick != null) Modifier.clickable { onImageClick(i) } else Modifier),
                )
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            images.chunked(3).take(3).forEach { row ->
                val startIdx = images.indexOf(row.first())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEachIndexed { j, it ->
                        AsyncImage(
                            model = ApiClient.absUrl(it), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                                .then(if (onImageClick != null) Modifier.clickable { onImageClick(startIdx + j) } else Modifier),
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 视频播放器（本地/远程直接播放，带系统控件） */
@Composable
fun VideoPlayer(url: String, height: androidx.compose.ui.unit.Dp = 200.dp) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { c ->
            android.widget.VideoView(c).apply {
                setVideoURI(android.net.Uri.parse(ApiClient.absUrl(url)))
                setOnPreparedListener { it.isLooping = false; start() }
                setMediaController(android.widget.MediaController(c).apply { setAnchorView(this@apply) })
            }
        },
        modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp)).background(Color.Black),
    )
}

/** 简易相对时间 */
fun timeAgo(iso: String): String {
    if (iso.orEmpty().isBlank()) return ""
    return try {
        val t = java.time.OffsetDateTime.parse(iso.replace("Z", "+00:00")).toInstant().toEpochMilli()
        val diff = System.currentTimeMillis() - t
        when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000}分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000}小时前"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000}天前"
            else -> iso.orEmpty().take(10)
        }
    } catch (_: Exception) {
        try {
            val t = java.time.LocalDateTime.parse(iso).atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            "${(System.currentTimeMillis() - t) / 86_400_000}天前"
        } catch (_: Exception) { iso.orEmpty().take(10) }
    }
}


/**
 * FAV_VIEW_CARD_V1: 数字格式化 —— 1234 → 1234，12345 → 1.2万
 */
private fun formatCount(n: Int): String {
    return when {
        n < 10000 -> n.toString()
        n < 100000000 -> String.format("%.1f万", n / 10000.0)
        else -> String.format("%.1f亿", n / 100000000.0)
    }
}
