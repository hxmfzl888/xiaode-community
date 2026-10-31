package com.xiaode.community.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xiaode.community.data.ApiClient

/**
 * ============================================================
 * UNIFIED_COMMENT_UI_V1 —— 统一评论组件（应用 / 资源共用）
 *
 * 设计要点（参考 StarFree 的圆润卡片风格，但用 Compose 重写）：
 * 1. 渐变头像环：头像外圈一圈极淡的主题色渐变，视觉更"活"
 * 2. 气泡卡片：评论内容装进浅色圆角气泡，带左上角小尖角暗示来源
 * 3. 入场动画：每条评论淡入 + 轻微上移，按索引错峰出现
 * 4. 星级动画：点评分星星时逐颗弹出，带过冲弹性
 * 5. 展开动画：长评论"展开/收起"平滑高度过渡
 * 6. 输入区：气泡输入框 + 圆形发送按钮，发送时按钮缩放反馈
 * 7. 空状态：柔和的插画式占位
 * ============================================================
 */

/** 一条评论的通用数据 */
data class UiComment(
    val id: Int,
    val userId: Int,
    val nickname: String,
    val avatar: String,
    val content: String,
    val rating: Int = 0,       // 0 = 不显示星级（资源评论）
    val createdAt: String = "",
)

/** 时间格式化：尽量简短 */
private fun shortTime(s: String): String {
    if (s.isBlank()) return ""
    // 后端格式 2026-10-31T01:45:36.123456 → 取到分钟
    val t = s.replace("T", " ")
    return if (t.length >= 16) t.substring(5, 16) else t
}

/**
 * 评论区（放在 LazyColumn 里）
 *
 * @param showRating 是否显示星级（应用 true / 资源 false）
 */
fun LazyListScope.CommentSection(
    comments: List<UiComment>,
    showRating: Boolean,
    inputText: String,
    onInputChange: (String) -> Unit,
    rating: Int = 0,
    onRatingChange: ((Int) -> Unit)? = null,
    onSubmit: () -> Unit,
    onDelete: ((UiComment) -> Unit)? = null,
    myId: Int = 0,
    enabled: Boolean = true,
    title: String = "全部评论",
) {
    // ---------- 输入卡 ----------
    item(key = "cmt_input") {
        Spacer(Modifier.height(12.dp))
        CommentInputCard(
            text = inputText,
            onTextChange = onInputChange,
            showRating = showRating,
            rating = rating,
            onRatingChange = onRatingChange,
            onSubmit = onSubmit,
            enabled = enabled,
        )
    }

    // ---------- 标题 ----------
    item(key = "cmt_title") {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧小竖条，强调标题
            Box(
                Modifier
                    .size(width = 3.5.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$title ${comments.size}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
    }

    // ---------- 列表 / 空状态 ----------
    if (comments.isEmpty()) {
        item(key = "cmt_empty") {
            CommentEmptyState()
        }
    } else {
        itemsIndexed(comments, key = { i, c -> "c_${c.id}_$i" }) { index, c ->
            CommentRow(
                comment = c,
                showRating = showRating,
                index = index,
                canDelete = onDelete != null && myId != 0 && c.userId == myId,
                onDelete = { onDelete?.invoke(c) },
            )
        }
    }
}


// ============================================================
// 评论输入卡
// ============================================================
@Composable
private fun CommentInputCard(
    text: String,
    onTextChange: (String) -> Unit,
    showRating: Boolean,
    rating: Int,
    onRatingChange: ((Int) -> Unit)?,
    onSubmit: () -> Unit,
    enabled: Boolean,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (showRating) "写下你的评价" else "说点什么",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )

            // ---- 星级（仅应用） ----
            if (showRating && onRatingChange != null) {
                Spacer(Modifier.height(8.dp))
                RatingPicker(rating = rating, onPick = onRatingChange)
            }

            Spacer(Modifier.height(10.dp))

            // ---- 气泡输入框 + 发送按钮 ----
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                ) {
                    if (text.isEmpty()) {
                        Text(
                            if (showRating) "说说你的使用体验…" else "友善的评论更容易被看到…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { if (it.length <= 500) onTextChange(it) },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                        ),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.width(9.dp))

                val canSend = enabled && text.isNotBlank()
                val sendScale by animateFloatAsState(
                    targetValue = if (canSend) 1f else 0.92f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "sendScale",
                )
                Box(
                    Modifier
                        .size(42.dp)
                        .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                        .clip(CircleShape)
                        .background(
                            if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )
                        .clickable(enabled = canSend) { onSubmit() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Send,
                        "发送",
                        tint = if (canSend) Color.White
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // 字数提示（超过 400 才出现，平时不打扰）
            if (text.length > 400) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${text.length}/500",
                    fontSize = 11.sp,
                    color = if (text.length >= 500) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

// ============================================================
// 星级选择器（逐颗弹跳）
// ============================================================
@Composable
private fun RatingPicker(rating: Int, onPick: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { s ->
            val active = s <= rating
            val scale by animateFloatAsState(
                targetValue = if (active) 1f else 0.86f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "star$s",
            )
            Icon(
                if (active) Icons.Filled.Star else Icons.Filled.StarBorder,
                null,
                tint = if (active) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clickable { onPick(s) },
            )
        }
        Spacer(Modifier.width(8.dp))
        val label = when (rating) {
            5 -> "很棒"
            4 -> "不错"
            3 -> "一般"
            2 -> "较差"
            1 -> "很差"
            else -> "点星评分"
        }
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (rating > 0) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

// ============================================================
// 单条评论
// ============================================================
@Composable
private fun CommentRow(
    comment: UiComment,
    showRating: Boolean,
    index: Int,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    // 入场动画：按索引错峰（最多延迟 8 条，避免长列表等待）
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index.coerceAtMost(8) * 45).toLong())
        appeared = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(260),
        label = "cmtAlpha",
    )
    val transY by animateFloatAsState(
        targetValue = if (appeared) 0f else 14f,
        animationSpec = tween(260),
        label = "cmtY",
    )

    // 长评论折叠
    val collapsed = comment.content.length > 120
    var expanded by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .graphicsLayer { this.alpha = alpha; translationY = transY },
    ) {
        // ---- 头像（带渐变环） ----
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            )
                        )
                    ),
            )
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (comment.avatar.isNotBlank()) {
                    AsyncImage(
                        model = ApiClient.absUrl(comment.avatar),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(38.dp).clip(CircleShape),
                    )
                } else {
                    Text(
                        comment.nickname.take(1).ifBlank { "德" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        // ---- 气泡 ----
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.nickname,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showRating && comment.rating > 0) {
                    Spacer(Modifier.width(6.dp))
                    Row {
                        (1..comment.rating).forEach { _ ->
                            Icon(
                                Icons.Filled.Star, null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (comment.createdAt.isNotBlank()) {
                    Text(
                        shortTime(comment.createdAt),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            Spacer(Modifier.height(5.dp))

            // 气泡本体
            Box(
                Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 3.dp, topEnd = 14.dp,
                            bottomStart = 14.dp, bottomEnd = 14.dp,
                        )
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 13.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(
                        comment.content,
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = if (collapsed && !expanded) 3 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (collapsed) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (expanded) "收起" else "展开全部",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { expanded = !expanded },
                        )
                    }
                }
            }

            if (canDelete) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "删除",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onDelete() }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// ============================================================
// 空状态
// ============================================================
@Composable
private fun CommentEmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("💬", fontSize = 26.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "还没有评论",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "来抢第一个沙发吧",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
    }
}