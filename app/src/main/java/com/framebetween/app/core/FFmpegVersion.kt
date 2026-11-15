package com.framebetween.app.core

import com.arthenica.ffmpegkit.FFmpegKitConfig

/** 「AI 解析」提示词里 {FFmpeg版本} 的取值：优先读原生库，取不到时用构建依赖版本兜底。 */
object FFmpegVersion {

    private const val FALLBACK = "FFmpegKitNext 9.0.2 / FFmpeg 9.0.2"

    fun current(): String {
        val ffmpeg = runCatching {
            FFmpegKitConfig.getFFmpegVersion().orEmpty().trim()
        }.getOrDefault("")
        val kit = runCatching {
            FFmpegKitConfig.getVersion().orEmpty().trim()
        }.getOrDefault("")
        return buildList {
            if (kit.isNotBlank()) add("FFmpegKitNext $kit")
            if (ffmpeg.isNotBlank() && ffmpeg != kit) add("FFmpeg $ffmpeg")
        }.joinToString(" / ").ifBlank { FALLBACK }
    }
}
