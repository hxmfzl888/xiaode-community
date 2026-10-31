package com.xiaode.community.ui.theme
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.xiaode.community.XiaodeApp

val BrandBlue = Color(0xFF7C5CFF)
val BrandDark = Color(0xFF1B2545)
val VipGold = Color(0xFFFFB300)

// ===== StarFree 风格色板 =====
val SFMint = Color(0xFF3CC9A4)        // 主色：薄荷绿
val SFMintLight = Color(0xFF6BDDBE)   // 浅薄荷
val SFMintDark = Color(0xFF2BAF8C)    // 深薄荷
val SFBg = Color(0xFFF7F8FA)          // 页面底色：极浅灰
val SFCard = Color(0xFFFFFFFF)        // 卡片白
val SFTextMain = Color(0xFF1F2937)    // 主文字
val SFTextSub = Color(0xFF9CA3AF)     // 次文字
val SFDivider = Color(0xFFF0F1F3)     // 分割线
val SFTagBg = Color(0xFFF0FBF7)       // 标签底色
val SFTagText = Color(0xFF3CC9A4)     // 标签文字

data class ThemePreset(val key: String, val label: String, val color: Color)
val THEME_PRESETS = listOf(
    ThemePreset("mint", "薄荷（默认）", Color(0xFF3CC9A4)),
    ThemePreset("teal", "青碧", Color(0xFF00A3A3)),
    ThemePreset("indigo", "紫罗兰", Color(0xFF7C5CFF)),
    ThemePreset("green", "森绿", Color(0xFF2E9E5B)),
    ThemePreset("purple", "深紫", Color(0xFF6C4CE0)),
    ThemePreset("orange", "暖橙", Color(0xFFFF7A29)),
    ThemePreset("pink", "桃粉", Color(0xFFE8508C)),
    ThemePreset("red", "朱红", Color(0xFFE53935)),
    ThemePreset("graphite", "石墨", Color(0xFF4A5568)),
)
object ThemeState {
    var presetKey by mutableStateOf(XiaodeApp.store.themePreset)
    var darkMode by mutableStateOf(XiaodeApp.store.themeDark)
    var navAlpha by mutableStateOf(XiaodeApp.store.navBarAlpha)
    var navCapsule by mutableStateOf(XiaodeApp.store.navBarCapsule)
    var revision by mutableStateOf(0)
    fun applyTheme(key: String) { presetKey = key; XiaodeApp.store.themePreset = key; revision++ }
    fun applyDark(mode: Int) { darkMode = mode; XiaodeApp.store.themeDark = mode; revision++ }
    fun applyNavAlpha(v: Float) { navAlpha = v; XiaodeApp.store.navBarAlpha = v; revision++ }
    fun applyNavCapsule(v: Boolean) { navCapsule = v; XiaodeApp.store.navBarCapsule = v; revision++ }
    val current: ThemePreset get() = THEME_PRESETS.firstOrNull { it.key == presetKey } ?: THEME_PRESETS[0]
}
@Composable
fun XiaodeTheme(
    darkTheme: Boolean = when (ThemeState.darkMode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() },
    content: @Composable () -> Unit,
) {
    val _rev = ThemeState.revision
    val primary = ThemeState.current.color
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = SFTextSub,
            background = Color(0xFF12151A),
            surface = Color(0xFF1A1E25),
            surfaceVariant = Color(0xFF232830),
            onSurface = Color(0xFFEDEFF5),
            onSurfaceVariant = Color(0xFF8A93A3),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = SFTextSub,
            background = SFBg,
            surface = SFCard,
            surfaceVariant = SFTagBg,
            onSurface = SFTextMain,
            onSurfaceVariant = SFTextSub,
        )
    }
    val view = LocalView.current
    if (view.isInEditMode == false) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = (darkTheme == false)
        }
    }
    // StarFree 风格：更圆润
    val appShapes = androidx.compose.material3.Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    )
    MaterialTheme(colorScheme = colors, shapes = appShapes, content = content)
}