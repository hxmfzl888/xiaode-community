package com.xiaode.community.ui.auth

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.xiaode.community.XiaodeApp
import com.xiaode.community.data.Repo
import com.xiaode.community.data.safeCall
import kotlinx.coroutines.launch

/**
 * 第三方登录回跳页（`xiaode://auth?...`）。
 *
 * 场景：其它端（如《战壕风云1917》游戏）需要用小德社区账号登录时，
 * 唤起本页 → 校验本地登录态 → 调 `/api/game/issue_token` 换取游戏 token
 * → 通过 result 回传给调用方。
 *
 * 支持的 query：
 *   - callback : 回跳 scheme（默认 game://login）
 */
class ThirdPartyAuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent?.data
        val callback = uri?.getQueryParameter("callback") ?: "xiaode://auth/result"

        if (!XiaodeApp.store.isLoggedIn()) {
            Toast.makeText(this, "请先在小德社区登录", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        lifecycleScope.launch {
            when (val r = safeCall { Repo.api.issueGameToken() }) {
                is com.xiaode.community.data.ApiResult.Ok -> {
                    val data = r.data
                    if (data.code == 0 && data.data != null) {
                        Toast.makeText(this@ThirdPartyAuthActivity, "授权成功", Toast.LENGTH_SHORT).show()
                        val ret = Uri.parse(callback)
                            .buildUpon()
                            .appendQueryParameter("token", data.data.token)
                            .appendQueryParameter("uid", data.data.player?.uid?.toString() ?: "")
                            .appendQueryParameter("nickname", data.data.player?.nickname ?: "")
                            .build()
                        val i = android.content.Intent(android.content.Intent.ACTION_VIEW, ret)
                        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        try { startActivity(i) } catch (_: Exception) { }
                    } else {
                        Toast.makeText(this@ThirdPartyAuthActivity, data.msg.ifBlank { "授权失败" }, Toast.LENGTH_LONG).show()
                    }
                }
                is com.xiaode.community.data.ApiResult.Err -> {
                    Toast.makeText(this@ThirdPartyAuthActivity, r.message, Toast.LENGTH_LONG).show()
                }
            }
            finish()
        }
    }
}