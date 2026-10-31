package com.xiaode.community.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计系统 DS v1
 * 借鉴 StarFree：统一圆角 / 字号 / 渐变 / 阴影 / 间距
 * 所有页面改造都应使用这里定义的常量，禁止继续硬编码
 */
object DS {

    // ============ 圆角（原 12 种 → 统一 4 档）============
    object Radius {
        val xs = 6.dp      // 小标签、徽章
        val sm = 10.dp     // 输入框、小卡片内元素
        val md = 14.dp     // 标准卡片（最常用）
        val lg = 20.dp     // 大卡片、弹窗
        val pill = 999.dp  // 胶囊
    }

    // ============ 字号（原 15 种 → 统一 7 档）============
    object Text {
        val tiny = 10.sp     // 角标、辅助
        val caption = 11.sp  // 次级说明
        val body2 = 12.sp    // 正文小
        val body = 13.sp     // 正文（最常用）
        val body1 = 15.sp    // 强调正文
        val title = 17.sp    // 卡片标题
        val h2 = 19.sp       // 页面小标题
        val h1 = 22.sp       // 页面大标题
    }

    // ============ 间距（原混乱 → 8 档）============
    object Space {
        val xxs = 4.dp
        val xs = 6.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 20.dp
        val xxl = 28.dp
    }

    // ============ 渐变体系（借鉴 StarFree 15 色 → 精选 10 组）============
    object Gradient {
        /** 主色渐变：跟随主题（薄荷绿系） */
        val primary: Brush
            @Composable @ReadOnlyComposable
            get() = Brush.linearGradient(listOf(Color(0xFF44E9BD), Color(0xFF3CC9A4)))

        /** 绿：工具/实用 */
        val green = Brush.linearGradient(listOf(Color(0xFFAAFF7F), Color(0xFF00AE4B)))
        /** 蓝：应用/资源 */
        val blue = Brush.linearGradient(listOf(Color(0xFFAAFFFF), Color(0xFF89ADFF)))
        /** 紫：内容/频道 */
        val purple = Brush.linearGradient(listOf(Color(0xFFAAAAFF), Color(0xFF811AFF)))
        /** 橙：活动/签到 */
        val orange = Brush.linearGradient(listOf(Color(0xFFFFD198), Color(0xFFFF5C10)))
        /** 青绿：数据/等级 */
        val mint = Brush.linearGradient(listOf(Color(0xFFACFFCC), Color(0xFF47BF7F)))
        /** 湖蓝：搜索/发现 */
        val lake = Brush.linearGradient(listOf(Color(0xFFAEFFFD), Color(0xFF2182BF)))
        /** 红橙：警示/热门 */
        val fire = Brush.linearGradient(listOf(Color(0xFFFF9326), Color(0xFFDF5557)))
        /** 桃粉：社交/互动 */
        val pink = Brush.linearGradient(listOf(Color(0xFFFFB3D9), Color(0xFFE8508C)))
        /** 深灰：设置/系统 */
        val slate = Brush.linearGradient(listOf(Color(0xFF939393), Color(0xFF414141)))

        /** VIP 金粉紫三色（StarFree 核心视觉符号，用了 14 次） */
        val vip = Brush.linearGradient(listOf(Color(0xFFF2AD5C), Color(0xFFE6216D), Color(0xFF901CCB)))

        /** 卡片顶部微光 */
        val cardSheen = Brush.verticalGradient(
            listOf(Color(0x0A000000), Color(0x00000000))
        )

        /** 从任意色生成同族渐变 */
        fun of(c: Color) = Brush.linearGradient(listOf(c.lighten(0.18f), c))
    }

    // ============ 阴影体系（原本几乎全平 → 4 档）============
    object Elevation {
        /** 无阴影 */
        val none = 0.dp
        /** 轻微：分割、小标签 */
        val xs: Dp = 1.dp
        /** 卡片标准阴影 */
        val card: Dp = 3.dp
        /** 悬浮元素（FAB、底部条） */
        val float: Dp = 8.dp
        /** 弹窗 */
        val dialog: Dp = 16.dp
    }

    // ============ 语义色 ============
    object Colors {
        val vipGold = Color(0xFFFFB300)
        val success = Color(0xFF34C759)
        val warning = Color(0xFFFF9500)
        val danger = Color(0xFFFF3B30)
        val info = Color(0xFF007AFF)
        /** 点赞红 */
        val like = Color(0xFFF06292)
        /** 收藏黄 */
        val star = Color(0xFFFFC107)
    }
}

/** Color 变亮/变暗工具 */
fun Color.lighten(f: Float): Color = Color(
    red = (red + (1f - red) * f).coerceIn(0f, 1f),
    green = (green + (1f - green) * f).coerceIn(0f, 1f),
    blue = (blue + (1f - blue) * f).coerceIn(0f, 1f),
    alpha = alpha,
)

fun Color.darken(f: Float): Color = Color(
    red = (red * (1f - f)).coerceIn(0f, 1f),
    green = (green * (1f - f)).coerceIn(0f, 1f),
    blue = (blue * (1f - f)).coerceIn(0f, 1f),
    alpha = alpha,
)

/** 标准卡片修饰符：圆角 md + 软阴影 + 背景色 */
@Composable
fun Modifier.dsCard(
    radius: Dp = DS.Radius.md,
    elevation: Dp = DS.Elevation.card,
): Modifier {
    val bg = androidx.compose.material3.MaterialTheme.colorScheme.surface
    return this
        .shadow(elevation, RoundedCornerShape(radius), clip = false)
        .clip(RoundedCornerShape(radius))
        .background(bg)
}

/** 渐变卡片 */
@Composable
fun Modifier.dsGradientCard(
    brush: Brush,
    radius: Dp = DS.Radius.md,
    elevation: Dp = DS.Elevation.card,
): Modifier {
    return this
        .shadow(elevation, RoundedCornerShape(radius), clip = false)
        .clip(RoundedCornerShape(radius))
        .background(brush)
}