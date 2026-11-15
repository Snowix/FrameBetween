package com.framebetween.app.core

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import java.util.Locale

/** 一个 Android MediaCodec 编解码器（同名不同 mime 已在 report 中合并）。 */
data class CodecEntry(
    val name: String,
    val mime: String,
    val hardware: Boolean,
    val encoder: Boolean
)

/** FFmpeg 运行时探测结果：直接来自本机构建的 -hwaccels / -encoders / -decoders。 */
data class FFmpegProbe(
    val version: String,
    val buildDate: String,
    val hwaccels: List<String>,
    val mediaCodecEncoders: List<String>,
    val mediaCodecDecoders: List<String>
) {
    val mediaCodecSupported: Boolean
        get() = hwaccels.any { it.equals("mediacodec", ignoreCase = true) } ||
            mediaCodecEncoders.isNotEmpty() || mediaCodecDecoders.isNotEmpty()
}

data class DeviceCodecReport(
    val hardwareDecoders: List<CodecEntry>,
    val hardwareEncoders: List<CodecEntry>,
    val softwareDecoders: List<CodecEntry>,
    val softwareEncoders: List<CodecEntry>,
    val probe: FFmpegProbe
)

/**
 * 设备编解码能力探测。
 *
 * 两部分都是实测而非硬编码清单：
 * 1. Android MediaCodecList：本设备实际注册的硬件 / 软件编解码器；
 * 2. FFmpegKit 运行时执行 -hwaccels / -encoders / -decoders，读取这份二进制真正
 *    编译进去的硬件加速方式与 MediaCodec 编解码器。
 *
 * 探测在冷启动由 App 起 IO 协程异步预热（不阻塞首帧），用户打开能力页时也会
 * 触发；结果会被缓存。[mediaCodecSupported] 绝不阻塞调用线程：探测完成前按已
 * 确认的构建事实（该 AAR 带 --enable-mediacodec / --enable-jni）返回 true，
 * 完成后以真实的 -hwaccels 结果为准。
 */
object CodecCapabilities {

    private val probeLock = Any()

    @Volatile
    private var cachedProbe: FFmpegProbe? = null

    /** 供 FFmpegEngine 判断是否注入 -hwaccel mediacodec；永不执行耗时探测。 */
    fun mediaCodecSupported(): Boolean {
        val probe = cachedProbe ?: return true
        return probe.hwaccels.any { it.contains("mediacodec", ignoreCase = true) }
    }

    fun report(): DeviceCodecReport {
        val probe = probeFfmpeg()
        val codecs = androidCodecs()
        return DeviceCodecReport(
            hardwareDecoders = distinctByName(codecs.filter { it.hardware && !it.encoder }),
            hardwareEncoders = distinctByName(codecs.filter { it.hardware && it.encoder }),
            softwareDecoders = distinctByName(codecs.filter { !it.hardware && !it.encoder }),
            softwareEncoders = distinctByName(codecs.filter { !it.hardware && it.encoder }),
            probe = probe
        )
    }

    fun androidCodecs(): List<CodecEntry> {
        val out = mutableListOf<CodecEntry>()
        runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.codecInfos.forEach { info ->
                val hardware = isHardware(info)
                info.supportedTypes.forEach { mime ->
                    if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                        out += CodecEntry(info.name, mime, hardware, info.isEncoder)
                    }
                }
            }
        }
        return out
    }

    private fun isHardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isHardwareAccelerated
        // API 26–28 没有 isHardwareAccelerated，用组件名做保守判断：
        // 厂商 OMX / Codec2 组件视为硬件，Google 的纯软件实现视为软件。
        val n = info.name.lowercase(Locale.US)
        return (n.startsWith("omx.") || n.startsWith("c2.")) &&
            !n.contains("google") && !n.contains("software") && !n.contains(".sw.")
    }

    private fun distinctByName(entries: List<CodecEntry>): List<CodecEntry> =
        entries.groupBy { it.name }
            .map { (name, group) ->
                val mimes = group.map { it.mime }.distinct().sorted()
                CodecEntry(name, mimes.joinToString(", "), group.first().hardware, group.first().encoder)
            }
            .sortedBy { it.name }

    fun probeFfmpeg(): FFmpegProbe = cachedProbe ?: synchronized(probeLock) {
        cachedProbe ?: runCatching { doProbe() }
            .getOrDefault(staticFallback())
            .also { cachedProbe = it }
    }

    private fun doProbe(): FFmpegProbe {
        val hwText = executeProbe("-hide_banner", "-hwaccels")
        val encText = executeProbe("-hide_banner", "-encoders")
        val decText = executeProbe("-hide_banner", "-decoders")
        // 三条都没输出说明原生库没起来；抛出去让 probeFfmpeg 回退到静态构建事实，
        // 否则会缓存一份「空能力」把硬件开关静默关掉。
        if (hwText.isBlank() && encText.isBlank() && decText.isBlank()) {
            throw IllegalStateException("FFmpeg information commands returned no output")
        }
        val hwaccels = hwText.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !line.startsWith("Hardware acceleration methods") &&
                    !line.startsWith("ffmpeg version") &&
                    !line.startsWith("configuration:") &&
                    line.length < 40
            }
            .distinct()
            .sorted()
        return FFmpegProbe(
            version = FFmpegVersion.current(),
            buildDate = runCatching { FFmpegKitConfig.getBuildDate().orEmpty() }.getOrDefault(""),
            hwaccels = hwaccels,
            mediaCodecEncoders = mediaCodecNames(encText),
            mediaCodecDecoders = mediaCodecNames(decText)
        )
    }

    /** 同步执行一次信息类命令；调用方必须放在 IO 线程。 */
    private fun executeProbe(vararg args: String): String = runCatching {
        // ffmpeg-kit-next 的 Kotlin 签名是 Array<String>（不变型），vararg 的
        // Array<out String> 投影不能直接传；显式复制成 Array<String>。
        // getAllLogsAsString() 在 Kotlin 库里是显式方法，不再合成 allLogsAsString 属性。
        FFmpegKit.executeWithArguments(Array(args.size) { args[it] })
            .getAllLogsAsString().orEmpty()
    }.getOrDefault("")

    private fun mediaCodecNames(text: String): List<String> = text.lines()
        .mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 3)
            parts.getOrNull(1)?.takeIf { it.contains("mediacodec", ignoreCase = true) }
        }
        .distinct()
        .sorted()

    /**
     * 原生探测失败时的静态兜底。这些名称来自对 AAR 内 libavcodec.so 的实测检查：
     * 该构建 configure 带 --enable-mediacodec / --enable-jni / --enable-libx264 /
     * --enable-libx265 / --enable-libass / --enable-libfreetype，并包含 AMediaCodec
     * JNI 桥接与 mediacodec 系列编解码器。
     */
    private fun staticFallback(): FFmpegProbe = FFmpegProbe(
        version = FFmpegVersion.current(),
        buildDate = "",
        hwaccels = listOf("mediacodec"),
        mediaCodecEncoders = listOf(
            "h264_mediacodec", "hevc_mediacodec", "mpeg4_mediacodec",
            "vp8_mediacodec", "vp9_mediacodec"
        ),
        mediaCodecDecoders = listOf(
            "h264_mediacodec", "hevc_mediacodec", "mpeg4_mediacodec",
            "vp8_mediacodec", "vp9_mediacodec"
        )
    )
}
