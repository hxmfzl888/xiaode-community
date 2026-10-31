package com.xiaode.community.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * 通用下拉刷新容器（重写版 v9.7）。
 *
 * 核心修复：
 *  - 用 dragging 标志区分「用户主动下拉」与「列表惯性滑动」，杜绝松手后自动刷新/自动弹回；
 *  - 松手（onPostFling）后只做一次定格决策，之后不再自动改动头部高度；
 *  - 头部「已展开」时再次下拉超阈值才触发 onRefresh。
 *
 * @param enabled       头部是否已经完全展开（展开后才允许再次下拉触发刷新）
 * @param headerMaxPx   头部完全展开对应的高度（px）
 * @param refreshing    是否正在刷新
 * @param onSettle      松手定格回调：0f=收起，headerMaxPx=完全展开
 * @param onPullChange  下拉中实时回调（px）
 */
@Composable
fun PullRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = false,
    headerMaxPx: Float = 0f,
    onPullChange: ((Float) -> Unit)? = null,
    onSettle: ((Float) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 56.dp.toPx() }
    val pullState = remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val enabledNow by rememberUpdatedState(enabled)
    val headerMaxNow by rememberUpdatedState(headerMaxPx)
    val onPullChangeNow by rememberUpdatedState(onPullChange)
    val onSettleNow by rememberUpdatedState(onSettle)
    val onRefreshNow by rememberUpdatedState(onRefresh)

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y < 0f && pullState.floatValue > 0f) {
                    val consumed = available.y.coerceAtLeast(-pullState.floatValue)
                    pullState.floatValue = (pullState.floatValue + consumed).coerceAtLeast(0f)
                    onPullChangeNow?.invoke(pullState.floatValue)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f) return Offset.Zero
                if (source != NestedScrollSource.Drag) return Offset.Zero
                dragging = true
                val limit = if (headerMaxNow > 0f && headerMaxNow < Float.MAX_VALUE) headerMaxNow else thresholdPx * 3.2f
                pullState.floatValue = (pullState.floatValue + available.y * 0.5f).coerceAtMost(limit)
                onPullChangeNow?.invoke(pullState.floatValue)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!dragging) return Velocity.Zero
                dragging = false
                val pulled = pullState.floatValue
                if (pulled > 0f) {
                    if (!enabledNow) {
                        val half = headerMaxNow / 2f
                        onSettleNow?.invoke(if (pulled >= half) headerMaxNow else 0f)
                    } else {
                        if (pulled >= thresholdPx) {
                            onRefreshNow()
                        }
                        onSettleNow?.invoke(headerMaxNow)
                    }
                }
                pullState.floatValue = 0f
                return Velocity.Zero
            }
        }
    }

    Box(modifier.fillMaxWidth().nestedScroll(connection)) {
        content()
        if (refreshing) {
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.height(22.dp).fillMaxWidth(0.06f), strokeWidth = 2.dp)
            }
        }
    }
}
