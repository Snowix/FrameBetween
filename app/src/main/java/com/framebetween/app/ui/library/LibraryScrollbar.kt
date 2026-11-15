package com.framebetween.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class ScrollMetrics(val total: Int, val first: Int, val visible: Int)

/**
 * 媒体库右侧滚动滑块：文件夹 / 媒体条目多到一屏放不下时出现，随滚动定位，
 * 点击轨道或拖动可快速跳转。纯 Compose 绘制，不引第三方库，也不加任何动画。
 * [listState] / [gridState] 只需传当前布局实际使用的那一个。
 */
@Composable
fun LibraryScrollbarHost(
    listState: LazyListState? = null,
    gridState: LazyGridState? = null,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val metrics by remember(listState, gridState) {
        derivedStateOf {
            when {
                listState != null -> {
                    val info = listState.layoutInfo
                    ScrollMetrics(
                        info.totalItemsCount,
                        info.visibleItemsInfo.firstOrNull()?.index ?: 0,
                        info.visibleItemsInfo.size
                    )
                }
                gridState != null -> {
                    val info = gridState.layoutInfo
                    ScrollMetrics(
                        info.totalItemsCount,
                        info.visibleItemsInfo.firstOrNull()?.index ?: 0,
                        info.visibleItemsInfo.size
                    )
                }
                else -> ScrollMetrics(0, 0, 0)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        content()
        val total = metrics.total
        val visible = metrics.visible
        if (total > visible + 1 && visible > 0) {
            val density = LocalDensity.current
            val trackPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            val thumbFraction = (visible.toFloat() / total.toFloat()).coerceIn(0.06f, 1f)
            val progress = (metrics.first.toFloat() /
                (total - visible).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            val thumbPx = (trackPx * thumbFraction).coerceAtLeast(24f)
            val topPx = (trackPx - thumbPx).coerceAtLeast(0f) * progress

            fun seek(y: Float) {
                val target = (y / trackPx).coerceIn(0f, 1f)
                val index = (target * (total - visible)).toInt().coerceIn(0, total - 1)
                scope.launch {
                    listState?.scrollToItem(index)
                    gridState?.scrollToItem(index)
                }
            }

            // 轨道同时接收点击与拖动；两个检测器在同一 pointerInput 里并行，互不抢事件
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(8.dp)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .pointerInput(total, trackPx) {
                        coroutineScope {
                            launch { detectTapGestures { offset -> seek(offset.y) } }
                            launch {
                                detectVerticalDragGestures { change, _ -> seek(change.position.y) }
                            }
                        }
                    }
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 2.dp)
                    .width(4.dp)
                    .height(with(density) { thumbPx.toDp() })
                    .offset(y = with(density) { topPx.toDp() })
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
