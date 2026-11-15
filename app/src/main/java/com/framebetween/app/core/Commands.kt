package com.framebetween.app.core
import com.framebetween.app.core.L10n

import com.framebetween.app.util.even
import com.framebetween.app.util.isAudioContainer
import com.framebetween.app.util.secondsString
import java.io.File

object Codecs {
    /** 所有需要重编码的视频处理默认使用 H.265 / HEVC。 */
    const val DEFAULT_VIDEO = "libx265"

    /** 音频码率档位（kbps）：0 =「不变」，不显式传 -b:a，由编码器决定。 */
    val bitratePresets = listOf(0, 96, 128, 192, 320)

    fun bitrateLabel(kbps: Int): String =
        if (kbps <= 0) L10n.t("不变") else "$kbps kbps"

    val video = listOf(
        "copy" to L10n.t("直接复制（最快）"),
        "libx264" to "H.264 / AVC",
        "libx265" to "H.265 / HEVC",
        "libvpx-vp9" to "VP9",
        "mpeg4" to "MPEG-4",
        "none" to L10n.t("不输出视频（纯音频）")
    )
    val audio = listOf(
        "copy" to L10n.t("直接复制（最快）"),
        "aac" to "AAC",
        "libmp3lame" to "MP3",
        "libopus" to "Opus",
        "libvorbis" to "Vorbis",
        "flac" to L10n.t("FLAC（无损）"),
        "pcm_s16le" to L10n.t("WAV / PCM（无损）"),
        "none" to L10n.t("静音（去掉音轨）")
    )
    val containers = listOf("mp4", "mkv", "mov", "webm", "mp3", "m4a", "wav", "flac", "ogg")
    val presets = listOf("ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow", "veryslow")
    val resolutions = listOf(
        "0" to L10n.t("原始分辨率"),
        "240" to "240p",
        "360" to "360p",
        "480" to "480p",
        "720" to "720p（HD）",
        "1080" to "1080p（FHD）",
        "1440" to "1440p（2K）",
        "2160" to "2160p（4K）"
    )
    val audioFormats = listOf("mp3", "m4a", "wav", "flac", "ogg")
    val sampleRates = listOf(8000, 16000, 22050, 32000, 44100, 48000, 96000)

    fun label(options: List<Pair<String, String>>, value: String): String =
        options.firstOrNull { it.first == value }?.second ?: value
}

data class ConvertConfig(
    val container: String = "mp4",
    val videoCodec: String = Codecs.DEFAULT_VIDEO,
    val audioCodec: String = "aac",
    val targetWidth: Int = 0,
    val targetHeight: Int = 0,
    val videoBitrateKbps: Int = 2500,
    val audioBitrateKbps: Int = 128,
    val crf: Int = 0,
    val preset: String = "veryfast",
    val fpsLimit: Int = 0
) {
    val audioOnly: Boolean get() = isAudioContainer(container) || videoCodec == "none"
}

enum class TrimMode(val label: String, val hint: String) {
    COPY(L10n.t("极速 · 不重编码"), L10n.t("秒级完成，起点对齐到关键帧")),
    FAST_ENCODE(L10n.t("均衡 · 快速精确"), L10n.t("输入前定位 + 重编码，速度与精度兼顾")),
    PRECISE(L10n.t("高质量 · 逐帧精确"), L10n.t("从起点解码，帧级精确但最慢"))
}

data class TrimConfig(
    val startMs: Long,
    val endMs: Long,
    /** 默认极速：直接流复制，秒级完成。需要逐帧精确时再切到「精确」。 */
    val mode: TrimMode = TrimMode.COPY
)

/** 亮度 / 对比度 / 饱和度 / 伽马；全部使用 eq 滤镜的合法范围。 */
data class BrightnessConfig(
    val brightness: Float = 0.06f,   // -1.0 .. 1.0
    val contrast: Float = 1.10f,     // -2.0 .. 2.0
    val saturation: Float = 1.05f,   // 0.0 .. 3.0
    val gamma: Float = 1.00f         // 0.1 .. 10.0
)

/**
 * 添加水印的全部可调参数。带默认值的字段让旧调用（位置 + 边距）保持原行为：
 * opacity=1 / scale=1 / 无时间段 / 无自定义坐标时生成的命令保持默认行为。
 */
data class WatermarkConfig(
    val overlayPng: String,
    val position: String = "br",
    val margin: Int = 24,
    /** 0.05–1.0，作用于第二输入 PNG 的 alpha 通道。 */
    val opacity: Float = 1f,
    /** 0.1–4.0，预览里拖动缩放后写回，等比缩放水印图层。 */
    val scale: Float = 1f,
    /** 显示时间段（秒）；endSec <= startSec 表示全程显示。 */
    val startSec: Double = 0.0,
    val endSec: Double = 0.0,
    /** 预览拖动的归一化位置（0–1，相对水印左上角）；小于 0 时回落到九宫格 position。 */
    val normX: Float = -1f,
    val normY: Float = -1f
)

data class CropConfig(val x: Int, val y: Int, val width: Int, val height: Int, val crf: Int = 20)

data class MergeConfig(val reencode: Boolean = false)

