package com.xiaode.community.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * StarFree 风格动效工具库
 * 参考 StarFree App.vue 的 @keyframes show：
 *   0%   translateY(-50px)
 *   60%  translateY(+40upx)   ← 过冲
 *   100% translateY(0)        ← 回弹
 * 用 Compose 的 spring / tween 复刻这种「弹性入场」手感
 */

/** 回弹入场：内容从上方落下并轻微过冲后归位 */
@Composable
fun EntranceAnimation(
    key: Any? = Unit,
    delayMs: Int = 0,
    offsetY: Int = -40,
    content: @Composable () -> Unit,
) {
    var started by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        started = true
    }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.55f,      // 低于 1 = 有回弹
            stiffness = Spring.StiffnessLow,
        ),
        label = "entrance",
    )
    Box(
        Modifier.graphicsLayer {
            translationY = (1f - progress) * offsetY
            alpha = progress.coerceIn(0f, 1f)
            scaleX = 0.96f + 0.04f * progress
            scaleY = 0.96f + 0.04f * progress
        }
    ) { content() }
}

/** 逐项错峰入场（列表用） */
@Composable
fun StaggeredItem(index: Int, stepMs: Int = 45, content: @Composable () -> Unit) {
    EntranceAnimation(key = index, delayMs = (index.coerceAtMost(10)) * stepMs, content = content)
}

/** 可点击缩放反馈（按下 0.96 倍） */
@Composable
fun Modifier.pressScale(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .scale(scale)
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
}

/** StarFree 渐变背景：粉彩薄荷 → 白 */
@Composable
fun starfreeHeaderBrush(): Brush {
    val primary = MaterialTheme.colorScheme.primary
    return Brush.verticalGradient(
        listOf(
            primary.copy(alpha = 0.22f),
            primary.copy(alpha = 0.08f),
            Color.Transparent,
        )
    )
}

/** 胶囊标签（StarFree 话题标签样式） */
@Composable
fun CpTag(
    text: String,
    modifier: Modifier = Modifier,
    bg: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    fg: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 10.5.sp, color = fg, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** 统计数字块（头像区下方「帖子/关注/粉丝」） */
@Composable
fun StatCell(num: String, label: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.pressScale(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(num, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 网格入口方块 —— 彩色渐变版（StarFree 风格） */
@Composable
fun GridEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    badge: Int = 0,
    brush: androidx.compose.ui.graphics.Brush? = null,
    onClick: () -> Unit,
) {
    // 未指定渐变时，按 label 稳定分配到某个色系
    val autoBrush = brush ?: gradientFor(label)
    val accent = accentFor(label)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(com.xiaode.community.ui.theme.DS.Radius.md))
            .pressScale(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Box {
            Box(
                Modifier
                    .size(46.dp)
                    .shadow(
                        6.dp,
                        RoundedCornerShape(15.dp),
                        ambientColor = accent.copy(alpha = 0.55f),
                        spotColor = accent.copy(alpha = 0.65f),
                        clip = false,
                    )
                    .clip(RoundedCornerShape(15.dp))
                    .background(autoBrush),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    icon, null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFF4D4F))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        if (badge > 99) "99+" else badge.toString(),
                        fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            fontSize = com.xiaode.community.ui.theme.DS.Text.caption,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 按名称稳定分配渐变（同名同色，不同名尽量不同色） */
private fun gradientFor(label: String): androidx.compose.ui.graphics.Brush {
    val g = listOf(
        com.xiaode.community.ui.theme.DS.Gradient.blue,
        com.xiaode.community.ui.theme.DS.Gradient.mint,
        com.xiaode.community.ui.theme.DS.Gradient.orange,
        com.xiaode.community.ui.theme.DS.Gradient.purple,
        com.xiaode.community.ui.theme.DS.Gradient.pink,
        com.xiaode.community.ui.theme.DS.Gradient.green,
        com.xiaode.community.ui.theme.DS.Gradient.lake,
        com.xiaode.community.ui.theme.DS.Gradient.fire,
    )
    val idx = (label.hashCode() and 0x7FFFFFFF) % g.size
    return g[idx]
}

/** 渐变对应的投影色 */
private fun accentFor(label: String): Color {
    val c = listOf(
        Color(0xFF89ADFF), Color(0xFF3CC9A4), Color(0xFFFF5C10), Color(0xFF811AFF),
        Color(0xFFE8508C), Color(0xFF00AE4B), Color(0xFF2182BF), Color(0xFFDF5557),
    )
    val idx = (label.hashCode() and 0x7FFFFFFF) % c.size
    return c[idx]
}

/** 淡入 + 上浮 的通用 Loading 骨架感（用于内容出现） */
@Composable
fun FadeInUp(key: Any? = Unit, content: @Composable () -> Unit) {
    var started by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { started = true }
    val p by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "fadeup",
    )
    Box(Modifier.graphicsLayer { translationY = (1f - p) * 24f; alpha = p }) { content() }
}
/** 页面级淡入上浮（子页面统一入场动效，复刻 StarFree pageIn） */
@Composable
fun Modifier.pageFadeIn(delayMs: Int = 0): Modifier {
    // PAGE_FADE_FIX: 关键修复
    // 原实现 alpha 从 0 开始 → 视图不可见 → 触摸事件被拦截
    // 加在根布局时会导致整页所有按钮失效
    // 现在：alpha 下限 0.4，且立即开始动画（delayMs 默认 0）
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        started = true
    }
    val p by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "pageFadeIn",
    )
    return this.graphicsLayer {
        alpha = 0.4f + 0.6f * p.coerceIn(0f, 1f)
        translationY = (1f - p) * 18f
    }
}
