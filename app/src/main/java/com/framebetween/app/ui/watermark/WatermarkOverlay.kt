package com.framebetween.app.ui.watermark

import com.framebetween.app.core.L10n

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.WatermarkGeometry
import com.framebetween.app.ui.common.DisplayRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val HandleColor = Color(0xFFFF5252)

/**
 * 预览画面里的水印图层：文字直接由 Compose 渲染，图片水印在 IO 线程采样解码。
 *
 * 交互稳定性要点：
 * - `pointerInput` 的 key 固定为 Unit，拖动时绝不会因状态变化而重启手势协程；
 * - 几何值放在组件内 `MutableState`（structuralEqualityPolicy），手势回调立即更新，
 *   既读到最新坐标又不依赖「重组之后才知道的新值」，选框不会只动一帧或越拖越偏；
 * - 不再在 if 分支里调用 remember（会打乱 Compose slot table），图片解码改为异步。
 */
@Composable
fun BoxScope.AddWatermarkPreview(
    rect: DisplayRect,
    videoWidth: Int,
    videoHeight: Int,
    isText: Boolean,
    text: String,
    textColor: Color,
    fontSizePx: Int,
    imagePath: String,
    /** 九宫格预设 / custom；custom 时改用拖动得到的 normX / normY。 */
    position: String,
    /** 视频像素为单位的边距，预览里按 unit 换算成屏幕像素。 */
    marginPx: Int,
    normX: Float,
    normY: Float,
    scale: Float,
    opacity: Float,
    onMove: (Float, Float) -> Unit,
    onScale: (Float) -> Unit
) {
    if (videoWidth <= 0 || videoHeight <= 0 || rect.width <= 0f || rect.height <= 0f) return
    val density = LocalDensity.current
    val unit = rect.width / videoWidth.toFloat()
    val lines = text.replace("\r", "").split("\n").map { it.ifBlank { " " } }
    val maxChars = lines.maxOf { it.length }.coerceAtLeast(1)

    // remember 必须在所有分支一致调用；图片解码放 IO，避免每次重组阻塞主线程
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imagePath) {
        bitmap = withContext(Dispatchers.IO) { decodeBitmap(imagePath) }
    }
    val baseWidthPx = if (isText) {
        maxChars * fontSizePx * 0.62f + fontSizePx * 0.7f
    } else {
        (bitmap?.width?.toFloat() ?: 1f).coerceAtLeast(1f)
    }
    val baseHeightPx = if (isText) {
        lines.size * fontSizePx * 1.35f + fontSizePx * 0.7f
    } else {
        (bitmap?.height?.toFloat() ?: 1f).coerceAtLeast(1f)
    }
    val wmW = (baseWidthPx * unit * scale).coerceIn(12f, rect.width)
    val wmH = (baseHeightPx * unit * scale).coerceIn(12f, rect.height)
    val maxLeft = (rect.width - wmW).coerceAtLeast(1f)
    val maxTop = (rect.height - wmH).coerceAtLeast(1f)
    // 之前把所有非 custom 位置的 normX/Y(-1) 直接 coerce 成 0，导致九个预设
    // 在预览里全部堆在左上角。这里按位置 + 边距换算，且与命令层的 overlay 表达式一致。
    val custom = position == "custom" || (normX >= 0f && normY >= 0f)
    val (nx, ny) = if (custom) {
        normX.coerceIn(0f, 1f) to normY.coerceIn(0f, 1f)
    } else {
        WatermarkGeometry.presetNorm(
            position, wmW, wmH, rect.width, rect.height,
            marginPx.coerceAtLeast(0) * unit
        )
    }
    val left = rect.left + nx * maxLeft
    val top = rect.top + ny * maxTop

    val gesture = WatermarkGesture(rect.left, rect.top, left, top, scale, maxLeft, maxTop)
    val state = remember { mutableStateOf(gesture, structuralEqualityPolicy()) }
    state.value = gesture
    val callbacks = rememberUpdatedState(onMove to onScale)

    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(
                width = with(density) { wmW.toDp() },
                height = with(density) { wmH.toDp() }
            )
            .border(1.dp, Color.White.copy(alpha = 0.85f))
            .background(Color.White.copy(alpha = 0.05f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val p = state.value
                    val (move, scaleTo) = callbacks.value
                    var nextScale = p.scale
                    if (zoom > 0f && zoom != 1f) {
                        nextScale = (p.scale * zoom).coerceIn(0.2f, 3f)
                        scaleTo(nextScale)
                    }
                    var nx = WatermarkGeometry.normOf(p.left, p.rectLeft, p.maxLeft)
                    var ny = WatermarkGeometry.normOf(p.top, p.rectTop, p.maxTop)
                    if (pan.x != 0f || pan.y != 0f) {
                        nx = WatermarkGeometry.normOf(p.left + pan.x, p.rectLeft, p.maxLeft)
                        ny = WatermarkGeometry.normOf(p.top + pan.y, p.rectTop, p.maxTop)
                        move(nx, ny)
                    }
                    // 立即写回本地，保证同一手势内下一帧从最新位置继续累加
                    state.value = p.copy(
                        left = p.rectLeft + nx * p.maxLeft,
                        top = p.rectTop + ny * p.maxTop,
                        scale = nextScale
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isText) {
            val previewFontPx = (fontSizePx * unit * scale).coerceIn(6f, 160f)
            val fontSizeSp = with(density) { previewFontPx.toDp().toSp() }
            val lineHeightSp = with(density) { (previewFontPx * 1.35f).toDp().toSp() }
            Text(
                text = lines.joinToString("\n").ifBlank { "FrameBetween" },
                color = textColor.copy(alpha = opacity.coerceIn(0.05f, 1f)),
                fontSize = fontSizeSp,
                lineHeight = lineHeightSp,
                fontWeight = FontWeight.Bold,
                maxLines = lines.size.coerceAtLeast(1)
            )
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = L10n.t("图片水印预览"),
                alpha = opacity.coerceIn(0.05f, 1f),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private data class WatermarkGesture(
    val rectLeft: Float,
    val rectTop: Float,
    val left: Float,
    val top: Float,
    val scale: Float,
    val maxLeft: Float,
    val maxTop: Float
)

/**
 * 去水印的可视化选框：拖动内部平移，拖动四角白色把手改变宽高。
 * 手势开始时锁定起始区域，之后按累计位移计算，实时写回 X / Y / 宽 / 高。
 */
@Composable
fun BoxScope.RemoveRegionPreview(
    rect: DisplayRect,
    videoWidth: Int,
    videoHeight: Int,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    onChange: (Int, Int, Int, Int) -> Unit
) {
    if (videoWidth <= 0 || videoHeight <= 0 || rect.width <= 0f || rect.height <= 0f) return
    val density = LocalDensity.current
    val unitX = rect.width / videoWidth.toFloat()
    val unitY = rect.height / videoHeight.toFloat()
    val region = WatermarkGeometry.clamp(
        WatermarkGeometry.Region(x, y, width, height), videoWidth, videoHeight
    )
    val left = rect.left + region.x * unitX
    val top = rect.top + region.y * unitY
    val boxW = (region.width * unitX).coerceAtLeast(24f)
    val boxH = (region.height * unitY).coerceAtLeast(24f)

    val latest = rememberUpdatedState(
        RegionGesture(region, unitX, unitY, videoWidth, videoHeight, onChange)
    )

    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(
                width = with(density) { boxW.toDp() },
                height = with(density) { boxH.toDp() }
            )
            .border(2.dp, HandleColor)
            .background(HandleColor.copy(alpha = 0.18f))
            .pointerInput(Unit) {
                var startRegion = latest.value.region
                var accX = 0f
                var accY = 0f
                detectDragGestures(
                    onDragStart = {
                        startRegion = latest.value.region
                        accX = 0f
                        accY = 0f
                    },
                    onDrag = { _, drag ->
                        accX += drag.x
                        accY += drag.y
                        val g = latest.value
                        val next = WatermarkGeometry.move(
                            startRegion, accX, accY,
                            g.unitX, g.unitY, g.videoWidth, g.videoHeight
                        )
                        g.onChange(next.x, next.y, next.width, next.height)
                    }
                )
            }
    ) {
        Text(
            L10n.t("拖动调整区域"),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.Center)
        )
        listOf(
            Alignment.TopStart, Alignment.TopEnd,
            Alignment.BottomStart, Alignment.BottomEnd
        ).forEachIndexed { corner, alignment ->
            Box(
                modifier = Modifier
                    .align(alignment)
                    .size(20.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, HandleColor, CircleShape)
                    .pointerInput(corner) {
                        var startRegion = latest.value.region
                        var accX = 0f
                        var accY = 0f
                        detectDragGestures(
                            onDragStart = {
                                startRegion = latest.value.region
                                accX = 0f
                                accY = 0f
                            },
                            onDrag = { _, drag ->
                                accX += drag.x
                                accY += drag.y
                                val g = latest.value
                                val next = WatermarkGeometry.resize(
                                    startRegion, corner, accX, accY,
                                    g.unitX, g.unitY, g.videoWidth, g.videoHeight
                                )
                                g.onChange(next.x, next.y, next.width, next.height)
                            }
                        )
                    }
            )
        }
    }
}

private data class RegionGesture(
    val region: WatermarkGeometry.Region,
    val unitX: Float,
    val unitY: Float,
    val videoWidth: Int,
    val videoHeight: Int,
    val onChange: (Int, Int, Int, Int) -> Unit
)

/** 预览用位图按最长边 1024 采样，防止超大水印图 OOM；调用方已切到 IO 线程。 */
private fun decodeBitmap(path: String): ImageBitmap? = runCatching {
    if (path.isBlank()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
    val sample = if (maxSide > 1024) Integer.highestOneBit(maxSide / 1024) else 1
    val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
    BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}.getOrNull()