enum class FlipOp(val label: String, val filter: String, val rotation: Int) {
    HFLIP(L10n.t("水平镜像"), "hflip", 0),
    VFLIP(L10n.t("垂直翻转"), "vflip", 0),
    ROT90(L10n.t("顺时针 90°"), "transpose=1", 90),
    ROT180(L10n.t("旋转 180°"), "hflip,vflip", 180),
    ROT270(L10n.t("逆时针 90°"), "transpose=2", 270)
}

data class ExtractConfig(
    val format: String = "mp3",
    val bitrateKbps: Int = 192,
    val startMs: Long = 0,
    val endMs: Long = -1
)

data class GifConfig(
    val startMs: Long = 0,
    val endMs: Long = -1,
    val width: Int = 480,
    val fps: Int = 12,
    val loop: Int = 0,
    val highQuality: Boolean = true
)

/** 音频拼接 / 剪切共用的输出参数。 */
data class AudioOutputConfig(
    val format: String = "mp3",
    val sampleRate: Int = 44100,
    val bitrateKbps: Int = 192
)

enum class AudioJoinMode(val label: String) {
    SEQUENCE(L10n.t("顺序拼接")),
    MIX(L10n.t("音轨合并"))
}

data class AudioJoinConfig(
    val mode: AudioJoinMode = AudioJoinMode.SEQUENCE,
    /** 与输入文件一一对应的音量 / 响度倍率，1.0 为原始音量。 */
    val volumes: List<Float> = emptyList(),
    /** 相邻音频之间的交叉淡入淡出时长，0 表示直接拼接。 */
    val crossfadeMs: Long = 0L,
    val output: AudioOutputConfig = AudioOutputConfig()
)

data class AudioCutConfig(
    val startMs: Long = 0,
    val endMs: Long = -1,
    /** 0.5–2.0 直接 atempo，超出范围时串联两级；1.0 不变速。 */
    val speed: Float = 1f,
    val output: AudioOutputConfig = AudioOutputConfig()
)

/** 变速页的快捷档位，滑条仍可在 0.25–4.0 之间任意取值。 */
enum class SpeedPreset(val value: Float) {
    X025(0.25f), X05(0.5f), X15(1.5f), X2(2f), X4(4f)
}

enum class SubtitleAction(val label: String) {
    EXTRACT(L10n.t("提取字幕")),
    EMBED(L10n.t("嵌入字幕")),
    BURN(L10n.t("烧录字幕"))
}

/** 动态水印：位置 / 可见性随时间变化，用于防盗录溯源。 */
enum class WatermarkMotion(val label: String) {
    STATIC(L10n.t("固定位置")),
    SLIDE(L10n.t("水平游走")),
    FLOAT(L10n.t("上下浮动")),
    BLINK(L10n.t("定时闪烁")),
    RANDOM(L10n.t("随机显示")),
    BOUNCE(L10n.t("边缘反弹"))
}

enum class RemoveMode(val label: String) {
    BLUR(L10n.t("模糊遮挡")),
    MOSAIC(L10n.t("马赛克")),
    INPAINT(L10n.t("智能填补（delogo）"))
}

data class RemoveWatermarkConfig(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val mode: RemoveMode = RemoveMode.BLUR,
    val intensity: Int = 10
)

/** 所有 FFmpeg 参数的唯一出口：只拼接参数数组，不拼 shell。 */
object Commands {

    /**
     * 需要重编码的编辑操作沿用源视频平均码率：ABR 目标 + maxrate/bufsize 双重上限，
     * 避免 CRF 恒定质量在已压缩素材上「越编越大」。音频一律直接复制。
     */
    private fun rateArgs(videoKbps: Long): List<String> =
        if (videoKbps > 0) listOf(
            "-b:v", "${videoKbps}k",
            "-maxrate", "${videoKbps}k",
            "-bufsize", "${videoKbps * 2}k"
        ) else listOf("-crf", "23")

    private fun finish(args: MutableList<String>, output: String) {
        val ext = output.substringAfterLast('.', "").lowercase()
        if (ext in setOf("mp4", "mov", "m4a")) {
            args += listOf("-movflags", "+faststart")
        }
        args += output
    }

    fun defaultCodecs(container: String): Pair<String, String> = when (container.lowercase()) {
        "webm" -> "libvpx-vp9" to "libopus"
        "mp3" -> "none" to "libmp3lame"
        "m4a" -> "none" to "aac"
        "wav" -> "none" to "pcm_s16le"
        "flac" -> "none" to "flac"
        "ogg" -> "none" to "libvorbis"
        "mkv" -> Codecs.DEFAULT_VIDEO to "aac"
        "mov" -> Codecs.DEFAULT_VIDEO to "aac"
        else -> Codecs.DEFAULT_VIDEO to "aac"
    }

