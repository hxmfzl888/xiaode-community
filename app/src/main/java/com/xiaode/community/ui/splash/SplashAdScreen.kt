package com.xiaode.community.ui.splash

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xiaode.community.data.ApiClient
import com.xiaode.community.data.Repo
import com.xiaode.community.data.SplashAdOut
import com.xiaode.community.data.safeCall
import kotlinx.coroutines.delay

/**
 * 开屏广告页
 * - 全屏展示后台配置的广告图
 * - 右上角「跳过 N 秒」倒计时，到 0 自动进入 App
 * - 点击广告跳系统浏览器（外部）
 */
@Composable
fun SplashAdScreen(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    var ad by remember { mutableStateOf<SplashAdOut?>(null) }
    var skipLeft by remember { mutableIntStateOf(3) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val r = safeCall { Repo.api.getSplash() }
        val a = when (r) {
            is com.xiaode.community.data.ApiResult.Ok -> r.data.ad
            is com.xiaode.community.data.ApiResult.Err -> null
        }
        if (a == null || a.image.isBlank()) {
            onFinish()
            return@LaunchedEffect
        }
        ad = a
        skipLeft = a.skipSeconds.coerceIn(0, 15)
        started = true
    }

    LaunchedEffect(started, skipLeft) {
        if (!started) return@LaunchedEffect
        while (skipLeft > 0) {
            delay(1000)
            skipLeft -= 1
        }
        if (skipLeft <= 0) onFinish()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF10131C))) {
        val a = ad
        if (a != null) {
            AsyncImage(
                model = ApiClient.absUrl(a.image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable {
                    val link = a.link.trim()
                    if (link.isNotEmpty()) {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Throwable) {
                        }
                    }
                    onFinish()
                },
            )

            Box(
                Modifier.align(Alignment.TopEnd)
                    .padding(top = 44.dp, end = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x66000000))
                    .clickable { onFinish() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    if (skipLeft > 0) "跳过 " + skipLeft else "跳过",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x99000000), Color(0xCC000000))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("小德社区", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("发现有趣的人和事", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}