package com.xiaode.community.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaode.community.ui.theme.DS

/**
 * 表情面板（OwO 风格）
 * 分类 → 点击插入到输入框光标处
 */
object EmojiData {
    /** 常用表情 */
    val face = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "😉",
        "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😋", "😛",
        "😜", "🤪", "😝", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐",
        "😑", "😶", "😏", "😒", "🙄", "😬", "🤥", "😌", "😔", "😪",
        "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🥵", "🥶", "😵",
        "🤯", "🤠", "🥳", "😎", "🤓", "🧐", "😕", "😟", "🙁", "😮",
        "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢",
        "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤",
    )

    /** 手势 */
    val hand = listOf(
        "👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙",
        "👈", "👉", "👆", "👇", "☝️", "👋", "🤚", "🖐️", "✋", "🖖",
        "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💪", "🦾", "🫶",
    )

    /** 心情符号 */
    val symbol = listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
        "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "✨", "🌟",
        "⭐", "💫", "🔥", "💥", "💯", "🎉", "🎊", "🎈", "🎁", "🏆",
        "✅", "❌", "⭕", "❗", "❓", "💤", "💦", "💨", "👑", "💎",
    )

    /** 食物动物等 */
    val other = listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
        "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍒", "🍑", "🍍",
        "☕", "🍺", "🍻", "🥂", "🍰", "🎂", "🍕", "🍔", "🍟", "🍜",
        "⚽", "🏀", "🎮", "🎯", "🎵", "🎶", "📱", "💻", "⌚", "🎧",
    )
}

/**
 * 表情选择面板
 * @param onPick 选中某个表情的回调
 */
@Composable
fun EmojiPanel(
    modifier: Modifier = Modifier,
    categories: List<Pair<String, List<String>>> = listOf(
        "笑脸" to EmojiData.face,
        "手势" to EmojiData.hand,
        "符号" to EmojiData.symbol,
        "其他" to EmojiData.other,
    ),
    onPick: (String) -> Unit,
) {
    var tab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val current = categories.getOrNull(tab)?.second ?: emptyList()

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = DS.Space.md, vertical = DS.Space.sm),
    ) {
        // 分类 Tab
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DS.Space.xs)) {
            categories.forEachIndexed { i, (name, _) ->
                val sel = i == tab
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(DS.Radius.pill))
                        .background(
                            if (sel) DS.Gradient.primary
                            else androidx.compose.ui.graphics.SolidColor(
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        .clickable { tab = i }
                        .padding(vertical = DS.Space.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name,
                        fontSize = DS.Text.body2,
                        color = if (sel) androidx.compose.ui.graphics.Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (sel) androidx.compose.ui.text.font.FontWeight.Medium
                        else androidx.compose.ui.text.font.FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(DS.Space.sm))

        // 表情网格
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.fillMaxWidth().height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(current) { e ->
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(DS.Radius.xs))
                        .clickable { onPick(e) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(e, fontSize = 22.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}