    fun convert(input: String, output: String, cfg: ConvertConfig, hasVideo: Boolean, hasAudio: Boolean): List<String> {
        val args = mutableListOf("-y", "-i", input)
        val audioOnly = cfg.audioOnly || !hasVideo

        if (audioOnly) {
            args += "-vn"
        } else {
            when (cfg.videoCodec) {
                "copy" -> args += listOf("-c:v", "copy")
                "none" -> args += "-vn"
                else -> {
                    args += listOf("-c:v", cfg.videoCodec)
                    if (cfg.videoCodec in setOf("libx264", "libx265")) {
                        args += listOf("-preset", cfg.preset)
                    }
                    if (cfg.crf > 0 && cfg.videoCodec in setOf("libx264", "libx265")) {
                        args += listOf("-crf", cfg.crf.toString())
                    } else {
                        args += listOf("-b:v", "${cfg.videoBitrateKbps}k")
                    }
                    val filters = mutableListOf<String>()
                    when {
                        cfg.targetWidth > 0 && cfg.targetHeight > 0 ->
                            filters += "scale=${even(cfg.targetWidth)}:${even(cfg.targetHeight)}"
                        cfg.targetHeight > 0 -> filters += "scale=-2:${even(cfg.targetHeight)}"
                        cfg.targetWidth > 0 -> filters += "scale=${even(cfg.targetWidth)}:-2"
                    }
                    if (filters.isNotEmpty()) args += listOf("-vf", filters.joinToString(","))
                    if (cfg.fpsLimit > 0) args += listOf("-r", cfg.fpsLimit.toString())
                    if (cfg.videoCodec != "libvpx-vp9") args += listOf("-pix_fmt", "yuv420p")
                }
            }
        }

        if (hasAudio) {
            when (cfg.audioCodec) {
                "none" -> args += "-an"
                "copy" -> args += listOf("-c:a", "copy")
                "pcm_s16le", "flac" -> args += listOf("-c:a", cfg.audioCodec)
                else -> args += listOf("-c:a", cfg.audioCodec, "-b:a", "${cfg.audioBitrateKbps}k")
            }
        }

        finish(args, output)
        return args
    }

    fun trim(input: String, output: String, cfg: TrimConfig, videoKbps: Long = 0): List<String> {
        val start = secondsString(cfg.startMs)
        val length = secondsString((cfg.endMs - cfg.startMs).coerceAtLeast(1))
        val args = mutableListOf<String>()
        when (cfg.mode) {
            TrimMode.COPY -> args += listOf(
                "-y", "-ss", start, "-i", input, "-t", length,
                "-c", "copy", "-avoid_negative_ts", "make_zero"
            )
            TrimMode.FAST_ENCODE -> args += listOf(
                "-y", "-ss", start, "-i", input, "-t", length,
                "-c:v", Codecs.DEFAULT_VIDEO, "-preset", "veryfast"
            ) + rateArgs(videoKbps) + listOf("-pix_fmt", "yuv420p", "-c:a", "copy")
            TrimMode.PRECISE -> args += listOf(
                "-y", "-i", input, "-ss", start, "-t", length,
                "-c:v", Codecs.DEFAULT_VIDEO, "-preset", "medium"
            ) + rateArgs(videoKbps) + listOf("-pix_fmt", "yuv420p", "-c:a", "copy")
        }
        finish(args, output)
        return args
    }

    /**
     * 按帧号删除区间。
     *
     * [FrameDeleteMode.RECODE]：视频用 select 丢弃命中帧 + setpts 重排时间戳；
     * 音频用 aselect 按「同一段时间」丢弃对应样本 + asetpts 重排，做到音画同步删除，
     * 而不是让音频保持原轨后被 -shortest 草草截断。只有 fps > 0 时才能把帧号换算成秒，
     * 因此 [fps] 由 UI 传入；为 0 时退化为「只删视频、音频重编码直通」，保持旧调用兼容。
     *
     * [FrameDeleteMode.STREAM_COPY]：把「保留区间」写成 concat demuxer 的
     * inpoint/outpoint 列表，整条命令只有 -c copy，不解码不重编码，速度极快；
     * 代价是切点吸附到关键帧，帧级精度不如重编码。[concatListPath] 由 UI 落盘后传入。
     *
     * -vf / -af 值里的单引号由 FFmpeg 滤镜解析器消费，保护 between() 中的逗号；
     * CommandTokenizer.quote 会在最外层再包一层双引号，保证整条链作为一个参数。
     */
    fun deleteFrames(
        input: String,
        output: String,
        ranges: List<FrameRange>,
        videoKbps: Long = 0,
        fps: Double = 0.0,
        mode: FrameDeleteMode = FrameDeleteMode.RECODE,
        concatListPath: String = "",
        totalFrames: Long = 0L
    ): List<String> {
        val safe = FrameRanges.normalize(ranges).take(FrameRanges.MAX_RANGES)
        if (safe.isEmpty()) return emptyList()

        if (mode == FrameDeleteMode.STREAM_COPY) {
            if (concatListPath.isBlank() || fps <= 0 || totalFrames <= 0) return emptyList()
            if (FrameDelete.keepRanges(safe, fps, totalFrames).isEmpty()) return emptyList()
            val args = mutableListOf(
                "-y", "-f", "concat", "-safe", "0", "-i", concatListPath,
                "-c", "copy", "-avoid_negative_ts", "make_zero"
            )
            finish(args, output)
            return args
        }

        val vChain = "select='${FrameRanges.selectExpression(safe)}',setpts=N/FRAME_RATE/TB"
        val args = mutableListOf("-y", "-i", input, "-vf", vChain)
        val aExpr = FrameDelete.audioSelectExpression(safe, fps)
        if (aExpr.isNotBlank()) {
            args += listOf("-af", "aselect='$aExpr',asetpts=N/SR/TB")
        }
        args += listOf("-c:v", Codecs.DEFAULT_VIDEO, "-preset", "veryfast")
        args += rateArgs(videoKbps)
        // 音频经过滤镜必须重编码；没有音轨时 -c:a 会被 FFmpeg 安全忽略
        args += listOf("-c:a", "aac", "-b:a", "192k", "-pix_fmt", "yuv420p", "-shortest")
        finish(args, output)
        return args
    }

