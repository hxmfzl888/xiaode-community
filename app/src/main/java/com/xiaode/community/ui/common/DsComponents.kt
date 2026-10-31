package com.xiaode.community.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaode.community.ui.theme.DS
import com.xiaode.community.ui.theme.lighten

/**
 * 设计系统组件库 DS Components
 * 借鉴 StarFree 设计语言，全站统一使用
 */

// ==================== 渐变功能格子 ====================
@Composable
fun GradientEntry(
    icon: ImageVector,
    label: String,
    brush: Brush,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: () -> Unit,
) {
    val accent = brushAccent(brush)
    Column(
        modifier
            .clip(RoundedCornerShape(DS.Radius.md))
            .clickable(onClick = onClick)
            .padding(vertical = DS.Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(size)
                .shadow(6.dp, RoundedCornerShape(size / 3), ambientColor = accent.copy(alpha = 0.45f), spotColor = accent.copy(alpha = 0.55f), clip = false)
                .clip(RoundedCornerShape(size / 3))
                .background(brush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.46f))
        }
        Spacer(Modifier.height(DS.Space.xs))
        Text(
            label,
            fontSize = DS.Text.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** 从 Brush 估一个代表色用于投影 */
private fun brushAccent(b: Brush): Color = Color(0xFF3CC9A4)

// ==================== VIP 徽章（金粉紫三色）====================
@Composable
fun VipBadge(
    modifier: Modifier = Modifier,
    text: String = "VIP",
    fontSize: Int = 9,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(DS.Radius.xs))
            .background(DS.Gradient.vip)
            .padding(horizontal = 6.dp, vertical = 1.5.dp),
    ) {
        Text(
            text,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** 用户等级 + VIP 标签组 */
@Composable
fun LevelTag(level: Int, isVip: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Lv.$level",
            fontSize = DS.Text.tiny,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isVip) {
            Spacer(Modifier.width(4.dp))
            VipBadge()
        }
    }
}

// ==================== 骨架屏 Shimmer ====================
@Composable
fun shimmerBrush(): Brush {
    val dark = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val light = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    return Brush.linearGradient(
        colors = listOf(dark, light, dark),
        start = androidx.compose.ui.geometry.Offset(x * 400f, 0f),
        end = androidx.compose.ui.geometry.Offset(x * 400f + 400f, 400f),
    )
}

/** 骨架块 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    radius: Dp = DS.Radius.sm,
) {
    Box(modifier.clip(RoundedCornerShape(radius)).background(shimmerBrush()))
}

/** 列表骨架屏（帖子/资源通用） */
@Composable
fun SkeletonPostList(count: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        repeat(count) {
            SkeletonPostCard()
            Spacer(Modifier.height(DS.Space.sm))
        }
    }
}

