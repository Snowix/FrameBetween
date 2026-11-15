package com.framebetween.app.ui.common
import com.framebetween.app.core.L10n

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.framebetween.app.core.AppLog
import com.framebetween.app.core.CommandRedactor
import com.framebetween.app.util.formatClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

/**
 * 共用的视频预览。外层 Box 与播放器同尺寸且统一 FIT 缩放，
 * 画面裁剪页可以据此把屏幕坐标精确换算回视频像素坐标。
 *
 * 用 TextureView 承载画面（消除黑屏与退出后残留一帧）、播放器重建时
 * 重新绑定、控制条做窄做细、双击进入全屏；直接预览源文件，不预转码。
 */
@Composable
fun VideoPreview(
    path: String,
    modifier: Modifier = Modifier,
    startMs: Long = 0,
    endMs: Long = -1,
    onSizeChanged: (IntSize) -> Unit = {},
    /** 可拖动图层需要盖在底部控制条之上，避免播放条抢占触摸事件。默认 false 保持旧页面行为。 */
    overlayAboveControls: Boolean = false,
    overlay: @Composable (BoxScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    var playing by remember(path) { mutableStateOf(true) }
    var position by remember(path) { mutableStateOf(0L) }
    var total by remember(path) { mutableStateOf(0L) }
    var scrubbing by remember(path) { mutableStateOf(false) }
    var showFullscreen by remember(path) { mutableStateOf(false) }
    var hadFullscreen by remember(path) { mutableStateOf(false) }
    var errorText by remember(path) { mutableStateOf<String?>(null) }

    // 直接预览源文件：没有异步准备、没有 ffprobe、没有兼容产物。换文件时重建播放器，
    // 解不了的编码由下方错误层明确提示，用户可主动去【压缩 / 格式转换】生成副本。
    val player: ExoPlayer? = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(startMs, player) { player?.seekTo(startMs) }
    LaunchedEffect(playing, player) { player?.playWhenReady = playing }
    LaunchedEffect(showFullscreen) {
        if (showFullscreen) {
            playing = false
            hadFullscreen = true
        } else if (hadFullscreen) {
            playing = true
        }
    }

    // 每 250ms 采样一次播放位置；拖动期间暂停采样，避免滑块与手指打架。
    LaunchedEffect(player) {
        while (isActive) {
            delay(250)
            if (!scrubbing) {
                player?.let { p ->
                    val current = p.currentPosition
                    if (current >= 0) position = current
                    val d = p.duration
                    if (d > 0) total = d
                }
            }
        }
    }
    LaunchedEffect(startMs, endMs, player) {
        if (endMs > startMs) {
            while (isActive) {
                delay(120)
                if (playing && (player?.currentPosition ?: 0) >= endMs) player?.seekTo(startMs)
            }
        }
    }
    DisposableEffect(player) {
        onDispose {
            player?.playWhenReady = false
            player?.pause()
            player?.release()
        }
    }
    // 解码失败时明确给出原因，不再静默重试或强制转码，避免一直黑屏却不给提示。
    DisposableEffect(player) {
        val p = player
        if (p == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    AppLog.e(
                        "Preview",
                        "${error.errorCodeName} " +
                            CommandRedactor.redactText(error.cause?.message.orEmpty()).take(120)
                    )
                    errorText = L10n.t("播放失败：%1\$s", error.errorCodeName) + "\n" +
                        L10n.t("该格式可能无法直接播放，可用「压缩 / 格式转换」生成兼容副本")
                }
            }
            p.addListener(listener)
            onDispose { p.removeListener(listener) }
        }
    }

    val sizeReporter = androidx.compose.runtime.rememberUpdatedState(onSizeChanged)
    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { sizeReporter.value(it) }
            // 换文件 / 重建播放器时尺寸可能完全相同，onSizeChanged 不会再回调；
            // 这里在每次布局完成后补一次上报，避免叠加图层拿到 0 尺寸后永远不出现。
            .onGloballyPositioned { coords ->
                sizeReporter.value(IntSize(coords.size.width, coords.size.height))
            }
    ) {
        PlayerSurface(
            player = player,
            resizeMode = RESIZE_FIT,
            modifier = Modifier.fillMaxSize()
        )
        // 双击进入全屏；放在业务 overlay 之下，裁剪 / 水印选区的拖动手势仍然优先。
        Box(
            Modifier.matchParentSize().pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { showFullscreen = true })
            }
        )
        if (!overlayAboveControls) overlay?.invoke(this)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(22.dp)
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { playing = !playing }, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) L10n.t("暂停") else L10n.t("播放"),
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
            ThinSeekBar(
                position = position,
                duration = total,
                onSeek = { ms ->
                    position = ms
                    player?.seekTo(ms)
                },
                modifier = Modifier.weight(1f),
                onScrubbing = { scrubbing = it }
            )
            Text(
                "${formatClock(position)} / ${formatClock(total)}",
                color = Color.White,
                fontSize = 9.sp,
                maxLines = 1
            )
            Spacer(Modifier.width(2.dp))
        }
        if (overlayAboveControls) overlay?.invoke(this)
        errorText?.let { msg ->
            Box(
                Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 11.sp, maxLines = 3)
            }
        }
    }

    if (showFullscreen) {
        MediaPlayerDialog(
            title = File(path).name,
            uris = listOf(Uri.fromFile(File(path))),
            video = true,
            startFullscreen = true,
            onDismiss = { showFullscreen = false }
        )
    }
}

/** FIT 模式下视频画面在预览 Box 中的实际矩形。 */
data class DisplayRect(val left: Float, val top: Float, val width: Float, val height: Float)

fun fitRect(videoWidth: Int, videoHeight: Int, boxWidth: Int, boxHeight: Int): DisplayRect {
    if (videoWidth <= 0 || videoHeight <= 0 || boxWidth <= 0 || boxHeight <= 0) {
        return DisplayRect(0f, 0f, boxWidth.toFloat(), boxHeight.toFloat())
    }
    val scale = minOf(
        boxWidth.toFloat() / videoWidth,
        boxHeight.toFloat() / videoHeight
    )
    val width = videoWidth * scale
    val height = videoHeight * scale
    return DisplayRect(
        left = (boxWidth - width) / 2,
        top = (boxHeight - height) / 2,
        width = width,
        height = height
    )
}