    fun crop(input: String, output: String, cfg: CropConfig, videoKbps: Long = 0): List<String> {
        val args = mutableListOf(
            "-y", "-noautorotate", "-i", input,
            "-vf", "crop=${even(cfg.width)}:${even(cfg.height)}:${even(cfg.x)}:${even(cfg.y)}",
            "-c:v", Codecs.DEFAULT_VIDEO, "-preset", "veryfast"
        ).toMutableList()
        args += rateArgs(videoKbps)
        args += listOf("-pix_fmt", "yuv420p", "-c:a", "copy")
        finish(args, output)
        return args
    }

    /** MediaRepository 导入的文件名已净化，不含引号与空格，concat 列表因此是安全的。 */
    fun concatList(paths: List<String>): String =
        paths.joinToString(separator = "\n", postfix = "\n") { "file '" + it.replace("'", "") + "'" }

    fun merge(concatFile: String, output: String, cfg: MergeConfig, videoKbps: Long = 0): List<String> {
        val args = mutableListOf("-y", "-fflags", "+genpts", "-f", "concat", "-safe", "0", "-i", concatFile)
        if (cfg.reencode) {
            args += listOf("-c:v", Codecs.DEFAULT_VIDEO, "-preset", "veryfast") +
                rateArgs(videoKbps) + listOf("-pix_fmt", "yuv420p", "-c:a", "copy")
        } else {
            args += listOf("-c", "copy")
        }
        finish(args, output)
        return args
    }

    fun transform(
        input: String,
        output: String,
        op: FlipOp,
        metadataOnly: Boolean,
        videoKbps: Long = 0
    ): List<String> {
        val args = mutableListOf<String>()
        if (metadataOnly && op.rotation != 0) {
            args += listOf(
                "-y", "-i", input, "-c", "copy",
                "-metadata:s:v:0", "rotate=${op.rotation}"
            )
        } else {
            args += listOf(
                "-y", "-noautorotate", "-i", input, "-vf", op.filter,
                "-c:v", Codecs.DEFAULT_VIDEO, "-preset", "veryfast"
            ) + rateArgs(videoKbps) + listOf("-pix_fmt", "yuv420p", "-c:a", "copy")
        }
        finish(args, output)
        return args
    }

    fun mute(input: String, output: String): List<String> {
        val args = mutableListOf("-y", "-i", input, "-an", "-c:v", "copy")
        finish(args, output)
        return args
    }

    fun extractAudio(input: String, output: String, cfg: ExtractConfig): List<String> {
        val codec = when (cfg.format.lowercase()) {
            "m4a" -> "aac"
            "wav" -> "pcm_s16le"
            "flac" -> "flac"
            "ogg" -> "libvorbis"
            else -> "libmp3lame"
        }
        val lossless = codec in setOf("pcm_s16le", "flac")
        val args = mutableListOf("-y")
        if (cfg.startMs > 0) args += listOf("-ss", secondsString(cfg.startMs))
        args += listOf("-i", input, "-vn", "-c:a", codec)
        if (!lossless) args += listOf("-b:a", "${cfg.bitrateKbps}k")
        if (cfg.endMs > cfg.startMs) {
            args += listOf("-t", secondsString(cfg.endMs - cfg.startMs))
        }
        finish(args, output)
        return args
    }

    /** 两遍调色板 GIF：palettegen + paletteuse，画质明显优于直接 -vf。 */
    fun gif(input: String, output: String, cfg: GifConfig): List<String> {
        val width = even(cfg.width).coerceAtLeast(2)
        val fps = cfg.fps.coerceIn(1, 30)
        val args = mutableListOf<String>("-y")
        if (cfg.startMs > 0) args += listOf("-ss", secondsString(cfg.startMs))
        args += listOf("-i", input)
        if (cfg.endMs > cfg.startMs) {
            args += listOf("-t", secondsString(cfg.endMs - cfg.startMs))
        }
        val scaling = "fps=$fps,scale=$width:-1:flags=lanczos"
        val filter = if (cfg.highQuality) {
            "[0:v] $scaling,split [a][b];[a] palettegen=stats_mode=diff [p];" +
                "[b][p] paletteuse=dither=bayer:bayer_scale=3"
        } else {
            scaling
        }
        args += listOf("-filter_complex", filter, "-loop", cfg.loop.toString(), output)
        return args
    }

    /**
     * 亮度调整：滤镜链必须以 format=yuv420p 开头，否则 10-bit / yuvj / 旋转帧
     * 注入 buffersrc 时会报 `Failed to inject frame into filter network: Invalid argument`。
     * 滤镜与 -c copy 互斥，因此强制重编码并显式指定 -pix_fmt yuv420p。
     */
    fun brightness(
        input: String,
        output: String,
        cfg: BrightnessConfig,
        videoKbps: Long = 0
    ): List<String> {
        val b = cfg.brightness.coerceIn(-1f, 1f)
        val c = cfg.contrast.coerceIn(-2f, 2f)
        val s = cfg.saturation.coerceIn(0f, 3f)
        val g = cfg.gamma.coerceIn(0.1f, 10f)
        val chain = "format=yuv420p,eq=" +
            "brightness=%.2f:contrast=%.2f:saturation=%.2f:gamma=%.2f".format(
                java.util.Locale.US, b, c, s, g
            )
        val args = mutableListOf(
            "-y", "-i", input, "-vf", chain,
            "-c:v", Codecs.DEFAULT_VIDEO, "-preset", "veryfast"
        )
        args += rateArgs(videoKbps)
        args += listOf("-c:a", "copy", "-pix_fmt", "yuv420p")
        finish(args, output)
        return args
    }

