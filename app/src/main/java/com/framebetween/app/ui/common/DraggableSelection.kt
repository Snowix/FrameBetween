package com.framebetween.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private enum class SelectionCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

private const val MIN_SPAN = 0.03f

private data class SelectionGesture(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val unitW: Float,
    val unitH: Float,
    val onChange: (Float, Float, Float, Float) -> Unit
)

/**
 * 【画面裁剪】与【去水印·智能填补】共用的拖动选框。
 *
 * 白色边框 + 三分线 + 四角白色圆点 + 中央尺寸坐标；框内拖动平移，四角拖动缩放。
 * 输入输出都是相对视频画面的归一化坐标 (0..1)，因此不关心调用方用像素还是比例。
 * 手势开始时锁定起始区域并按累计位移计算，避免状态重组导致选框抖动或越拖越偏。
 */
@Composable
fun BoxScope.DraggableSelectionBox(
    rect: DisplayRect,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    label: String,
    onChange: (Float, Float, Float, Float) -> Unit
) {
    if (rect.width <= 0f || rect.height <= 0f) return
    val density = LocalDensity.current
    val x = rect.left + left * rect.width
    val y = rect.top + top * rect.height
    val w = ((right - left) * rect.width).coerceAtLeast(24f)
    val h = ((bottom - top) * rect.height).coerceAtLeast(24f)
    val latest = rememberUpdatedState(
        SelectionGesture(left, top, right, bottom, rect.width, rect.height, onChange)
    )

    Box(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(
                width = with(density) { w.toDp() },
                height = with(density) { h.toDp() }
            )
            .border(2.dp, Color.White)
            .background(Color.White.copy(alpha = 0.10f))
            .pointerInput(Unit) {
                var start = latest.value
                var accX = 0f
                var accY = 0f
                detectDragGestures(
                    onDragStart = {
                        start = latest.value
                        accX = 0f
                        accY = 0f
                    },
                    onDrag = { _, drag ->
                        accX += drag.x
                        accY += drag.y
                        val spanW = (start.right - start.left).coerceAtLeast(MIN_SPAN)
                        val spanH = (start.bottom - start.top).coerceAtLeast(MIN_SPAN)
                        val nl = (start.left + accX / start.unitW).coerceIn(0f, 1f - spanW)
                        val nt = (start.top + accY / start.unitH).coerceIn(0f, 1f - spanH)
                        latest.value.onChange(nl, nt, nl + spanW, nt + spanH)
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val line = 1.dp.toPx()
            val color = Color.White.copy(alpha = 0.35f)
            drawLine(color, Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), line)
            drawLine(color, Offset(size.width * 2 / 3, 0f), Offset(size.width * 2 / 3, size.height), line)
            drawLine(color, Offset(0f, size.height / 3), Offset(size.width, size.height / 3), line)
            drawLine(color, Offset(0f, size.height * 2 / 3), Offset(size.width, size.height * 2 / 3), line)
        }
        Box(
            Modifier.align(Alignment.Center)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
        }
        SelectionCorner.entries.forEach { corner ->
            val alignment = when (corner) {
                SelectionCorner.TOP_LEFT -> Alignment.TopStart
                SelectionCorner.TOP_RIGHT -> Alignment.TopEnd
                SelectionCorner.BOTTOM_LEFT -> Alignment.BottomStart
                SelectionCorner.BOTTOM_RIGHT -> Alignment.BottomEnd
            }
            val offsetX = when (corner) {
                SelectionCorner.TOP_LEFT, SelectionCorner.BOTTOM_LEFT -> (-10).dp
                else -> 10.dp
            }
            val offsetY = when (corner) {
                SelectionCorner.TOP_LEFT, SelectionCorner.TOP_RIGHT -> (-10).dp
                else -> 10.dp
            }
            Box(
                Modifier
                    .align(alignment)
                    .offset(offsetX, offsetY)
                    .size(20.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .pointerInput(corner) {
                        var start = latest.value
                        var accX = 0f
                        var accY = 0f
                        detectDragGestures(
                            onDragStart = {
                                start = latest.value
                                accX = 0f
                                accY = 0f
                            },
                            onDrag = { _, drag ->
                                accX += drag.x
                                accY += drag.y
                                val dx = accX / start.unitW
                                val dy = accY / start.unitH
                                var l = start.left
                                var t = start.top
                                var r = start.right
                                var b = start.bottom
                                when (corner) {
                                    SelectionCorner.TOP_LEFT -> {
                                        l = (l + dx).coerceIn(0f, r - MIN_SPAN)
                                        t = (t + dy).coerceIn(0f, b - MIN_SPAN)
                                    }
                                    SelectionCorner.TOP_RIGHT -> {
                                        r = (r + dx).coerceIn(l + MIN_SPAN, 1f)
                                        t = (t + dy).coerceIn(0f, b - MIN_SPAN)
                                    }
                                    SelectionCorner.BOTTOM_LEFT -> {
                                        l = (l + dx).coerceIn(0f, r - MIN_SPAN)
                                        b = (b + dy).coerceIn(t + MIN_SPAN, 1f)
                                    }
                                    SelectionCorner.BOTTOM_RIGHT -> {
                                        r = (r + dx).coerceIn(l + MIN_SPAN, 1f)
                                        b = (b + dy).coerceIn(t + MIN_SPAN, 1f)
                                    }
                                }
                                latest.value.onChange(l, t, r, b)
                            }
                        )
                    }
            )
        }
    }
}
