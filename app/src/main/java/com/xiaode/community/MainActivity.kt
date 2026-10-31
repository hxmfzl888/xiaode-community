package com.xiaode.community
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.xiaode.community.ui.XiaodeRoot
import com.xiaode.community.ui.splash.SplashAdScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 开屏广告：先showSplash=true，广告播完/跳过/无广告时置false
            var showSplash by remember { mutableStateOf(true) }
            if (showSplash) {
                SplashAdScreen(onFinish = { showSplash = false })
            } else {
                XiaodeRoot()
            }
        }
    }
}