    /** 九宫格位置对应的 overlay 参数（纯 Kotlin，不含分号，命令行页可直接执行）。 */
    private fun overlayExpression(position: String, margin: Int): String {
        val m = margin.coerceIn(0, 400)
        val (x, y) = when (position.lowercase(java.util.Locale.US)) {
            "tl" -> "$m" to "$m"
            "tc" -> "(W-w)/2" to "$m"
            "tr" -> "W-w-$m" to "$m"
            "ml" -> "$m" to "(H-h)/2"
            "mc" -> "(W-w)/2" to "(H-h)/2"
            "mr" -> "W-w-$m" to "(H-h)/2"
            "bl" -> "$m" to "H-h-$m"
            "bc" -> "(W-w)/2" to "H-h-$m"
            else -> "W-w-$m" to "H-h-$m" // br 默认右下
        }
        return "x=$x:y=$y"
    }

    /**
     * 文字水印：把文字渲染成 PNG 作为第二输入，用 overlay 合成。
     * FFmpeg 9.0.2 已有原生 drawtext，但这里保留 PNG 方案是为了支持预览里的
     * 拖动定位、双指缩放与透明度调节；单条滤镜链不含分号，命令行页可直接执行。
     */
    fun watermark(
        input: String,
        output: String,
        cfg: WatermarkConfig,
        videoKbps: Long = 0
    ): List<String> {
        val pos = overlayExpression(cfg.position, cfg.margin)
        val chain = "[0:v][1:v]overlay=$pos:format=auto,format=yuv420p"
        val args = mutableListOf(
            "-y", "-i", input, "-i", cfg.overlayPng,
            "-filter_complex", chain,
            "-c:v", Codecs.DEFAULT_VIDEO, "-preset", "veryfast"
        )
        args += rateArgs(videoKbps)
        args += listOf("-c:a", "copy", "-pix_fmt", "yuv420p")
        finish(args, output)
        return args
    }

    /**
     * 烧录字幕：FFmpeg 9.0.2 已内置原生 drawtext（freetype / fontconfig / fribidi /
     * harfbuzz），主路径把 SRT / VTT / ASS / SSA 解析成逐条 drawtext，配本机 CJK 字体
     * 与 enable 时间轴，不再沿用 8.1.x 的 libass 替代方案。
     * 字幕超过 MAX_CUES 条或格式无法解析时，回落到 libass subtitles 滤镜保证可用；
     * fontsdir 必须显式指向系统字体目录，否则 fontconfig 仍按编译机路径找中文。
     */
    fun burnSubtitles(
        input: String,
        output: String,
        subtitlePath: String,
        videoKbps: Long = 0,
        fontsDir: String = "/system/fonts"
    ): List<String> {
        val cues = runCatching {
            SubtitleCues.parse(File(subtitlePath).readText(Charsets.UTF_8), subtitlePath)
        }.getOrDefault(emptyList())
        val drawtext = if (cues.isNotEmpty() && cues.size <= SubtitleCues.MAX_CUES) {
            SubtitleCues.drawtextChain(cues, SubtitleCues.systemFont())
        } else ""
        val chain = if (drawtext.isNotBlank()) {
            "format=yuv420p,$drawtext"
        } else {
            "format=yuv420p,subtitles=filename=$subtitlePath:fontsdir=$fontsDir"
        }
        val args = mutableListOf(
            "-y", "-i", input, "-vf", chain,
            "-c:v", Codecs.DEFAULT_VIDEO, "-preset", "veryfast"
        )
        args += rateArgs(videoKbps)
        args += listOf("-c:a", "copy", "-pix_fmt", "yuv420p")
        finish(args, output)
        return args
    }

    /** atempo 只接受 0.5–2.0，超出时用两级串联覆盖 0.25–4.0。 */
    fun atempoChain(speed: Float): String {
        val s = speed.coerceIn(0.25f, 4f)
        if (kotlin.math.abs(s - 1f) < 0.001f) return ""
        fun f(v: Float): String = "%.4f".format(java.util.Locale.US, v)
        if (s in 0.5f..2f) return "atempo=${f(s)}"
        val part = kotlin.math.sqrt(s.toDouble()).toFloat()
        return "atempo=${f(part)},atempo=${f(part)}"
    }

    fun audioCodec(format: String): String = when (format.lowercase(java.util.Locale.US)) {
        "m4a", "aac" -> "aac"
        "wav" -> "pcm_s16le"
        "flac" -> "flac"
        "ogg" -> "libvorbis"
        else -> "libmp3lame"
    }

    private fun audioEncodeArgs(cfg: AudioOutputConfig): List<String> {
        val codec = audioCodec(cfg.format)
        val lossless = codec in setOf("pcm_s16le", "flac")
        val args = mutableListOf("-c:a", codec, "-ar", cfg.sampleRate.coerceIn(8000, 96000).toString())
        // 0 =「不变」：不显式指定 -b:a，由编码器按默认 / VBR 策略决定码率
        if (!lossless && cfg.bitrateKbps > 0) {
            args += listOf("-b:a", "${cfg.bitrateKbps.coerceIn(32, 320)}k")
        }
        return args
    }

