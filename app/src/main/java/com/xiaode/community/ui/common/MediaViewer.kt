package com.xiaode.community.ui.common

import android.app.Activity
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.xiaode.community.data.ApiClient
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/** 全屏图片查看器：左右滑动切换、双指缩放、点击关闭 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerDialog(
    images: List<String>,
    startIndex: Int = 0,
    onDismiss: () -> Unit,
    allowDownload: Boolean = true,
) {
    if (images.isEmpty()) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var saveIndex by remember { mutableStateOf(-1) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, images.size - 1)) { images.size }
        // SHARED_TRANSITION: 打开瞬间 缩放+淡入，模拟列表图 → 全屏的共享元素过渡
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        val scaleAnim by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (entered) 1f else 0.82f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = 0.78f,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            ),
            label = "viewerScale",
        )
        val alphaAnim by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(180),
            label = "viewerAlpha",
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scaleAnim,
                    scaleY = scaleAnim,
                    alpha = alphaAnim,
                )
                .background(Color.Black)
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(
                    url = ApiClient.absUrl(images[page]),
                    allowDownload = allowDownload,
                    onRequestSave = { saveIndex = page },
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(Icons.Filled.Close, "关闭", tint = Color.White)
            }
            if (images.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${images.size}",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                )
            }
        }
        if (saveIndex >= 0) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { saveIndex = -1 },
                title = { androidx.compose.material3.Text("保存图片") },
                text = { androidx.compose.material3.Text("是否保存这张图片到相册？") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        saveImageToGallery(ctx, images[saveIndex])
                        saveIndex = -1
                    }) { androidx.compose.material3.Text("保存") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { saveIndex = -1 }) {
                        androidx.compose.material3.Text("取消")
                    }
                },
            )
        }
    }
}

/** 可缩放图片 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImage(url: String, allowDownload: Boolean, onRequestSave: () -> Unit = {}) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = com.xiaode.community.data.ApiClient.absUrl(url),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offX, translationY = offY)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { if (allowDownload) onRequestSave() },
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) { offX += pan.x; offY += pan.y } else { offX = 0f; offY = 0f }
                    }
                },
        )

    }
}

/** 把网络图片保存到相册（MediaStore，无需存储权限） */
private fun saveImageToGallery(ctx: android.content.Context, url: String) {
    Thread {
        try {
            val conn = java.net.URL(url).openConnection()
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            val bytes = conn.getInputStream().use { it.readBytes() }
            val name = "xiaode_" + System.currentTimeMillis() + ".jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/小德社区")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let { ctx.contentResolver.openOutputStream(it)?.use { os -> os.write(bytes) } }
            (ctx as? Activity)?.runOnUiThread { Toast.makeText(ctx, "已保存到相册", Toast.LENGTH_SHORT).show() }
        } catch (e: Throwable) {
            (ctx as? Activity)?.runOnUiThread { Toast.makeText(ctx, "保存失败", Toast.LENGTH_SHORT).show() }
        }
    }.start()
}

/** 全屏视频播放器：横屏 + 系统控件 */
@Composable
fun VideoViewerDialog(url: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { c ->
                    android.widget.VideoView(c).apply {
                        setVideoURI(android.net.Uri.parse(ApiClient.absUrl(url)))
                        setOnPreparedListener { it.isLooping = false; start() }
                        setMediaController(android.widget.MediaController(c).apply { setAnchorView(this@apply) })
                        setOnCompletionListener { }
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(Icons.Filled.Close, "关闭", tint = Color.White)
            }
        }
    }
}
