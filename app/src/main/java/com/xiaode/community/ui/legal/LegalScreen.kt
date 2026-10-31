package com.xiaode.community.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaode.community.ui.theme.BrandBlue

/** 协议类型 */
enum class LegalType(val title: String) {
    PRIVACY("用户隐私政策"),
    DISCLAIMER("免责声明"),
}

/** 协议全文阅读页（可滚动） */
@OptIn(ExperimentalMaterial3Api::class)
// SF_POLISH_ALL: StarFree 风格统一
@Composable
fun LegalScreen(onBack: () -> Unit, type: LegalType) {
    val content = when (type) {
        LegalType.PRIVACY -> Legal.PRIVACY_POLICY
        LegalType.DISCLAIMER -> Legal.DISCLAIMER
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(type.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).padding(18.dp),
        ) {
            Text(content, fontSize = 14.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(30.dp))
            Text(
                "如对内容有疑问，请联系：${Legal.CONTACT_EMAIL}",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

/**
 * 同意勾选框组件（登录/注册页使用）。
 * 文案："我已阅读并同意《用户隐私政策》和《免责声明》"
 * 《用户隐私政策》《免责声明》高亮为可点击的蓝字。
 */
@Composable
fun AgreementCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDisclaimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(2.dp))

        val annotated = buildAnnotatedString {
            append(Legal.AGREEMENT_PREFIX)
            pushStringAnnotation("privacy", "privacy")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                append(Legal.PRIVACY_TITLE)
            }
            pop()
            append("和")
            pushStringAnnotation("disclaimer", "disclaimer")
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                append(Legal.DISCLAIMER_TITLE)
            }
            pop()
        }

        ClickableText(
            text = annotated,
            style = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
            onClick = { offset ->
                annotated.getStringAnnotations("privacy", offset, offset).firstOrNull()?.let {
                    onOpenPrivacy()
                }
                annotated.getStringAnnotations("disclaimer", offset, offset).firstOrNull()?.let {
                    onOpenDisclaimer()
                }
            },
        )
    }
}

/**
 * 未勾选时的提示条（点击"登录/注册"但没勾选时弹出）。
 * 也可用 Snackbar，这里给一个简单文字提示样式。
 */
val NOT_AGREED_HINT = "请先阅读并同意《用户隐私政策》和《免责声明》"