    /**
     * 音频拼接：每个输入先按自身音量调整并统一到相同采样率 / 声道，
     * 顺序模式用 concat 或 acrossfade 串联，合并模式用 amix 叠成一条音轨。
     */
    fun audioJoin(inputs: List<String>, output: String, cfg: AudioJoinConfig): List<String> {
        if (inputs.isEmpty()) return emptyList()
        val args = mutableListOf<String>("-y")
        inputs.forEach { args += listOf("-i", it) }
        val rate = cfg.output.sampleRate.coerceIn(8000, 96000)
        // concat / acrossfade 要求各路采样格式、采样率与声道布局完全一致
        val norm = "aformat=sample_fmts=fltp:sample_rates=$rate:channel_layouts=stereo"
        val labels = mutableListOf<String>()
        val filters = mutableListOf<String>()
        inputs.forEachIndexed { index, _ ->
            val vol = cfg.volumes.getOrElse(index) { 1f }.coerceIn(0f, 3f)
            "%.2f".format(java.util.Locale.US, vol).let { v ->
                filters += "[${index}:a]volume=$v,$norm[a$index]"
            }
            labels += "[a$index]"
        }
        when {
            cfg.mode == AudioJoinMode.MIX -> {
                filters += labels.joinToString("") +
                    "amix=inputs=${inputs.size}:duration=longest:normalize=0[out]"
            }
            cfg.crossfadeMs > 0 && inputs.size > 1 -> {
                var current = labels.first()
                for (index in 1 until inputs.size) {
                    val next = if (index == inputs.size - 1) "[out]" else "[x$index]"
                    val seconds = (cfg.crossfadeMs / 1000.0).coerceAtLeast(0.1)
                    val d = "%.2f".format(java.util.Locale.US, seconds)
                    filters += "$current${labels[index]}acrossfade=d=$d:c1=tri:c2=tri$next"
                    current = next
                }
            }
            else -> filters += labels.joinToString("") +
                "concat=n=${inputs.size}:v=0:a=1[out]"
        }
        args += listOf("-filter_complex", filters.joinToString(";"), "-map", "[out]", "-vn")
        args += audioEncodeArgs(cfg.output)
        finish(args, output)
        return args
    }

    /** 音频剪切：先按源时间轴截取，再变速；输出时长约为截取时长 / 速度。 */
    fun audioCut(input: String, output: String, cfg: AudioCutConfig): List<String> {
        val args = mutableListOf<String>("-y")
        if (cfg.startMs > 0) args += listOf("-ss", secondsString(cfg.startMs))
        args += listOf("-i", input, "-vn")
        if (cfg.endMs > cfg.startMs) {
            args += listOf("-t", secondsString(cfg.endMs - cfg.startMs))
        }
        atempoChain(cfg.speed).takeIf { it.isNotBlank() }?.let {
            args += listOf("-filter:a", it)
        }
        args += audioEncodeArgs(cfg.output)
        finish(args, output)
        return args
    }

    /**
     * 视频变速：setpts 缩放视频时间轴，atempo 同步处理音频，两条轨道倍率一致。
     * 0.25–4.0 倍范围内 atempo 可能串联两级，[atempoChain] 已处理。
     */
    fun speed(
        input: String,
        output: String,
        speed: Float,
        videoKbps: Long = 0,
        hasAudio: Boolean = true
    ): List<String> {
        val s = speed.coerceIn(0.25f, 4f)
        val args = mutableListOf<String>("-y", "-i", input)
        if (kotlin.math.abs(s - 1f) > 0.001f) {
            args += listOf("-vf", "setpts=PTS/%.4f".format(java.util.Locale.US, s))
            if (hasAudio) {
                atempoChain(s).takeIf { it.isNotBlank() }?.let { args += listOf("-af", it) }
            }
        }
        args += listOf("-c:v", Codecs.DEFAULT_VIDEO, "-preset", "veryfast")
        args += rateArgs(videoKbps)
        args += listOf("-c:a", "aac", "-b:a", "192k", "-pix_fmt", "yuv420p")
        finish(args, output)
        return args
    }

    /** 提取内封的第一条字幕流为 SRT；PGS / DVB 等图形字幕无法转文本，会由 FFmpeg 报错。 */
    fun extractSubtitle(input: String, output: String): List<String> {
        val args = mutableListOf("-y", "-i", input, "-map", "0:s:0", "-c:s", "srt")
        finish(args, output)
        return args
    }

    /** 嵌入软字幕（不烧进画面）：mp4/mov 用 mov_text，mkv 用 srt，其余轨道直接复制。 */
    fun embedSubtitle(
        input: String,
        subtitle: String,
        output: String,
        container: String
    ): List<String> {
        val codec = if (container.lowercase(java.util.Locale.US) == "mkv") "srt" else "mov_text"
        val args = mutableListOf(
            "-y", "-i", input, "-i", subtitle,
            "-map", "0", "-map", "1:0",
            "-c", "copy", "-c:s", codec
        )
        finish(args, output)
        return args
    }

