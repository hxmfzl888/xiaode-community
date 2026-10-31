package com.xiaode.community.ui.common

import android.content.Context
import android.graphics.Color as AColor
import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.graphics.Typeface
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 富文本工具：把"所见即所得"的原生 EditText 中的 Spannable 与后端标记互转。
 *
 * 标记格式（与 RichText.render 解析器保持一致）：
 *  - 加粗：**文本**
 *  - 斜体：*文本*
 *  - 下划线：<u>文本</u>
 *  - 颜色：[color=red]文本[/color]（支持 red/blue/green/orange/purple/pink/brown/gray/black 及 #RRGGBB）
 */
object RichFormat {
    const val BOLD = "**"
    const val ITALIC = "*"
    const val UNDERLINE_OPEN = "<u>"
    const val UNDERLINE_CLOSE = "</u>"
    const val COLOR_OPEN = "[color="
    const val COLOR_CLOSE = "[/color]"

    private val NAMED: Map<Int, String> = mapOf(
        AColor.parseColor("#F44336") to "red",
        AColor.parseColor("#2196F3") to "blue",
        AColor.parseColor("#4CAF50") to "green",
        AColor.parseColor("#FF9800") to "orange",
        AColor.parseColor("#9C27B0") to "purple",
        AColor.parseColor("#E91E63") to "pink",
        AColor.parseColor("#795548") to "brown",
        AColor.parseColor("#9E9E9E") to "gray",
        AColor.parseColor("#000000") to "black",
    )

    /** 颜色名 → argb。用于把标记里的颜色名映射回 span 颜色。 */
    fun nameToArgb(name: String): Int? {
        NAMED.entries.firstOrNull { it.value.equals(name, true) }?.let { return it.key }
        if (name.startsWith("#")) {
            return try { AColor.parseColor(name) } catch (_: Exception) { null }
        }
        return null
    }

    /** argb → 颜色名（优先命名色，否则 #RRGGBB）。 */
    fun argbToName(argb: Int): String {
        NAMED[argb]?.let { return it }
        return String.format("#%06X", 0xFFFFFF and argb)
    }

