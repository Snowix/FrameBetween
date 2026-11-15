package com.framebetween.app.core

data class VideoStream(
    val codec: String = "",
    val profile: String = "",
    val pixFmt: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fps: Double = 0.0,
    val bitrateKbps: Long = 0,
    val nbFrames: Long = 0,
    val aspectRatio: String = ""
)

data class AudioStream(
    val codec: String = "",
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitrateKbps: Long = 0,
    val channelLayout: String = ""
)

data class MediaInfo(
    val path: String,
    val displayName: String,
    val sizeBytes: Long = 0,
    val durationMs: Long = 0,
    val formatName: String = "",
    val formatBitrateKbps: Long = 0,
    val video: VideoStream? = null,
    val audio: AudioStream? = null
) {
    val hasVideo: Boolean get() = video != null
    val hasAudio: Boolean get() = audio != null

    /** 容器整体码率（FFprobe 缺失时按文件大小 / 时长推算）。 */
    val overallBitrateKbps: Long
        get() = when {
            formatBitrateKbps > 0 -> formatBitrateKbps
            durationMs > 0 -> (sizeBytes * 8 / (durationMs / 1000.0) / 1000).toLong()
            else -> 0L
        }

    /** 裁剪 / 翻转等「保持原码率」场景使用的目标视频码率。 */
    val videoKbps: Long
        get() = video?.bitrateKbps?.takeIf { it > 0 }
            ?: (overallBitrateKbps - (audio?.bitrateKbps ?: 0L)).coerceAtLeast(0L)

    val audioKbps: Long
        get() = audio?.bitrateKbps?.takeIf { it > 0 } ?: 0L

    val totalFrames: Long
        get() = if (video != null && video.fps > 0) {
            (durationMs / 1000.0 * video.fps).toLong()
        } else 0L
}

enum class TaskStatus { RUNNING, SUCCESS, FAILED, CANCELLED }

data class TaskItem(
    val id: Long,
    val title: String,
    val command: String,
    val output: String = "",
    val durationMs: Long = 0,
    val status: TaskStatus = TaskStatus.RUNNING,
    val progress: Float = 0f,
    /** 进度来自耗时软估算（没有任何时间信号时），UI 会明确标注而不是假装精确。 */
    val estimated: Boolean = false,
    val speed: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val message: String = ""
) {
    val elapsedMs: Long get() = (endedAt ?: System.currentTimeMillis()) - startedAt
}
