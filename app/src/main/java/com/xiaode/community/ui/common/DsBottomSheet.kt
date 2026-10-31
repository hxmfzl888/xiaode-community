package com.xiaode.community.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaode.community.ui.theme.DS
import com.xiaode.community.ui.theme.lighten

/**
 * 统一底部弹窗 BottomSheet
 * 全站分享 / 举报 / 排序 / 选择 都用这个，风格一致
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DsBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String = "",
    subtitle: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = DS.Radius.lg, topEnd = DS.Radius.lg),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(36.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)),
            )
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = DS.Space.lg)) {
            if (title.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = DS.Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            fontSize = DS.Text.title,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                subtitle,
                                fontSize = DS.Text.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            content()
            Spacer(Modifier.height(DS.Space.xl))
        }
    }
}

/** 底部弹窗里的操作项（图标 + 文字 + 可选副标题） */
@Composable
fun SheetAction(
    icon: ImageVector,
    label: String,
    description: String = "",
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val c = tint ?: MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DS.Radius.md))
            .clickable(onClick = onClick)
            .padding(horizontal = DS.Space.sm, vertical = DS.Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(DS.Radius.sm))
                .background(c.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(DS.Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = DS.Text.body1,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    fontSize = DS.Text.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 底部弹窗里的操作项（渐变图标版 —— 用于分享渠道） */
@Composable
fun SheetGradientAction(
    icon: ImageVector,
    label: String,
    brush: androidx.compose.ui.graphics.Brush,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(DS.Radius.md))
            .clickable(onClick = onClick)
            .padding(vertical = DS.Space.sm, horizontal = DS.Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(DS.Radius.md))
                .background(brush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(DS.Space.xs))
        Text(
            label,
            fontSize = DS.Text.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 底部弹窗里的单选列表 */
@Composable
fun SheetRadioRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DS.Radius.sm))
            .clickable(onClick = onClick)
            .padding(vertical = DS.Space.sm, horizontal = DS.Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text,
            fontSize = DS.Text.body,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}