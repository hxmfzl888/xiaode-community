package com.xiaode.community.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xiaode.community.data.ApiClient
import com.xiaode.community.ui.theme.BrandBlue
import com.xiaode.community.ui.theme.VipGold

/** 头像（带等级光环 + 会员角标） */
@Composable
fun XiaodeAvatar(
    url: String?,
    size: Dp = 40.dp,
    levelEffect: String = "normal",
    isVip: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val ringColor = when (levelEffect) {
        "aurora" -> Color(0xFF00E5FF)
        "gold" -> Color(0xFFFFC107)
        "blue" -> BrandBlue
        else -> Color.Transparent
    }
    Box(modifier = if (onClick != null) Modifier.clickableNoRipple(onClick) else Modifier) {
        Box(
            modifier = Modifier
                .size(size + if (ringColor != Color.Transparent) 4.dp else 0.dp)
                .then(
                    if (ringColor != Color.Transparent)
                        Modifier.border(2.dp, ringColor, CircleShape) else Modifier
                )
                .padding(2.dp),
        ) {
            if (url.isNullOrBlank()) {
                Box(
                    Modifier.size(size).clip(CircleShape).background(BrandBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Text("德", color = BrandBlue, fontWeight = FontWeight.Bold) }
            } else {
                AsyncImage(
                    model = ApiClient.absUrl(url),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size).clip(CircleShape),
                )
            }
        }
        if (isVip) {
            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = "大会员",
                tint = VipGold,
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

/** 等级小徽章 Lv.x */
@Composable
fun LevelBadge(level: Int, effect: String = "normal") {
    val bg = when (effect) {
        "aurora" -> Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF)))
        "gold" -> Brush.horizontalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF8F00)))
        "blue" -> Brush.horizontalGradient(listOf(Color(0xFF64B5F6), Color(0xFF1976D2)))
        else -> Brush.horizontalGradient(listOf(Color(0xFFB0BEC5), Color(0xFF78909C)))
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text("Lv.$level", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
/** 大会员金标 —— 金粉紫三色渐变（StarFree 核心视觉） */
@Composable
fun VipBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFF2AD5C), Color(0xFFE6216D), Color(0xFF901CCB))
                )
            )
            .padding(horizontal = 6.dp, vertical = 1.5.dp),
    ) {
        Text(
            "大会员",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        )
    }
}

/** 红色数字圆点 */
@Composable
fun DotBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .clip(CircleShape)
            .background(Color(0xFFF44336))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** 无涟漪点击（Compose 基础库没有 clickableNoRipple，这里自己包一下） */
@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectTapGestures(onTap = { onClick() })
    }

// 把 clickableNoRipple 暴露给外部（同一个 Modifier 扩展，重复定义会有歧义，所以用别名）
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectTapGestures(onTap = { onClick() })
    }

/** 通用"加载中" */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
            Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

/** 通用空态 */
@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}