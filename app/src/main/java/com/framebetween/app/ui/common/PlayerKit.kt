package com.framebetween.app.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.framebetween.app.R
import com.framebetween.app.core.L10n
import com.framebetween.app.util.formatClock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 播放器公共积木：TextureView 画面、细进度条、全屏手势与手势指示。
 * 媒体库播放器与功能页预览共用，避免两套实现行为不一致。
 */

/**
 * 常量：普通窗口用 FIT 完整显示；全屏「适配屏幕」见 adaptiveResizeMode。
 * RESIZE_MODE_FILL 会直接拉伸画面（不保持宽高比），绝不能用于「适配屏幕」；
 * 铺满必须用 RESIZE_MODE_ZOOM（保持比例、只做轻微裁剪）。
 */
const val RESIZE_FIT = AspectRatioFrameLayout.RESIZE_MODE_FIT
const val RESIZE_ZOOM = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
const val RESIZE_AUTO_FILL = AspectRatioFrameLayout.RESIZE_MODE_ZOOM

/** 全屏铺满前允许的最大比例偏差：超过就保持完整显示，宁可留黑边也不裁剪 / 拉伸。 */
private const val FIT_TOLERANCE = 0.02f

/**
 * 全屏「适配屏幕」。比例必须用有效显示尺寸计算，否则 2772×1272 一类录屏会错：
 *  - pixelWidthHeightRatio（SAR）非 1 时按显示宽度修正；Media3 1.5.0 曾在部分
 *    Pixel 设备上上报异常 SAR，这里只接受 0.5–2.0 的合理值，其余按方形像素处理；
 *  - rotationDegrees 仅为兼容性保留（播放器已内部应用旋转，恒为 0）；
 *  - view 尺寸用全屏画布实测值，未知时安全退回 FIT。
 * 修正后差异 ≤ 2% 才 ZOOM 铺满，其余一律 FIT 完整显示。可在 JVM 单测里直接验证。
 */
fun adaptiveResizeMode(
    videoWidth: Int,
    videoHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
    pixelWidthHeightRatio: Float = 1f,
    rotationDegrees: Int = 0
): Int {
    if (videoWidth <= 0 || videoHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return RESIZE_FIT
    val rotated = ((rotationDegrees % 180) + 180) % 180 == 90
    val w = (if (rotated) videoHeight else videoWidth).toFloat()
    val h = (if (rotated) videoWidth else videoHeight).toFloat()
    val sar = if (pixelWidthHeightRatio in 0.5f..2.0f) pixelWidthHeightRatio else 1f
    val videoRatio = (w * sar) / h
    val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
    val delta = abs(videoRatio - viewRatio) / viewRatio
    return if (delta <= FIT_TOLERANCE) RESIZE_AUTO_FILL else RESIZE_FIT
}

private fun newPlayerView(context: Context, resizeMode: Int): PlayerView {
    val view = LayoutInflater.from(context)
        .inflate(R.layout.player_surface, null) as PlayerView
    view.useController = false
    view.setResizeMode(resizeMode)
    view.layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )
    return view
}

/**
 * 换文件 / 重建 player 时必须通过 update 把新实例绑到 PlayerView 上，
 * 否则 factory 只执行一次，视图会一直持有已释放的旧 player（黑屏、进度不动）。
 * onRelease 在离开组合时立刻解绑，配合 TextureView 消除残留画面。
 */
@Composable
fun PlayerSurface(
    player: Player?,
    resizeMode: Int,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx -> newPlayerView(ctx, resizeMode).apply { this.player = player } },
        modifier = modifier.background(Color.Black),
        onRelease = { view -> view.player = null },
        update = { view ->
            if (view.player !== player) view.player = player
            view.setResizeMode(resizeMode)
            view.useController = false
        }
    )
}

/**
 * 细进度条：轨道 2dp、圆点 8dp、整体 18dp 高，不再用 Material3 Slider 的
 * 20dp 滑块和 48dp 触摸区，避免挡住画面。支持点击定位与横向拖动。
 */
@Composable
fun ThinSeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onScrubbing: (Boolean) -> Unit = {},
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.32f)
) {
    val seekRef = rememberUpdatedState(onSeek)
    val scrubRef = rememberUpdatedState(onScrubbing)
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val max = duration.coerceAtLeast(1L)
        val fraction = (position.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        Box(
            Modifier.fillMaxWidth().height(2.dp)
                .clip(RoundedCornerShape(1.dp)).background(inactiveColor)
        )
        if (fraction > 0f) {
            Box(
                Modifier.fillMaxWidth(fraction).height(2.dp)
                    .clip(RoundedCornerShape(1.dp)).background(activeColor)
            )
        }
        val thumbX = with(density) { (widthPx * fraction).toDp() - 4.dp }
        Box(
            Modifier.offset(x = thumbX.coerceAtLeast(0.dp)).size(8.dp)
                .clip(CircleShape).background(activeColor)
        )
        Box(
            Modifier.matchParentSize().pointerInput(duration, widthPx) {
                fun seekAt(x: Float) {
                    val ms = ((x.coerceIn(0f, widthPx) / widthPx) * max).toLong()
                        .coerceIn(0L, max)
                    seekRef.value(ms)
                }
                coroutineScope {
                    launch {
                        detectTapGestures { offset ->
                            scrubRef.value(true)
                            seekAt(offset.x)
                            scrubRef.value(false)
                        }
                    }
                    launch {
                        detectHorizontalDragGestures(
                            onDragStart = { scrubRef.value(true) },
                            onDragEnd = { scrubRef.value(false) },
                            onDragCancel = { scrubRef.value(false) }
                        ) { change, _ ->
                            seekAt(change.position.x)
                            change.consume()
                        }
                    }
                }
            }
        )
    }
}

