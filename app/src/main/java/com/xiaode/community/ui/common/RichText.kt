package com.xiaode.community.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * 轻量富文本渲染器（v2.0.1）。
 *
 * 支持的标记：
 *  - **加粗**
 *  - *斜体*
 *  - <u>下划线</u>
 *  - [color=#RRGGBB]彩色文字[/color]（也支持 [color=red] 等）
 *
 * 解析优先级：颜色 > 下划线 > 加粗 > 斜体。未匹配的标记会原样保留。
 * 用于帖子内容在详情页/卡片中的真实渲染，让发帖工具栏“真的有效果”。
 */
object RichText {
    // 常用颜色名 → 色值
    private val NAMED = mapOf(
        "red" to Color(0xFFE53935),
        "blue" to Color(0xFF1E88E5),
        "green" to Color(0xFF43A047),
        "orange" to Color(0xFFFB8C00),
        "purple" to Color(0xFF8E24AA),
        "pink" to Color(0xFFD81B60),
        "brown" to Color(0xFF6D4C41),
        "gray" to Color(0xFF757575),
        "black" to Color(0xFF000000),
    )

    private fun parseColor(v: String): Color? {
        val s = v.trim()
        NAMED[s.lowercase()]?.let { return it }
        if (s.startsWith("#")) {
            return try {
                val hex = s.substring(1)
                val full = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
                val c = full.toLong(16)
                if (full.length == 6) Color((0xFF000000L or c).toInt())
                else Color(c.toInt())
            } catch (_: Throwable) { null }
        }
        return null
    }

    /** 把带标记的文本转为 AnnotatedString（无标记时就是纯文本） */
    fun render(text: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        var bold = false
        var italic = false
        var underline = false
        val colorStack = ArrayDeque<Color?>()
        fun curColor(): Color? = colorStack.lastOrNull()
        while (i < text.length) {
            // [color=xxx]
            if (text.startsWith("[color=", i)) {
                val end = text.indexOf(']', i)
                if (end > 0) {
                    val v = text.substring(i + 7, end)
                    val c = parseColor(v)
                    if (c != null) { colorStack.addLast(c); i = end + 1; continue }
                }
            }
            if (text.startsWith("[/color]", i)) {
                if (colorStack.isNotEmpty()) colorStack.removeLast()
                i += 8; continue
            }
            if (text.startsWith("<u>", i)) { underline = true; i += 3; continue }
            if (text.startsWith("</u>", i)) { underline = false; i += 4; continue }
            if (text.startsWith("**", i)) { bold = !bold; i += 2; continue }
            if (text.startsWith("*", i)) { italic = !italic; i += 1; continue }
            // 普通字符
            val ch = text[i].toString()
            withStyle(
                SpanStyle(
                    fontWeight = if (bold) FontWeight.Bold else null,
                    fontStyle = if (italic) FontStyle.Italic else null,
                    textDecoration = if (underline) TextDecoration.Underline else null,
                    color = curColor() ?: Color.Unspecified,
                )
            ) { append(ch) }
            i += 1
        }
    }
}