@Composable
fun SkeletonPostCard(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = DS.Space.sm, vertical = DS.Space.xs)
            .clip(RoundedCornerShape(DS.Radius.md))
            .background(MaterialTheme.colorScheme.surface)
            .padding(DS.Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(38.dp), DS.Radius.pill)
            Spacer(Modifier.width(DS.Space.sm))
            Column(Modifier.weight(1f)) {
                SkeletonBox(Modifier.width(80.dp).height(12.dp))
                Spacer(Modifier.height(6.dp))
                SkeletonBox(Modifier.width(50.dp).height(10.dp))
            }
        }
        Spacer(Modifier.height(DS.Space.md))
        SkeletonBox(Modifier.fillMaxWidth().height(13.dp))
        Spacer(Modifier.height(6.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.85f).height(13.dp))
        Spacer(Modifier.height(6.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.6f).height(13.dp))
        Spacer(Modifier.height(DS.Space.md))
        Row {
            repeat(3) {
                SkeletonBox(Modifier.size(88.dp, 88.dp), DS.Radius.sm)
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

// ==================== 空态（借鉴 StarFree no-data）====================
@Composable
fun EmptyState(
    icon: String = "📭",
    title: String = "暂时没有内容",
    desc: String = "",
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 56.dp,
) {
    Box(modifier.fillMaxWidth().padding(vertical = verticalPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 大号圆形图标底
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = 30.sp)
            }
            Spacer(Modifier.height(DS.Space.lg))
            Text(
                title,
                fontSize = DS.Text.body1,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (desc.isNotBlank()) {
                Spacer(Modifier.height(DS.Space.xs))
                Text(
                    desc,
                    fontSize = DS.Text.body2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(DS.Space.lg))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(DS.Radius.pill))
                        .background(DS.Gradient.primary)
                        .clickable(onClick = onAction)
                        .padding(horizontal = DS.Space.xl, vertical = DS.Space.sm),
                ) {
                    Text(actionText, fontSize = DS.Text.body, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ==================== 空态（矢量图标版）====================
@Composable
fun IconEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String = "暂时没有内容",
    desc: String = "",
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 56.dp,
    iconTint: Color? = null,
) {
    Box(modifier.fillMaxWidth().padding(vertical = verticalPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.height(DS.Space.lg))
            Text(
                title,
                fontSize = DS.Text.body1,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (desc.isNotBlank()) {
                Spacer(Modifier.height(DS.Space.xs))
                Text(
                    desc,
                    fontSize = DS.Text.body2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(DS.Space.lg))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(DS.Radius.pill))
                        .background(DS.Gradient.primary)
                        .clickable(onClick = onAction)
                        .padding(horizontal = DS.Space.xl, vertical = DS.Space.sm),
                ) {
                    Text(actionText, fontSize = DS.Text.body, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ==================== 九宫格图片墙 ====================
@Composable
fun ImageGrid(
    images: List<String>,
    modifier: Modifier = Modifier,
    maxShow: Int = 9,
    cellSize: Dp = 100.dp,
    spacing: Dp = 4.dp,
    onImageClick: (Int) -> Unit = {},
) {
    if (images.isEmpty()) return
    val show = images.take(maxShow)
    val count = show.size

    // 1 张：大图
    if (count == 1) {
        AsyncImageGridCell(show[0], cellSize * 2.2f, cellSize * 1.4f, 0) { onImageClick(0) }
        return
    }
    // 2 张：两个半宽
    if (count == 2) {
        Row(modifier) {
            show.forEachIndexed { i, url ->
                AsyncImageGridCell(url, cellSize * 1.1f, cellSize * 1.1f, i) { onImageClick(i) }
                if (i == 0) Spacer(Modifier.width(spacing))
            }
        }
        return
    }
    // 3+ 张：九宫格 3 列
    Column(modifier) {
        show.chunked(3).forEachIndexed { rowIdx, rowImages ->
            Row {
                rowImages.forEachIndexed { colIdx, url ->
                    val i = rowIdx * 3 + colIdx
                    Box {
                        AsyncImageGridCell(url, cellSize, cellSize, i) { onImageClick(i) }
                        // 最后一张且还有更多 → 显示 +N
                        if (i == maxShow - 1 && images.size > maxShow) {
                            Box(
                                Modifier
                                    .size(cellSize)
                                    .clip(RoundedCornerShape(DS.Radius.sm))
                                    .background(Color(0x99000000)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+${images.size - maxShow}", color = Color.White, fontSize = DS.Text.title, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (colIdx < rowImages.size - 1) Spacer(Modifier.width(spacing))
                }
            }
            if (rowIdx < show.chunked(3).size - 1) Spacer(Modifier.height(spacing))
        }
    }
}

@Composable
private fun AsyncImageGridCell(
    url: String,
    w: Dp,
    h: Dp,
    index: Int,
    onClick: () -> Unit,
) {
    coil.compose.AsyncImage(
        model = com.xiaode.community.data.ApiClient.absUrl(url),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier
            .size(w, h)
            .clip(RoundedCornerShape(DS.Radius.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    )
}

// ==================== 底部固定操作条 ====================
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .shadow(DS.Elevation.float, RoundedCornerShape(topStart = DS.Radius.lg, topEnd = DS.Radius.lg), clip = false)
            .clip(RoundedCornerShape(topStart = DS.Radius.lg, topEnd = DS.Radius.lg))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = DS.Space.md, vertical = DS.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

// ==================== 统计胶囊 ====================
@Composable
fun StatPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = DS.Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = DS.Text.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = DS.Text.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ==================== 标签 Chip ====================
@Composable
fun DsChip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val bg = if (selected) DS.Gradient.primary else null
    Box(
        modifier
            .clip(RoundedCornerShape(DS.Radius.pill))
            .then(
                if (bg != null) Modifier.background(bg)
                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = DS.Space.md, vertical = DS.Space.xs),
    ) {
        Text(
            text,
            fontSize = DS.Text.body2,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}