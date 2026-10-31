package com.xiaode.community.ui.square
import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalContext
import com.xiaode.community.ui.common.*
import com.xiaode.community.ui.theme.BrandBlue
import com.xiaode.community.ui.common.pageFadeIn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.SentimentSatisfied

/**
 * 帖子详情页 —— 评论只有在这里才可见。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(nav: NavController, momentId: Int) {
    val api = Repo.api
    val myId = XiaodeApp.store.currentUserJson?.let { extractId(it) } ?: 0
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var moment by remember { mutableStateOf<MomentOut?>(null) }
    var comments by remember { mutableStateOf<List<CommentOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<CommentOut?>(null) }
    var liked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableIntStateOf(0) }
    var commentIsCode by remember { mutableStateOf(false) }
    var commentLang by remember { mutableStateOf("js") }

    suspend fun reload() {
        when (val r = safeCall { api.momentDetail(momentId) }) {
            is ApiResult.Ok -> {
                moment = r.data
                comments = r.data.comments
                liked = r.data.liked
                likeCount = r.data.likeCount
            }
            is ApiResult.Err -> snackbar.showSnackbar(r.message)
        }
        loading = false
    }

    LaunchedEffect(momentId) { reload() }

    // SHARE_FEATURE_V1: 分享面板状态
    var showShare by remember { mutableStateOf(false) }
    var showCommentEmoji by remember { mutableStateOf(false) }
    val shareCtx = androidx.compose.ui.platform.LocalContext.current

    // SHARE_FEATURE_V2: 统一底部弹窗 + 渐变图标
    if (showShare) {
        val shareUrl = "https://api.delai.fun/web?post=" + momentId
        val shareTitle = (moment?.title ?: "").ifBlank { "小德社区帖子" }
        com.xiaode.community.ui.common.DsBottomSheet(
            visible = true,
            onDismiss = { showShare = false },
            title = "分享帖子",
            subtitle = shareTitle.take(24),
        ) {
            // 分享渠道：渐变格子
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                com.xiaode.community.ui.common.SheetGradientAction(
                    icon = Icons.Filled.Link,
                    label = "复制链接",
                    brush = com.xiaode.community.ui.theme.DS.Gradient.blue,
                    accent = Color(0xFF89ADFF),
                ) {
                    val cm = shareCtx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("link", shareUrl))
                    showShare = false
                    scope.launch { snackbar.showSnackbar("链接已复制") }
                }
                com.xiaode.community.ui.common.SheetGradientAction(
                    icon = Icons.Filled.Share,
                    label = "更多",
                    brush = com.xiaode.community.ui.theme.DS.Gradient.mint,
                    accent = Color(0xFF47BF7F),
                ) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, shareTitle)
                        putExtra(
                            android.content.Intent.EXTRA_TEXT,
                            shareTitle + "\n" + shareUrl + "\n—— 来自小德社区",
                        )
                    }
                    showShare = false
                    shareCtx.startActivity(
                        android.content.Intent.createChooser(intent, "分享到"),
                    )
                }
                com.xiaode.community.ui.common.SheetGradientAction(
                    icon = Icons.Filled.Info,
                    label = "收藏",
                    brush = com.xiaode.community.ui.theme.DS.Gradient.orange,
                    accent = Color(0xFFFF5C10),
                ) {
                    showShare = false
                }
            }
            Spacer(Modifier.height(10.dp))
            // 链接预览条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(
                        com.xiaode.community.ui.theme.DS.Radius.sm
                    ))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    shareUrl,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("帖子详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                // SHARE_FEATURE_V1: 分享按钮
                actions = {
                    IconButton(onClick = { showShare = true }) {
                        Icon(
                            Icons.Filled.Share,
                            "分享",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                // STARFREE_DETAIL: 白底顶栏
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            CommentInputBar(
                value = input,
                replyTo = replyTo?.nickname,
                isCode = commentIsCode,
                codeLang = commentLang,
                onToggleCode = { commentIsCode = !commentIsCode },
                onLangChange = { commentLang = it },
                onChange = { input = it },
                onCancelReply = { replyTo = null },
                onSend = {
                    if (input.isBlank()) return@CommentInputBar
                    scope.launch {
                        val langArg = if (commentIsCode) commentLang else ""
                        val rt = replyTo
                        val pid = if (rt == null) null else (rt.parentId ?: rt.id)
                        val rid = if (rt == null) null else rt.id
                        when (val r = safeCall { api.createComment(momentId, CommentCreateIn(input.trim(), pid, rid, langArg)) }) {
                            is ApiResult.Ok -> {
                                input = ""; replyTo = null
                                reload()
                            }
                            is ApiResult.Err -> snackbar.showSnackbar(r.message)
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (loading) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
            }
            return@Scaffold
        }
        val m = moment
        if (m == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("帖子不存在")
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .pageFadeIn()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // 帖子主体
            item {
                // SF_DETAIL_SLIM: 与卡片统一 10dp 圆角 / 13dp 内边距
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(Modifier.padding(13.dp)) {
                        // SF_DETAIL_V3: 标题在最上（源码 .title font-size:30upx; font-weight:900）
                        if (m.title.isNotBlank()) {
                            Text(
                                m.title,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 27.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = (-0.3).sp,
                            )
                            Spacer(Modifier.height(11.dp))
                        }
                        // 作者行
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            XiaodeAvatar(m.avatar, 34.dp, m.levelEffect, m.isVip)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        m.nickname.ifBlank { m.username },
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    LevelBadge(m.level, m.levelEffect)
                                    if (m.isVip) { Spacer(Modifier.width(3.dp)); VipBadge() }
                                }
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    timeAgo(m.createdAt),
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (m.content.isNotBlank()) {
                            Spacer(Modifier.height(11.dp))
                            if (m.lang.isNotBlank()) {
                                CodeBlock(code = m.content, lang = m.lang)
                            } else {
                                val ctxD = LocalContext.current
                                Text(
                                    RichText.render(m.content),
                                    fontSize = 14.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { copyText(ctxD, m.content) },
                                    ),
                                )
                            }
                        }
                        if (m.images.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            ImageGrid(m.images, allowDownload = m.allowDownload)
                        }
                        if (m.videos.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            m.videos.forEach { v ->
                                VideoPlayer(url = v, height = 200.dp)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        // SF_DETAIL_V3: 操作栏胶囊化
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (liked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    )
                                    .clickable {
                                        scope.launch {
                                            when (val r = safeCall { api.likeMoment(momentId) }) {
                                                is ApiResult.Ok -> { liked = r.data.liked; likeCount = r.data.likeCount; XiaodeApp.markMomentsDirty() }
                                                is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 13.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "$likeCount",
                                    fontSize = 12.sp,
                                    fontWeight = if (liked) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }

                        Spacer(Modifier.height(10.dp))
                        com.xiaode.community.ui.common.TipSection(
                            momentId = momentId,
                            isMine = (moment?.userId ?: 0) == myId,
                        )
                    }
                }
            }

            // SF_DETAIL_V3: 评论区独立白卡，与帖子块分开
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "评论",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${comments.size}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (comments.isEmpty()) {
                item { EmptyBox("还没有评论，来抢沙发～") }
            } else {
                itemsIndexed(comments, key = { i, it -> "${it.id}_${i}" }) { _, c ->
                    // STAGGER_COMMENT: 评论错峰淡入
                    EntranceAnimation(key = c.id, offsetY = -18) {
                    // SF_DETAIL_V3: 评论项白底，左右 10dp 对齐评论区
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                    CommentItem(
                        c = c,
                        myId = myId,
                        onReplyTo = { replyTo = it },
                        onReplyLike = { rp ->
                            scope.launch {
                                when (val r = safeCall { api.likeComment(rp.id) }) {
                                    is ApiResult.Ok -> reload()
                                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                }
                            }
                        },
                        onReplyDelete = { rp ->
                            scope.launch {
                                when (val r = safeCall { api.deleteComment(rp.id) }) {
                                    is ApiResult.Ok -> reload()
                                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                }
                            }
                        },
                        onReply = { replyTo = c },
                        onDelete = {
                            scope.launch {
                                when (val r = safeCall { api.deleteComment(c.id) }) {
                                    is ApiResult.Ok -> reload()
                                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                }
                            }
                        },
                        onLike = {
                            scope.launch {
                                when (val r = safeCall { api.likeComment(c.id) }) {
                                    is ApiResult.Ok -> reload()
                                    is ApiResult.Err -> snackbar.showSnackbar(r.message)
                                }
                            }
                        },
                    )
                    }
                    }
                }
            }
            item { Spacer(Modifier.height(70.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentItem(
    c: CommentOut,
    myId: Int,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onLike: () -> Unit,
    onReplyLike: (CommentOut) -> Unit = {},
    onReplyDelete: (CommentOut) -> Unit = {},
    onReplyTo: (CommentOut) -> Unit = {},
) {
    var showReplies by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        XiaodeAvatar(c.avatar, 34.dp, c.levelEffect, c.isVip)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.nickname.ifBlank { c.username }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(5.dp))
                LevelBadge(c.level, c.levelEffect)
                if (c.isVip) { Spacer(Modifier.width(4.dp)); VipBadge() }
            }
            Spacer(Modifier.height(3.dp))
            if (c.lang.isNotBlank()) {
                CodeBlock(code = c.content, lang = c.lang)
            } else {
                val ctxC = LocalContext.current
                Text(
                    RichText.render(c.content),
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { copyText(ctxC, c.content) }),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(timeAgo(c.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text("回复", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onReply))
                if (c.userId == myId) {
                    Spacer(Modifier.width(16.dp))
                    Text("删除", fontSize = 12.sp, color = Color(0xFFF44336), modifier = Modifier.clickable(onClick = onDelete))
                }
                if (c.replies.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    val arrowRot by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (showReplies) 180f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(220),
                        label = "replyArrow",
                    )
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(com.xiaode.community.ui.theme.DS.Radius.pill))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                            .clickable { showReplies = !showReplies }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${c.replies.size} 条回复",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer(rotationZ = arrowRot),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.clickable(onClick = onLike), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (c.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                        tint = if (c.liked) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("${c.likeCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 回复（楼中楼）—— 平滑展开/收起
            androidx.compose.animation.AnimatedVisibility(
                visible = showReplies && c.replies.isNotEmpty(),
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(220)
                ) + androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.tween(240),
                    expandFrom = Alignment.Top,
                ),
                exit = androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(160)
                ) + androidx.compose.animation.shrinkVertically(
                    animationSpec = androidx.compose.animation.core.tween(200),
                    shrinkTowards = Alignment.Top,
                ),
            ) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(8.dp),
                ) {
                    c.replies.forEach { rp ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            XiaodeAvatar(rp.avatar, 26.dp, rp.levelEffect, rp.isVip)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(rp.nickname.ifBlank { rp.username }, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(4.dp))
                                    LevelBadge(rp.level, rp.levelEffect)
                                }
                                Spacer(Modifier.height(2.dp))
                                if (rp.replyToNickname.isNotBlank() && rp.replyToId != null && rp.replyToId != rp.parentId) {
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("@" + rp.replyToNickname) }
                                        },
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(1.dp))
                                }
                                if (rp.lang.isNotBlank()) {
                                    CodeBlock(code = rp.content, lang = rp.lang)
                                } else {
                                    Text(rp.content, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Spacer(Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(timeAgo(rp.createdAt), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    Text("回复", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onReplyTo(rp) })
                                    if (rp.userId == myId) {
                                        Spacer(Modifier.width(12.dp))
                                        Text("删除", fontSize = 11.sp, color = Color(0xFFF44336), modifier = Modifier.clickable { onReplyDelete(rp) })
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Row(Modifier.clickable { onReplyLike(rp) }, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (rp.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null,
                                            tint = if (rp.liked) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Text("${rp.likeCount}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentInputBar(
    value: String,
    replyTo: String?,
    onChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    isCode: Boolean = false,
    codeLang: String = "js",
    onToggleCode: () -> Unit = {},
    onLangChange: (String) -> Unit = {},
) {
    var langMenu by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Column {
            if (replyTo != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("回复 @$replyTo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.Close, null, Modifier.size(16.dp).clickable(onClick = onCancelReply),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 文字 / 代码 切换 + 语言选择
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = !isCode,
                    onClick = { if (isCode) onToggleCode() },
                    label = { Text("文字", fontSize = 12.sp) },
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = isCode,
                    onClick = { if (!isCode) onToggleCode() },
                    label = { Text("代码", fontSize = 12.sp) },
                )
                if (isCode) {
                    Spacer(Modifier.width(8.dp))
                    Box {
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.outlineVariant)
                                .clickable { langMenu = true }.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(langLabel(codeLang), fontSize = 12.sp, color = Color(0xFF4C6FFF))
                            Icon(Icons.Filled.ArrowDropDown, null, tint = Color(0xFF4C6FFF), modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                            CODE_LANGS.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, fontSize = 13.sp) },
                                    onClick = { onLangChange(key); langMenu = false },
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    placeholder = { Text(if (isCode) "在此粘贴代码…" else "说点什么…", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = if (isCode) 6 else 3,
                    textStyle = if (isCode) androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ) else androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                )
                Spacer(Modifier.width(8.dp))
                // 表情按钮（OwO）
                IconButton(
                    onClick = { showEmoji = !showEmoji },
                    modifier = Modifier.clip(CircleShape)
                        .background(
                            if (showEmoji) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .size(44.dp),
                ) {
                    Icon(
                        Icons.Filled.SentimentSatisfied,
                        "表情",
                        tint = if (showEmoji) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank(),
                    modifier = Modifier.clip(CircleShape).background(BrandBlue).size(44.dp),
                ) {
                    Icon(Icons.Filled.Send, "发送", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            // 表情面板（平滑展开）
            androidx.compose.animation.AnimatedVisibility(
                visible = showEmoji,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.tween(200)
                ) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = androidx.compose.animation.core.tween(160)
                ) + androidx.compose.animation.fadeOut(),
            ) {
                com.xiaode.community.ui.common.EmojiPanel(
                    onPick = { e -> onChange(value + e) },
                )
            }
        }
    }
}

/** 复用到剪贴板 */
internal fun copyText(ctx: android.content.Context, text: String) {
    try {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("xiaode", text))
        android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
    } catch (_: Throwable) {}
}