enum class PlayerGesture { NONE, SEEK, VOLUME, BRIGHTNESS }

/**
 * 全屏手势：
 * - 双击 → 进入 / 退出全屏；
 * - 水平拖动 → 按屏幕宽度比例快进 / 快退；
 * - 右半屏纵向拖动 → 音量；
 * - 左半屏纵向拖动 → 亮度。
 * 一次拖动只锁定一种类型，避免斜向滑动时音量与进度同时变化。
 */
fun Modifier.fullscreenPlayerGestures(
    onDoubleTap: () -> Unit,
    onSeek: (deltaPx: Float, finished: Boolean) -> Unit,
    onVolume: (deltaPx: Float, finished: Boolean) -> Unit,
    onBrightness: (deltaPx: Float, finished: Boolean) -> Unit,
    onKind: (PlayerGesture) -> Unit
): Modifier = pointerInput(Unit) {
    coroutineScope {
        launch { detectTapGestures(onDoubleTap = { _ -> onDoubleTap() }) }
        launch {
            var startX = 0f
            var accX = 0f
            var accY = 0f
            var kind = PlayerGesture.NONE
            fun finish() {
                when (kind) {
                    PlayerGesture.SEEK -> onSeek(0f, true)
                    PlayerGesture.VOLUME -> onVolume(0f, true)
                    PlayerGesture.BRIGHTNESS -> onBrightness(0f, true)
                    PlayerGesture.NONE -> Unit
                }
                onKind(PlayerGesture.NONE)
            }
            detectDragGestures(
                onDragStart = { offset ->
                    startX = offset.x
                    accX = 0f
                    accY = 0f
                    kind = PlayerGesture.NONE
                },
                onDrag = { change, drag ->
                    accX += drag.x
                    accY += drag.y
                    if (kind == PlayerGesture.NONE && (abs(accX) > 12f || abs(accY) > 12f)) {
                        kind = when {
                            abs(accX) >= abs(accY) -> PlayerGesture.SEEK
                            startX > size.width / 2f -> PlayerGesture.VOLUME
                            else -> PlayerGesture.BRIGHTNESS
                        }
                        onKind(kind)
                    }
                    when (kind) {
                        PlayerGesture.SEEK -> onSeek(drag.x, false)
                        PlayerGesture.VOLUME -> onVolume(-drag.y, false)
                        PlayerGesture.BRIGHTNESS -> onBrightness(-drag.y, false)
                        PlayerGesture.NONE -> Unit
                    }
                    change.consume()
                },
                onDragEnd = { finish() },
                onDragCancel = { finish() }
            )
        }
    }
}

/** 全屏手势的即时反馈：中央显示跳转时间，左右显示音量 / 亮度竖条。 */
@Composable
fun BoxScope.GestureIndicator(
    kind: PlayerGesture,
    volumePercent: Int,
    brightnessPercent: Int,
    seekMs: Long,
    durationMs: Long
) {
    if (kind == PlayerGesture.NONE) return
    val dim = Color.Black.copy(alpha = 0.6f)
    if (kind == PlayerGesture.SEEK) {
        Box(
            Modifier.align(Alignment.Center)
                .clip(RoundedCornerShape(8.dp))
                .background(dim)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                "${formatClock(seekMs)} / ${formatClock(durationMs)}",
                color = Color.White, fontSize = 14.sp
            )
        }
        return
    }
    val percent = if (kind == PlayerGesture.VOLUME) volumePercent else brightnessPercent
    val label = if (kind == PlayerGesture.VOLUME) {
        L10n.t("音量 %1\$s%%", percent)
    } else {
        L10n.t("亮度 %1\$s%%", percent)
    }
    val align = if (kind == PlayerGesture.VOLUME) Alignment.CenterEnd else Alignment.CenterStart
    Column(
        Modifier.align(align).padding(horizontal = 24.dp)
            .size(width = 56.dp, height = 132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(dim)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.width(6.dp).weight(1f)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.28f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .fillMaxHeight((percent / 100f).coerceIn(0.01f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
            )
        }
    }
}
