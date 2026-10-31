package com.xiaode.community.ui.auth

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaode.community.MainActivity
import com.xiaode.community.R
import com.xiaode.community.ui.theme.XiaodeTheme
import com.xiaode.community.ui.theme.BrandBlue
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {

    /** 用 BitmapFactory 直接解码图标位图，不依赖 Compose 资源系统 */
    private fun loadLogo(): ImageBitmap? = try {
        val id = R.mipmap.ic_launcher_foreground
        BitmapFactory.decodeResource(resources, id)?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val logo = loadLogo()
        setContent {
            XiaodeTheme {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // 顶部标语
                        Text("快乐马上出现", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = com.xiaode.community.ui.theme.BrandBlue)
                        Spacer(Modifier.height(40.dp))
                        // 下方：图标 + 名字
                        if (logo != null) {
                            Image(
                                bitmap = logo,
                                contentDescription = "logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(110.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("小德社区", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text("景聚智途", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LaunchedEffect(Unit) {
                    delay(1200)
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            }
        }
    }
}
