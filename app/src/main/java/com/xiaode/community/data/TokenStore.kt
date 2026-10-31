package com.xiaode.community.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 登录态 & 本地缓存持久化。
 * 多账号支持：账号列表 + 当前账号 token，满足"可同时登录无数个账号"。
 */
class TokenStore(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("xiaode_store", Context.MODE_PRIVATE)

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    // 当前登录用户信息（json）
    var currentUserJson: String?
        get() = sp.getString("current_user", null)
        set(v) = sp.edit().putString("current_user", v).apply()

    // 已登录账号列表（json 数组，记录 user+token），支持多账号切换
    var accountListJson: String?
        get() = sp.getString("account_list", null)
        set(v) = sp.edit().putString("account_list", v).apply()

    // 会员背景（云端地址缓存）
    var vipBg: String
        get() = sp.getString("vip_bg", "").orEmpty()
        set(v) = sp.edit().putString("vip_bg", v).apply()

    // 是否开启图片自动缓存（聊天图片）
    var imageCacheEnabled: Boolean
        get() = sp.getBoolean("img_cache", true)
        set(v) = sp.edit().putBoolean("img_cache", v).apply()

    // 上次同步的会话/好友时间戳（用于红点轮询）
    var lastSync: Long
        get() = sp.getLong("last_sync", 0L)
        set(v) = sp.edit().putLong("last_sync", v).apply()

    var themePreset: String
        get() = sp.getString("theme_preset", "indigo") ?: "indigo"
        set(v) = sp.edit().putString("theme_preset", v).apply()

    var themeDark: Int
        get() = sp.getInt("theme_dark", 0)
        set(v) = sp.edit().putInt("theme_dark", v).apply()

    // navBar alpha (0.35~1.0), default 0.72 (frosted floating)
    var navBarAlpha: Float
        get() = sp.getFloat("nav_bar_alpha", 0.72f)
        set(v) = sp.edit().putFloat("nav_bar_alpha", v.coerceIn(0.35f, 1f)).apply()

    // navbar capsule floating style
    var navBarCapsule: Boolean
        get() = sp.getBoolean("nav_bar_capsule", true)
        set(v) = sp.edit().putBoolean("nav_bar_capsule", v).apply()

    fun isLoggedIn(): Boolean = !token.isNullOrEmpty()

    fun clear() {
        sp.edit().remove("token").remove("current_user").apply()
    }
}