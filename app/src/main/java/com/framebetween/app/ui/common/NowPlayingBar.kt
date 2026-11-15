package com.framebetween.app.ui.common
import com.framebetween.app.core.L10n

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.framebetween.app.core.LibraryItem
import com.framebetween.app.core.MediaThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** 音频封面：命中缩略图缓存就直接复用，失败回退音符图标。 */
@Composable
private fun rememberCover(item: LibraryItem): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.id) {
        bitmap = withContext(Dispatchers.IO) { MediaThumbnails.load(context, item) }
    }
    return bitmap
}

/**
 * 音乐播放条：贴在底部导航栏上方，不弹窗、不打断页面。
 * 提供缩小封面、歌名、播放/暂停与停止；停止后由调用方清空状态并淡出关闭。
 */
@Composable
fun NowPlayingBar(items: List<LibraryItem>, onStop: () -> Unit) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    var playing by remember(items) { mutableStateOf(true) }
    var index by remember(items) { mutableStateOf(0) }
    val player = remember(items) {
        ExoPlayer.Builder(context).build().apply {
            items.forEach { addMediaItem(MediaItem.fromUri(it.uri)) }
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(playing) { player.playWhenReady = playing }
    LaunchedEffect(player) {
        while (isActive) {
            index = player.currentMediaItemIndex.coerceIn(0, items.lastIndex)
            if (player.playbackState == Player.STATE_ENDED) playing = false
            delay(250)
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    val current = items.getOrNull(index) ?: items.first()
    val cover = rememberCover(current)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(62.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (cover != null) {
                    Image(
                        bitmap = cover!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    current.title,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    if (items.size > 1) {
                        L10n.t("共 %1\$s 首 · 第 %2\$s 首", items.size, index + 1)
                    } else L10n.t("正在播放"),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
            IconButton(onClick = { playing = !playing }) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) L10n.t("暂停") else L10n.t("播放"),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            IconButton(onClick = {
                player.stop()
                playing = false
                onStop()
            }) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = L10n.t("停止"),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
