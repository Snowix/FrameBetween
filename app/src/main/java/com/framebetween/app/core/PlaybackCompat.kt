package com.framebetween.app.core

import android.net.Uri
import com.framebetween.app.App
import java.io.File

/**
 * 播放源归一化与格式判定。
 *
 * 播放链路**不为不支持的格式预转码**：Media3 ExoPlayer 直接打开源
 * 文件，不跑 ffprobe、不重编码、不生成 H.264 副本、不落地 `content://` 源副本。
 * 因此既没有编码等待，也不会因为预览而占用存储。设备解不了的编码由播放器明确
 * 报错，需要兼容副本时由用户主动使用【压缩 / 格式转换】生成。
 *
 * 这里只保留两类零成本能力：
 *  1) 纯格式判定函数（媒体库标记、错误提示、JVM 单测），不再驱动任何转换；
 *  2) 把能解析出真实路径的 `content://` 换成 `file://`——只是路径换算，不复制
 *     文件；其余 `content://` 原样交给 ExoPlayer 的 ContentDataSource。
 *
 * [clearLegacyCache] 回收 `pb_*` / `pbe_*` / `pbf_*` 兼容产物与
 * `src_*` 源副本，冷启动执行一次即可释放残留占用。
 */
object PlaybackCompat {

    /** ExoPlayer 自带 Extractor 覆盖不到的非常见容器，仅用于标记与诊断。 */
    private val UNCOMMON_CONTAINERS = setOf(
        "wmv", "asf", "avi", "rm", "rmvb", "flv", "f4v", "vob", "mpg", "mpeg", "mpe",
        "m1v", "m2v", "mpv", "dat", "m2ts", "mts", "ts", "3gp", "3g2", "3gp2",
        "divx", "ogv", "mk3d", "gifv", "mxf", "dv", "swf", "vcd", "ps"
    )

    /** 纯音频容器，仅用于标记与诊断。 */
    private val AUDIO_ONLY = setOf(
        "wma", "ape", "ac3", "mka", "amr", "aif", "aiff", "ra", "wv", "mpc", "m4b", "caf"
    )

    private val VIDEO_MIMES = mapOf(
        "hevc" to "video/hevc", "h265" to "video/hevc", "hev1" to "video/hevc", "hvc1" to "video/hevc",
        "av1" to "video/av01", "av01" to "video/av01",
        "vp9" to "video/x-vnd.on2.vp9", "vp8" to "video/x-vnd.on2.vp8",
        "mpeg4" to "video/mp4v-es", "msmpeg4v3" to "video/mp4v-es", "divx" to "video/mp4v-es",
        "mpeg2video" to "video/mpeg2", "mpeg1video" to "video/mpeg2",
        "vc1" to "video/x-ms-wmv", "wmv3" to "video/x-ms-wmv"
    )

    private val AUDIO_MIMES = mapOf(
        "ac3" to "audio/ac3", "eac3" to "audio/eac3",
        "dts" to "audio/vnd.dts", "truehd" to "audio/truehd", "mlp" to "audio/truehd",
        "alac" to "audio/alac", "ape" to "audio/x-ape",
        "wmapro" to "audio/x-ms-wma", "wmalossless" to "audio/x-ms-wma"
    )

    private val ALWAYS_UNSUPPORTED_VIDEO = setOf(
        "vc1", "wmv1", "wmv2", "wmv3", "prores", "dnxhd", "ffv1", "rawvideo",
        "huffyuv", "cinepak", "svq3", "rv30", "rv40", "flv1", "vp6", "theora",
        "mpeg1video", "mpeg2video", "msmpeg4v2", "mpeg4", "divx"
    )
    private val ALWAYS_UNSUPPORTED_AUDIO = setOf(
        "cook", "sipr", "atrac3", "wmapro", "wmalossless", "ape", "mlp", "truehd",
        "pcm_s24le", "pcm_s32le", "pcm_f32le"
    )

    /** 是否为 ExoPlayer 非常见容器。仅供 UI 标记 / 诊断，不再触发任何转换。 */
    fun isUncommonContainer(nameOrPath: String): Boolean =
        nameOrPath.substringAfterLast('.', "").lowercase() in UNCOMMON_CONTAINERS

    fun isAudioOnly(nameOrPath: String): Boolean =
        nameOrPath.substringAfterLast('.', "").lowercase() in AUDIO_ONLY

    fun videoMimeOf(codec: String): String? = VIDEO_MIMES[codec.lowercase()]

    fun audioMimeOf(codec: String): String? = AUDIO_MIMES[codec.lowercase()]

    /**
     * 该编码是否超出本机 MediaCodec 能力。纯函数，单测覆盖 HEVC / 10-bit / AC-3
     * 等情形；只用于失败提示与诊断，播放链路不再据此生成副本。
     */
    fun shouldTranscode(
        videoCodec: String,
        videoPixFmt: String,
        audioCodec: String,
        supportedMimes: Set<String>
    ): Boolean {
        val vc = videoCodec.lowercase()
        val ac = audioCodec.lowercase()
        val fmt = videoPixFmt.lowercase()
        if (fmt.contains("10le") || fmt.contains("12le") || fmt.contains("10be") ||
            fmt.contains("yuv444") || fmt.contains("gbrp")
        ) return true
        if (vc.isNotBlank()) {
            if (vc in ALWAYS_UNSUPPORTED_VIDEO) return true
            val mime = videoMimeOf(vc)
            if (mime != null && mime !in supportedMimes) return true
        }
        if (ac.isNotBlank()) {
            if (ac in ALWAYS_UNSUPPORTED_AUDIO) return true
            val mime = audioMimeOf(ac)
            if (mime != null && mime !in supportedMimes) return true
        }
        return false
    }

    /**
     * 播放源归一化：仅把可解析出真实路径的 `content://` 换成 `file://`。
     * 不查询 ContentResolver 显示名、不复制文件、不生成任何缓存产物。
     */
    fun playbackUri(uri: Uri): Uri {
        if (!uri.scheme.equals("content", ignoreCase = true)) return uri
        val real = runCatching { MediaRepository.resolveOriginalPath(uri) }.getOrNull()
            ?.takeIf { it.isNotBlank() && File(it).isFile && File(it).canRead() }
            ?: return uri
        return Uri.fromFile(File(real))
    }

    /**
     * 删除 `cacheDir/playback` 下遗留的整个暂存目录（兼容产物 +
     * `src_*` 源副本），返回释放的字节数。目录只用于播放暂存，可整体回收。
     */
    fun clearLegacyCache(): Long {
        val dir = File(App.instance.cacheDir, "playback")
        if (!dir.isDirectory) return 0L
        var freed = 0L
        runCatching {
            dir.listFiles()?.forEach { f ->
                if (f.isFile) {
                    val size = f.length()
                    if (f.delete()) freed += size
                }
            }
            if (dir.listFiles().isNullOrEmpty()) runCatching { dir.delete() }
        }
        return freed
    }
}
