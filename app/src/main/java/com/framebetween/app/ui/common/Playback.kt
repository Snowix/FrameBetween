package com.framebetween.app.ui.common
import com.framebetween.app.core.L10n

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.net.Uri
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.framebetween.app.core.AppLog
import com.framebetween.app.core.CommandRedactor
import com.framebetween.app.core.PlaybackCompat
import com.framebetween.app.util.formatClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.math.roundToInt

/**
 * 应用内播放器：媒体库点击直接播放，多选时按顺序连播。
 *
 * 实现要点：
 * - PlayerView 改用 TextureView，消除部分设备黑屏与退出页面后画面残留约一秒的问题；
 * - player 重建时通过 update 重新绑定，避免视图一直持有已释放的旧实例；
 * - 直接打开源文件播放，不预转码、不生成中间副本、不占用额外存储；
 * - 全屏改为「画面铺满 + 控制条浮层 + 隐藏系统栏」的真全屏；
 * - 双击全屏、水平拖动快进、右侧音量、左侧亮度，进度条统一变细。
 */
@Composable
fun MediaPlayerDialog(
    title: String,
    uris: List<Uri>,
    video: Boolean,
    onDismiss: () -> Unit,
    onAddToCurrent: (() -> Unit)? = null,
    startFullscreen: Boolean = false
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    var fullscreen by remember { mutableStateOf(video && startFullscreen) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // 直接打开源文件：不跑 ffprobe、不重编码、不复制 content://、不生成缓存产物。
    // 只有能解析出真实路径的 content:// 会被换算成 file://，属于零 IO 的字符串替换。
    val sources = remember(uris) { uris.map { PlaybackCompat.playbackUri(it) } }
    LaunchedEffect(uris) { errorText = null }
    var landscapeVideo by remember { mutableStateOf(false) }
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }
    // 非方形像素（SAR）。Media3 1.5.1 已内部应用旋转，width/height 就是显示尺寸。
    var videoSar by remember { mutableStateOf(1f) }

    // 播放器直接吃源文件。设备解不了的编码在这里明确报错，不再为了预览先跑一次
    // 重编码——那既慢又会在 cacheDir 里留下整份 H.264 副本。
    val player: ExoPlayer? = remember(sources) {
        ExoPlayer.Builder(context).build().apply {
            sources.forEach { addMediaItem(MediaItem.fromUri(it)) }
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(playing, player) { player?.playWhenReady = playing }
    LaunchedEffect(player) {
        while (isActive) {
            player?.let { p ->
                if (!scrubbing) position = p.currentPosition.coerceAtLeast(0L)
                val d = p.duration
                duration = if (d > 0) d else 0L
                if (p.playbackState == Player.STATE_ENDED) playing = false
            }
            delay(250)
        }
    }
    // 离开组合时先停播再释放，音频不会在返回上一页后继续响。
    DisposableEffect(player) {
        onDispose {
            player?.playWhenReady = false
            player?.pause()
            player?.release()
        }
    }

    DisposableEffect(player) {
        val p = player
        if (p == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                    // unappliedRotationDegrees 在 1.5.1 恒为 0 且已废弃，不要再交换宽高
                    videoSar = videoSize.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
                    landscapeVideo = videoSize.width > 0 && videoSize.height > 0 &&
                        videoSize.width > videoSize.height
                }

                override fun onPlayerError(error: PlaybackException) {
                    val reason = CommandRedactor.redactText(
                        error.cause?.message.orEmpty()
                    ).take(160)
                    AppLog.e("Playback", "${error.errorCodeName} $reason")
                    // 不再自动转码兜底：明确告知失败原因，并指向主动转换入口。
                    errorText = L10n.t("播放失败：%1\$s", error.errorCodeName) +
                        (if (reason.isNotBlank()) "\n$reason" else "") +
                        "\n" + L10n.t("该格式可能无法直接播放，可用「压缩 / 格式转换」生成兼容副本")
                }
            }
            p.addListener(listener)
            onDispose { p.removeListener(listener) }
        }
    }

    LaunchedEffect(fullscreen, landscapeVideo, video) {
        activity?.requestedOrientation = if (fullscreen && video && landscapeVideo) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    // 全屏时返回键先退出全屏，不直接关闭播放器
    BackHandler(enabled = fullscreen) { fullscreen = false }

    // 真全屏：把 Dialog 的 Window 拉到 MATCH_PARENT，并让 Activity Window 与
    // Dialog Window 一起进入沉浸式。全面屏手势导航 / 挖孔屏上只隐藏 Dialog 的面板
    // 窗口会被系统还原，状态栏图标与手势提示条会重新出现，因此统一交给
    // FullscreenBarsSession：两个窗口一起隐藏，并在系统栏重新可见时自动补隐藏。
    DisposableEffect(fullscreen) {
        val dialogWindow = view.dialogWindow()
        if (dialogWindow != null) {
            dialogWindow.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            dialogWindow.setBackgroundDrawable(ColorDrawable(0))
            dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        val windows = listOfNotNull(activity?.window, dialogWindow).distinct()
        val session = FullscreenBarsSession(windows, dialogWindow?.decorView)
        if (fullscreen) session.enter() else session.exit()
        onDispose {
            session.exit()
            dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
    }
    // 退出全屏后亮度交还给系统
    LaunchedEffect(fullscreen) {
        if (!fullscreen) activity?.window?.let { w ->
            val lp = w.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            w.attributes = lp
        }
    }

    // —— 全屏手势状态 ——
    var gesture by remember { mutableStateOf(PlayerGesture.NONE) }
    var seekPreview by remember { mutableStateOf(0L) }
    var volumePercent by remember { mutableStateOf(0) }
    var brightnessPercent by remember { mutableStateOf(50) }
    var surfaceWidthPx by remember { mutableStateOf(1f) }
    var surfaceHeightPx by remember { mutableStateOf(1f) }
    var seekAccum by remember { mutableStateOf(0f) }
    var seekStart by remember { mutableStateOf(0L) }
    var volumeAccum by remember { mutableStateOf(0f) }
    var volumeStart by remember { mutableStateOf(0) }
    var brightnessAccum by remember { mutableStateOf(0f) }
    var brightnessStart by remember { mutableStateOf(0.5f) }

    val onSeek: (Float, Boolean) -> Unit = { delta, finished ->
        if (finished) {
            player?.seekTo(seekPreview)
            position = seekPreview
            seekAccum = 0f
        } else {
            seekAccum += delta
            val span = duration.coerceAtLeast(1L)
            val deltaMs = (seekAccum / surfaceWidthPx.coerceAtLeast(1f) * span).toLong()
            seekPreview = (seekStart + deltaMs).coerceIn(0L, span)
        }
    }
    val onVolume: (Float, Boolean) -> Unit = { delta, finished ->
        if (finished) {
            volumeAccum = 0f
        } else {
            volumeAccum += delta
            val target = (volumeStart + volumeAccum / 320f * maxVolume)
                .roundToInt().coerceIn(0, maxVolume)
            runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
            volumePercent = target * 100 / maxVolume
        }
    }
    val onBrightness: (Float, Boolean) -> Unit = { delta, finished ->
        if (finished) {
            brightnessAccum = 0f
        } else {
            brightnessAccum += delta
            val value = (brightnessStart + brightnessAccum / 400f).coerceIn(0.02f, 1f)
            activity?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = value
                w.attributes = lp
            }
            brightnessPercent = (value * 100).toInt()
        }
    }
    val onKind: (PlayerGesture) -> Unit = { kind ->
        gesture = kind
        when (kind) {
            PlayerGesture.SEEK -> {
                seekAccum = 0f
                seekStart = position
                seekPreview = position
            }
            PlayerGesture.VOLUME -> {
                volumeAccum = 0f
                volumeStart = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                volumePercent = volumeStart * 100 / maxVolume
            }
            PlayerGesture.BRIGHTNESS -> {
                brightnessAccum = 0f
                val lp = activity?.window?.attributes
                brightnessStart = lp?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                brightnessPercent = (brightnessStart * 100).toInt()
            }
            PlayerGesture.NONE -> Unit
        }
    }

    val barTint = if (fullscreen) Color.White else MaterialTheme.colorScheme.onSurface
    val topBar: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
                .then(if (fullscreen) Modifier.background(Color(0x66000000)) else Modifier)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                color = barTint,
                modifier = Modifier.weight(1f)
            )
            if (onAddToCurrent != null) {
                IconButton(onClick = onAddToCurrent, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = L10n.t("添加到列表"),
                        tint = barTint,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            if (video) {
                IconButton(
                    onClick = { fullscreen = !fullscreen },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (fullscreen) L10n.t("退出全屏") else L10n.t("全屏"),
                        tint = barTint,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = L10n.t("关闭"),
                    tint = barTint,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
    val controls: @Composable (Modifier) -> Unit = { mod ->
        PlayerControls(
            playing = playing,
            onTogglePlay = { playing = !playing },
            position = position,
            duration = duration,
            onSeek = { ms -> player?.seekTo(ms); position = ms },
            onScrubbing = { scrubbing = it },
            fullscreen = fullscreen,
            modifier = mod
        )
    }
    val overlayMessage: @Composable () -> Unit = {
        val msg = errorText
        if (msg != null) {
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    msg,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 4
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = !fullscreen,
            decorFitsSystemWindows = !fullscreen
        )
    ) {
        if (fullscreen) {
            // 首帧 onSizeChanged 还没回来时用屏幕配置兜底，避免按 1×1 画布算出错误比例
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            val fallbackWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
            val fallbackHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
            val boxWidth = if (surfaceWidthPx > 1f) surfaceWidthPx else fallbackWidth
            val boxHeight = if (surfaceHeightPx > 1f) surfaceHeightPx else fallbackHeight
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                // 全屏「适配屏幕」：仅比例几乎一致时保持比例铺满，其余完整显示，绝不拉伸
                val fullscreenResize = adaptiveResizeMode(
                    videoWidth, videoHeight,
                    boxWidth.roundToInt(), boxHeight.roundToInt(), videoSar
                )
                PlayerSurface(
                    player = player,
                    resizeMode = fullscreenResize,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.matchParentSize()
                        .onSizeChanged {
                            surfaceWidthPx = it.width.toFloat().coerceAtLeast(1f)
                            surfaceHeightPx = it.height.toFloat().coerceAtLeast(1f)
                        }
                        .fullscreenPlayerGestures(
                            onDoubleTap = { fullscreen = false },
                            onSeek = onSeek,
                            onVolume = onVolume,
                            onBrightness = onBrightness,
                            onKind = onKind
                        )
                )
                Box(Modifier.align(Alignment.TopStart)) { topBar() }
                controls(Modifier.align(Alignment.BottomStart))
                GestureIndicator(gesture, volumePercent, brightnessPercent, seekPreview, duration)
                Box(Modifier.align(Alignment.Center)) { overlayMessage() }
            }
        } else {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    topBar()
                    Spacer(Modifier.height(6.dp))
                    if (video) {
                        Box(Modifier.fillMaxWidth().height(220.dp).background(Color.Black)) {
                            PlayerSurface(player, RESIZE_FIT, Modifier.fillMaxSize())
                            // 小窗双击直接进全屏
                            Box(
                                Modifier.matchParentSize().pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = { fullscreen = true })
                                }
                            )
                            Box(Modifier.align(Alignment.Center)) { overlayMessage() }
                        }
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(120.dp).background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(10.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    controls(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** 窄控制条：播放按钮 28dp、进度条 18dp、时间 10sp，尽量不遮挡画面。 */
@Composable
private fun PlayerControls(
    playing: Boolean,
    onTogglePlay: () -> Unit,
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onScrubbing: (Boolean) -> Unit,
    fullscreen: Boolean,
    modifier: Modifier = Modifier
) {
    val tint = if (fullscreen) Color.White else MaterialTheme.colorScheme.onSurface
    val inactive = if (fullscreen) {
        Color.White.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
    Row(
        modifier = modifier.fillMaxWidth()
            .then(if (fullscreen) Modifier.background(Color(0x66000000)) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onTogglePlay, modifier = Modifier.size(28.dp)) {
            Icon(
                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) L10n.t("暂停") else L10n.t("播放"),
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        ThinSeekBar(
            position = position,
            duration = duration,
            onSeek = onSeek,
            modifier = Modifier.weight(1f),
            onScrubbing = onScrubbing,
            activeColor = tint,
            inactiveColor = inactive
        )
        Text(
            "${formatClock(position)} / ${formatClock(duration)}",
            color = tint,
            fontSize = 10.sp,
            maxLines = 1
        )
        Spacer(Modifier.width(4.dp))
    }
}

/**
 * 单条音频的轻量预览。startMs / endMs 变化时播放器同步 seek 到新的起点，
 * 播放到终点自动暂停，因此「音频剪切」调整区间时能立刻听到对应位置。
 * speed 也会实时作用到播放器，变速试听与输出保持一致。
 */
@Composable
fun AudioPreview(
    path: String,
    speed: Float = 1f,
    startMs: Long = 0L,
    endMs: Long = Long.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var playing by remember(path) { mutableStateOf(false) }
    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            prepare()
        }
    }
    val startRef by rememberUpdatedState(startMs)
    val endRef by rememberUpdatedState(endMs)
    val playingRef by rememberUpdatedState(playing)
    LaunchedEffect(startMs) { player.seekTo(startMs.coerceAtLeast(0L)) }
    LaunchedEffect(playing) { player.playWhenReady = playing }
    LaunchedEffect(speed) { player.setPlaybackSpeed(speed.coerceIn(0.25f, 4f)) }
    LaunchedEffect(player) {
        while (isActive) {
            if (playingRef && endRef != Long.MAX_VALUE && player.currentPosition >= endRef) {
                player.pause()
                playing = false
                player.seekTo(startRef.coerceAtLeast(0L))
            }
            if (player.playbackState == Player.STATE_ENDED) playing = false
            delay(120)
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.pause()
            player.release()
        }
    }
    IconButton(
        onClick = {
            if (!playing) player.seekTo(startMs.coerceAtLeast(0L))
            playing = !playing
        },
        modifier = modifier
    ) {
        Icon(
            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playing) L10n.t("暂停") else L10n.t("播放"),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/** Compose Dialog 的 Window 是独立 Window，通过父链找到 DialogWrapper 拿 window。 */
private fun View.dialogWindow(): Window? {
    var node: ViewParent? = this.parent
    while (node != null) {
        val current = node
        val found = runCatching {
            val method = runCatching { current.javaClass.getMethod("getWindow") }
                .getOrElse { current.javaClass.getDeclaredMethod("getWindow") }
            method.isAccessible = true
            method.invoke(current) as? Window
        }.getOrNull()
        if (found != null) return found
        node = current.parent
    }
    return null
}

/** Compose 的 LocalContext 通常是 ContextWrapper，逐层取到 Activity 才控制屏幕方向 / 亮度。 */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