    /** 统一用 Locale.US 输出浮点，避免某些语言环境把小数点写成逗号而破坏滤镜表达式。 */
    private fun num(value: Double): String = String.format(java.util.Locale.US, "%.4f", value)

    /** overlay 的 x/y / enable 表达式：动态模式用 t（秒）逐帧求值。 */
    private fun overlayMotion(cfg: WatermarkConfig, motion: WatermarkMotion): String {
        val m = cfg.margin.coerceIn(0, 400)
        // 预览里拖动过就直接用归一化坐标；九宫格预设只在未拖动时生效。
        val fixed = if (cfg.normX >= 0f && cfg.normY >= 0f) {
            "x=${num(cfg.normX.coerceIn(0f, 1f).toDouble())}*(W-w)" +
                ":y=${num(cfg.normY.coerceIn(0f, 1f).toDouble())}*(H-h)"
        } else {
            overlayExpression(cfg.position, m)
        }
        val pos = when (motion) {
            WatermarkMotion.STATIC, WatermarkMotion.BLINK -> fixed
            WatermarkMotion.SLIDE -> "x='mod(t*60,max(W-w,1))':y='H-h-$m'"
            WatermarkMotion.FLOAT -> "x='W-w-$m':y='(H-h)/2+sin(t*2)*(H-h)/4'"
            // 质数步进的确定性伪随机：不依赖 random()（部分构建未编译），
            // 每 0.5 秒跳一次位置并间歇隐藏，肉眼看起来是随机出现。
            WatermarkMotion.RANDOM ->
                "x='mod(floor(t*2)*7919,max(W-w,1))':y='mod(floor(t*2)*104729,max(H-h,1))'"
            // 三角波：x 4 秒一回合、y 6 秒一回合，撞到画面边缘自动折返。
            WatermarkMotion.BOUNCE ->
                "x='(W-w)*abs(1-mod(t/4,2))':y='(H-h)*abs(1-mod(t/6,2))'"
        }
        val enables = mutableListOf<String>()
        if (motion == WatermarkMotion.BLINK) enables += "lt(mod(t,2),1)"
        if (motion == WatermarkMotion.RANDOM) enables += "lt(mod(floor(t*2),7),5)"
        if (cfg.endSec > cfg.startSec && cfg.startSec >= 0.0) {
            enables += "between(t,${num(cfg.startSec)},${num(cfg.endSec)})"
        }
        // 多个条件相乘（0/1 逻辑与）：既落在显示时间段内，又满足动态效果自身的可见性。
        val enable = if (enables.isEmpty()) "" else ":enable='${enables.joinToString("*")}'"
        return "$pos:eval=frame:format=auto$enable"
    }

    /** 文字 / 图片水印（第二输入是 PNG），支持动态效果、透明度、缩放与显示时间段。 */
    fun watermarkDynamic(
        input: String,
        output: String,
        cfg: WatermarkConfig,
        motion: WatermarkMotion,
        videoKbps: Long = 0
    ): List<String> {
        val alpha = cfg.opacity.coerceIn(0.05f, 1f)
        val scale = cfg.scale.coerceIn(0.1f, 4f)
        val preFilters = mutableListOf<String>()
        if (alpha < 0.999f) {
            preFilters += "format=rgba"
            preFilters += "colorchannelmixer=aa=${num(alpha.toDouble())}"
        }
        if (kotlin.math.abs(scale - 1f) > 0.001f) {
            val s = num(scale.toDouble())
            preFilters += "scale=iw*$s:ih*$s"
        }
        val prepared = preFilters.isNotEmpty()
        val chain = buildString {
            if (prepared) append("[1:v]").append(preFilters.joinToString(",")).append("[wm];")
            append("[0:v]").append(if (prepared) "[wm]" else "[1:v]")
            append("overlay=").append(overlayMotion(cfg, motion))
            append(",format=yuv420p")
        }
        val args = mutableListOf(
            "-y", "-i", input, "-i", cfg.overlayPng,
            "-filter_complex", chain,
            "-c:v", Codecs.DEFAULT_VIDEO, "-preset", "veryfast"
        )
        args += rateArgs(videoKbps)
        args += listOf("-c:a", "copy", "-pix_fmt", "yuv420p")
        finish(args, output)
        return args
    }

