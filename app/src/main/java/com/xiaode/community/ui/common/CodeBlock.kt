package com.xiaode.community.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 代码块支持的语言（key 存后端，label 展示） */
val CODE_LANGS: List<Pair<String, String>> = listOf(
    "js" to "JavaScript",
    "ts" to "TypeScript",
    "php" to "PHP",
    "web" to "HTML/Web",
    "html" to "HTML",
    "css" to "CSS",
    "python" to "Python",
    "java" to "Java",
    "kotlin" to "Kotlin",
    "c" to "C",
    "cpp" to "C++",
    "csharp" to "C#",
    "go" to "Go",
    "rust" to "Rust",
    "sql" to "SQL",
    "json" to "JSON",
    "xml" to "XML",
    "bash" to "Shell",
    "text" to "纯文本",
)

fun langLabel(key: String): String =
    CODE_LANGS.firstOrNull { it.first == key.lowercase() }?.second ?: key.ifBlank { "代码" }

/**
 * 只读代码块：深色框 + 右上角语言标签 + 复制按钮。
 */
@Composable
fun CodeBlock(code: String, lang: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1E2430)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                langLabel(lang),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.onSurface)
                    .clickableNoRipple { copyToClipboard(ctx, code) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ContentCopy, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("复制", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
        Text(
            text = code,
            color = Color(0xFFE6EAF2),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }
}

private fun copyToClipboard(ctx: Context, text: String) {
    try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("code", text))
        Toast.makeText(ctx, "已复制代码", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
    }
}