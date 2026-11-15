package com.framebetween.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    if (index == 0) return "$bytes B"
    val rounded = (value * 100).roundToLong() / 100.0
    return "$rounded ${units[index]}"
}

private fun pad2(value: Long): String = value.toString().padStart(2, '0')

fun formatDuration(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    val total = safe / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val millis = (safe % 1000).toString().padStart(3, '0')
    return "${pad2(h)}:${pad2(m)}:${pad2(s)}.$millis"
}

/** 不足 1 小时的时长只显示 mm:ss，避免出现多余的 00: 小时位。 */
fun formatClock(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "${pad2(h)}:${pad2(m)}:${pad2(s)}" else "${pad2(m)}:${pad2(s)}"
}

/** FFmpeg -ss / -t 使用的小数秒，避免 locale 导致小数点异常。 */
fun secondsString(ms: Long): String {
    val value = (ms.coerceAtLeast(0) / 1000.0 * 1000).roundToLong() / 1000.0
    return value.toString()
}

fun frameToMs(frame: Long, fps: Double): Long =
    if (fps > 0) (frame * 1000.0 / fps).roundToLong() else 0L

fun msToFrame(ms: Long, fps: Double): Long =
    if (fps > 0) (ms / 1000.0 * fps).roundToLong() else 0L

/** H.264/H.265 要求宽高为偶数。 */
fun even(value: Int): Int = (value.coerceAtLeast(0) / 2) * 2

fun safeFileName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "media" }

/**
 * 输出文件名：原文件名（保留中文）+ MMddHHmmss 时间戳。
 * 只剔除文件系统非法字符与空格，供 JVM 单元测试直接验证。
 */
fun outputFileName(sourceName: String?, ext: String, timeMs: Long = System.currentTimeMillis()): String {
    val base = sourceName
        ?.substringBeforeLast('.', sourceName)
        ?.replace(Regex("[/\\\\:*?\"<>|\\r\\n\\t]"), "_")
        ?.replace(' ', '_')
        ?.trim()
        ?.take(60)
        ?.takeUnless { it.isBlank() }
        ?: "FrameBetween"
    val suffix = ext.lowercase().takeUnless { it.isBlank() } ?: "mp4"
    val stamp = SimpleDateFormat("MMddHHmmss", Locale.US).format(Date(timeMs))
    return "${base}_$stamp.$suffix"
}

fun isAudioContainer(ext: String): Boolean =
    ext.lowercase() in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")

/**
 * 媒体库只收录音视频。图片 / 文档交给标题旁的「全部文件」或系统「打开方式」，
 * 这里统一按 mime 卡住，避免相册照片混进媒体库。
 * mimeOf() 仍保留图片后缀，因为产物分享、封面与水印素材需要正确的 mime。
 * 纯 Kotlin，供 JVM 单测直接验证。
 */
fun isAudioVideoMime(mime: String): Boolean =
    mime.startsWith("video/") || mime.startsWith("audio/")

/**
 * 媒体类型判定：媒体库扫描、文件管理与产物分享都靠它过滤文件。
 * 之前只认十来种后缀，AVI / TS / FLAC 之外的音频、JPG / PNG 图片一律判成
 * application/octet-stream 被丢掉，这是「扫描到的媒体文件不全」的直接原因。
 */
fun mimeOf(path: String): String {
    return when (path.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v", "mov" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "ts", "mts", "m2ts" -> "video/mp2t"
        "3gp", "3g2" -> "video/3gpp"
        "mpg", "mpeg", "mpe", "vob", "m1v", "m2v", "mpv", "dat" -> "video/mpeg"
        "flv", "f4v" -> "video/x-flv"
        "wmv", "asf" -> "video/x-ms-wmv"
        "rm", "rmvb" -> "video/vnd.rn-realvideo"
        "divx" -> "video/divx"
        "ogv", "mk3d", "gifv" -> "video/webm"
        "mxf", "dv", "3gp2" -> "video/mp4"
        "mp3", "mp2", "mpga" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "ogg", "opus", "oga" -> "audio/ogg"
        "amr" -> "audio/amr"
        "wma" -> "audio/x-ms-wma"
        "aif", "aiff", "aifc" -> "audio/aiff"
        "ape" -> "audio/x-ape"
        "ac3", "eac3", "dts" -> "audio/ac3"
        "mka" -> "audio/x-matroska"
        "m4b", "m4r" -> "audio/mp4"
        "wv", "mpc", "ra", "caf", "au" -> "audio/x-wav"
        "3ga", "mid", "midi" -> "audio/mpeg"
        "gif" -> "image/gif"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "heic", "heif" -> "image/heic"
        "avif" -> "image/avif"
        "tif", "tiff" -> "image/tiff"
        else -> "application/octet-stream"
    }
}