    /**
     * 区域去水印：模糊 / 马赛克用 split + crop + 局部处理再 overlay 回去；
     * 智能填补用 delogo（根据区域边缘像素插值，适合小面积台标）。
     *
     * 滤镜链统一以 format=yuv420p 开头，否则 10-bit / yuvj / 旋转帧注入 buffersrc
     * 会报 `Failed to inject frame into filter network: Invalid argument`；
     * x/y/w/h 一律偶数化，delogo 四周至少留 2px 参考带；-noautorotate 避免旋转
     * 元数据让坐标系错乱。传入视频宽高时把区域夹到画面内（四周各留 2px）。
     * FFmpeg 9.0.2 移除了 delogo 的 band 选项（旧版 band=1 会报 Option not found），
     * 这里只传 x/y/w/h，边缘模糊带由滤镜内部固定处理。
     */
    fun removeWatermark(
        input: String,
        output: String,
        cfg: RemoveWatermarkConfig,
        videoKbps: Long = 0,
        videoWidth: Int = 0,
        videoHeight: Int = 0
    ): List<String> {
        val x0 = even(cfg.x.coerceAtLeast(0))
        val y0 = even(cfg.y.coerceAtLeast(0))
        val w0 = even(cfg.width.coerceIn(2, 4096)).coerceAtLeast(2)
        val h0 = even(cfg.height.coerceIn(2, 4096)).coerceAtLeast(2)
        // delogo 需要四周参考像素：x>=2, y>=2, x+w<=W-2, y+h<=H-2
        val x = if (videoWidth >= 8) x0.coerceIn(2, (videoWidth - 4).coerceAtLeast(2)) else x0.coerceAtLeast(2)
        val y = if (videoHeight >= 8) y0.coerceIn(2, (videoHeight - 4).coerceAtLeast(2)) else y0.coerceAtLeast(2)
        val w = if (videoWidth >= 8) w0.coerceIn(2, (videoWidth - x - 2).coerceAtLeast(2)) else w0
        val h = if (videoHeight >= 8) h0.coerceIn(2, (videoHeight - y - 2).coerceAtLeast(2)) else h0
        val chain = when (cfg.mode) {
            RemoveMode.INPAINT -> "[0:v]format=yuv420p,delogo=x=$x:y=$y:w=$w:h=$h[outv]"
            else -> {
                val effect = when (cfg.mode) {
                    RemoveMode.BLUR -> "boxblur=${cfg.intensity.coerceIn(2, 30)}:2"
                    else -> {
                        val smallW = even(w / 12).coerceAtLeast(2)
                        val smallH = even(h / 12).coerceAtLeast(2)
                        "scale=$smallW:$smallH,scale=$w:$h:flags=neighbor"
                    }
                }
                "[0:v]format=yuv420p,split[base][region];" +
                    "[region]crop=$w:$h:$x:$y,$effect[patched];" +
                    "[base][patched]overlay=$x:$y:format=auto,format=yuv420p[outv]"
            }
        }
        val args = mutableListOf(
            "-y", "-noautorotate", "-i", input,
            "-filter_complex", chain,
            "-map", "[outv]", "-map", "0:a?",
            "-c:v", Codecs.DEFAULT_VIDEO, "-preset", "veryfast"
        )
        args += rateArgs(videoKbps)
        args += listOf("-c:a", "copy", "-pix_fmt", "yuv420p")
        finish(args, output)
        return args
    }

    fun preview(args: List<String>): String = CommandTokenizer.join(args)
}

object Estimate {
    /** kbps × 时长 → 字节。1 kbps = 125 byte/s。 */
    fun targetBytes(videoKbps: Int, audioKbps: Int, durationMs: Long): Long {
        if (durationMs <= 0) return 0L
        return ((videoKbps.toDouble() + audioKbps.toDouble()) * 125.0 * durationMs / 1000.0).toLong()
    }

    /** CRF 模式没有固定码率，用原文件体积 × 经验系数给出量级估计。 */
    fun crfBytes(originalBytes: Long, crf: Int): Long {
        val factor = when {
            crf <= 16 -> 1.5
            crf <= 20 -> 1.1
            crf <= 23 -> 0.8
            crf <= 28 -> 0.5
            else -> 0.3
        }
        return (originalBytes * factor).toLong()
    }

    fun convertBytes(cfg: ConvertConfig, media: MediaInfo): Long {
        val duration = media.durationMs
        if (duration <= 0) return 0L
        val audioKbps = when {
            !media.hasAudio || cfg.audioCodec == "none" -> 0
            cfg.audioCodec == "copy" -> media.audio?.bitrateKbps?.toInt() ?: 0
            cfg.audioCodec in setOf("flac", "pcm_s16le") -> 1411
            else -> cfg.audioBitrateKbps
        }
        // CRF 为恒定质量模式，无法按码率推算，直接基于原文件体积给量级估计
        if (cfg.crf > 0 && cfg.videoCodec in setOf("libx264", "libx265")) {
            return crfBytes(media.sizeBytes, cfg.crf)
        }
        val videoKbps = when {
            cfg.audioOnly || !media.hasVideo -> 0
            cfg.videoCodec == "copy" -> media.video?.bitrateKbps?.toInt() ?: 0
            else -> cfg.videoBitrateKbps
        }
        return targetBytes(videoKbps, audioKbps, duration)
    }

    fun isEstimateRough(cfg: ConvertConfig): Boolean =
        cfg.crf > 0 && cfg.videoCodec in setOf("libx264", "libx265")

    /** 音频产物估算：无损格式按 PCM 1411 kbps 计，其余按目标码率。 */
    fun audioBytes(cfg: AudioOutputConfig, durationMs: Long): Long {
        if (durationMs <= 0) return 0L
        val lossless = cfg.format.lowercase() in setOf("wav", "flac")
        // 「不变」没有可预估的目标码率，按 192 kbps 给出一个粗估值
        val kbps = if (cfg.bitrateKbps > 0) cfg.bitrateKbps else 192
        return targetBytes(0, if (lossless) 1411 else kbps, durationMs)
    }

    /** GIF 体积粗估：帧数 × 像素数 × 调色板压缩经验系数。 */
    fun gifBytes(width: Int, fps: Int, durationMs: Long): Long {
        if (width <= 0 || fps <= 0 || durationMs <= 0) return 0L
        val frames = durationMs / 1000.0 * fps
        val height = width * 9.0 / 16.0
        return (frames * width * height * 0.3).toLong()
    }
}