    /**
     * 把带样式的 Spannable 序列化成后端标记字符串。
     * 采用"从后往前插入"策略，避免插入导致的区间位移。
     */
    fun toMarkup(sp: Spanned): String {
        val sb = StringBuilder(sp.toString())
        data class Op(val at: Int, val text: String, val isOpen: Boolean)
        val ops = ArrayList<Op>()

        for (span in sp.getSpans(0, sp.length, Any::class.java)) {
            val start = sp.getSpanStart(span)
            val end = sp.getSpanEnd(span)
            if (start < 0 || end <= start) continue
            when (span) {
                is UnderlineSpan -> {
                    ops.add(Op(start, UNDERLINE_OPEN, true))
                    ops.add(Op(end, UNDERLINE_CLOSE, false))
                }
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD -> {
                        ops.add(Op(start, BOLD, true))
                        ops.add(Op(end, BOLD, false))
                    }
                    Typeface.ITALIC -> {
                        ops.add(Op(start, ITALIC, true))
                        ops.add(Op(end, ITALIC, false))
                    }
                }
                is ForegroundColorSpan -> {
                    val name = argbToName(span.foregroundColor)
                    ops.add(Op(start, "$COLOR_OPEN$name]", true))
                    ops.add(Op(end, COLOR_CLOSE, false))
                }
                else -> {}
            }
        }
        // 位置相同时：闭合先插、开启后插，避免标签交叉（在相同位置，先插的出现在前面）
        ops.sortWith(compareBy({ it.at }, { it.isOpen }))
        // 从后往前插入
        for (op in ops.asReversed()) {
            val pos = op.at.coerceIn(0, sb.length)
            sb.insert(pos, op.text)
        }
        return sb.toString()
    }

    /** 把后端标记字符串解析成 Spannable（用于编辑旧帖/回填）。 */
    fun fromMarkup(text: String, baseColor: Int = AColor.BLACK): Spannable {
        val plain = StringBuilder()
        val styles = ArrayList<Triple<Int, Int, Any>>() // start,end,style-marker
        val colorStack = ArrayList<Pair<Int, Int>>()   // (start,argb)
        var underlineStart = -1
        var boldStart = -1
        var italicStart = -1

        var i = 0
        while (i < text.length) {
            when {
                text.startsWith(UNDERLINE_OPEN, i) -> { underlineStart = plain.length; i += UNDERLINE_OPEN.length }
                text.startsWith(UNDERLINE_CLOSE, i) -> {
                    if (underlineStart in 0 until plain.length) styles.add(Triple(underlineStart, plain.length, UnderlineSpan()))
                    underlineStart = -1; i += UNDERLINE_CLOSE.length
                }
                text.startsWith(COLOR_OPEN, i) -> {
                    val close = text.indexOf(']', i)
                    if (close > 0) {
                        val name = text.substring(i + COLOR_OPEN.length, close)
                        (nameToArgb(name) ?: baseColor).let { colorStack.add(plain.length to it) }
                        i = close + 1
                    } else { plain.append(text[i]); i++ }
                }
                text.startsWith(COLOR_CLOSE, i) -> {
                    colorStack.removeLastOrNull()?.let { (s, c) -> styles.add(Triple(s, plain.length, ForegroundColorSpan(c))) }
                    i += COLOR_CLOSE.length
                }
                text.startsWith(BOLD, i) -> {
                    if (boldStart < 0) boldStart = plain.length
                    else { styles.add(Triple(boldStart, plain.length, StyleSpan(Typeface.BOLD))); boldStart = -1 }
                    i += BOLD.length
                }
                text[i] == '*' -> {
                    if (italicStart < 0) italicStart = plain.length
                    else { styles.add(Triple(italicStart, plain.length, StyleSpan(Typeface.ITALIC))); italicStart = -1 }
                    i += 1
                }
                else -> { plain.append(text[i]); i++ }
            }
        }
        val sp = android.text.SpannableString(plain.toString()) as android.text.Spannable
        for ((s, e, st) in styles) {
            if (s in 0..e && e <= sp.length && s < e) sp.setSpan(st, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sp
    }
}

/**
 * 富文本编辑框：内部是原生 EditText + Spannable，真正所见即所得。
 *
 * 设计：EditText 自身持有内容与选区。用户输入 → TextWatcher → 回调给外部。
 * 外部若要回填（进入编辑页），通过 [initial] 仅在首次注入。
 *
 * @param initial 初始富文本（仅首次注入，之后由 EditText 自己维护）
 * @param onValueChange 内容变化回调（文本/样式变化都会触发）
 * @param onSelection 光标/选区变化回调：start, end
 * @param editorHolder 回传 EditText 实例（供工具栏直接改选区样式）
 */
@Composable
fun RichEditField(
    initial: Spannable,
    onValueChange: (Spannable) -> Unit,
    onSelection: (Int, Int) -> Unit,
    editorHolder: (EditText?) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    textSizeSp: Float = 16f,
) {
    AndroidView(
        modifier = modifier.fillMaxWidth().heightIn(min = 200.dp),
        factory = { ctx: Context ->
            SelTrackingEditText(ctx).apply {
                background = null
                setPadding(0, 0, 0, 0)
                isVerticalScrollBarEnabled = true
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setHint(hint)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                if (initial.isNotEmpty()) setText(initial)
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        s?.let { onValueChange(it as Spannable) }
                        LastSelection.update(selectionStart, selectionEnd)
                        onSelection(selectionStart, selectionEnd)
                    }
                })
            }
        },
        update = { et -> editorHolder(et) },
        onRelease = { editorHolder(null) },
    )
}

/** 缓存编辑器最后一次「有效选区」，供工具栏按钮使用（点按钮时 EditText 会失焦，选区会塌陷）。 */
object LastSelection {
    var start: Int = 0
    var end: Int = 0
    fun range(): Pair<Int, Int>? {
        val a = minOf(start, end); val b = maxOf(start, end)
        return if (b > a) (a to b) else null
    }
    fun update(s: Int, e: Int) {
        val a = minOf(s, e); val b = maxOf(s, e)
        if (b > a) { start = a; end = b }   // 只记录非空选区，避免失焦塌陷覆盖
    }
    fun clear() { start = 0; end = 0 }
}

/**
 * 实时追踪选区的 EditText。
 * 关键：onSelectionChanged 在选区变化时一定回调（选中文字不会触发 TextWatcher），
 * 这样点工具栏按钮时即使失焦，也能用 LastSelection 里保存的有效区间。
 */
class SelTrackingEditText(context: android.content.Context) : android.widget.EditText(context) {
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        LastSelection.update(selStart, selEnd)
    }
}
