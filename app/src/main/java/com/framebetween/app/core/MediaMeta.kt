package com.framebetween.app.core

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class VideoMeta(val width: Int, val height: Int, val fps: Double)

/**
 * 视频帧率不在 MediaStore 的标准列里，逐条跑 FFprobe 又太重。
 * 这里只对「实际可见」的媒体项惰性读取一次 MediaMetadataRetriever，
 * 结果缓存在内存中，UI 通过 StateFlow 自动重组。
 */
object MediaMeta {
    private val _map = MutableStateFlow<Map<String, VideoMeta>>(emptyMap())
    val map: StateFlow<Map<String, VideoMeta>> = _map.asStateFlow()

    private val inFlight: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun clear() {
        _map.value = emptyMap()
    }

    suspend fun load(context: Context, item: LibraryItem) {
        if (!item.isVideo || _map.value.containsKey(item.id) || !inFlight.add(item.id)) return
        val meta = withContext(Dispatchers.IO) { probe(context, item) }
        inFlight.remove(item.id)
        if (meta != null) _map.value = _map.value + (item.id to meta)
    }

    private fun probe(context: Context, item: LibraryItem): VideoMeta? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, item.uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: item.width
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: item.height
            var fps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toDoubleOrNull() ?: 0.0
            } else 0.0
            if (fps <= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && item.durationMs > 0) {
                val frames = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                    ?.toLongOrNull() ?: 0L
                if (frames > 0) fps = frames * 1000.0 / item.durationMs
            }
            VideoMeta(w, h, fps)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
