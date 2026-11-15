package com.framebetween.app.core

import com.framebetween.app.core.L10n

/**
 * 硬件解码参数注入（纯 Kotlin，便于单测）。
 *
 * 自建 FFmpeg 9.0.2 AAR 已启用 `--enable-lib-android-media-codec` 与 `--enable-jni`，
 * 因此开启开关后在第一个输入前加 `-hwaccel mediacodec` 即可让
 * FFmpeg 走 Android MediaCodec 硬解；编码仍由 libx264 / libx265 完成，避免
 * mediacodec 编码器对色彩格式与码率控制的诸多限制破坏滤镜链与画质。
 *
 * 以下情况不注入，避免改变命令语义或直接报错：
 * - 用户命令里已显式指定任何 hwaccel 相关参数
 * - 整条命令是流复制（-c copy / -c:v copy / -codec:v copy …），没有解码过程
 * - 纯音频输出（-vn）或没有输入文件
 * - 命令含滤镜链（-vf / -filter_complex / -af …）：硬解输出帧需回读到 CPU 内存，
 *   帧格式与滤镜 buffersrc 不匹配，会报 `Failed to inject frame into filter network`。
 *
 * [decide] 在返回结果的同时给出简短原因，供 FFmpegEngine 写日志让用户直观看到
 * 开关是否生效；[withHardwareDecode] 保留旧签名，内部直接复用 [decide]。
 */
object HardwareCodec {

    const val DECODER = "mediacodec"

    data class Decision(val args: List<String>, val injected: Boolean, val reason: String)

    private val HWACCEL_FLAGS = setOf(
        "-hwaccel", "-hwaccel_device", "-hwaccel_output_format", "-hwaccel_pix_fmt"
    )

    /** 含这些 flag 的命令一律跳过硬解，避免硬解帧格式与滤镜 buffersrc 冲突。 */
    private val FILTER_FLAGS = setOf(
        "-vf", "-filter:v", "-filter_complex", "-lavfi", "-af", "-filter:a"
    )

    fun withHardwareDecode(
        args: List<String>,
        enabled: Boolean,
        supported: Boolean
    ): List<String> = decide(args, enabled, supported).args

    fun decide(args: List<String>, enabled: Boolean, supported: Boolean): Decision {
        if (!enabled) return Decision(args, false, L10n.t("硬件解码已关闭"))
        if (!supported) return Decision(args, false, L10n.t("设备不支持 MediaCodec 硬解"))
        if (args.isEmpty()) return Decision(args, false, L10n.t("命令没有输入文件"))
        if (args.any { it in HWACCEL_FLAGS }) return Decision(args, false, L10n.t("命令已指定 hwaccel 参数"))
        if (args.contains("-vn")) return Decision(args, false, L10n.t("纯音频输出"))
        if (isStreamCopy(args)) return Decision(args, false, L10n.t("流复制命令无需解码"))
        if (args.any { it in FILTER_FLAGS }) {
            return Decision(args, false, L10n.t("命令含滤镜链，跳过硬解以避免帧格式错误"))
        }
        val inputIndex = args.indexOf("-i")
        if (inputIndex < 0) return Decision(args, false, L10n.t("命令没有输入文件"))
        val effective = args.toMutableList().apply {
            addAll(inputIndex, listOf("-hwaccel", DECODER))
        }
        return Decision(effective, true, L10n.t("已注入 MediaCodec 硬解"))
    }

    /**
     * 流复制写法很多：-c / -c:v / -c:v:0 / -c:a / -codec / -codec:v /
     * -codec:a / -vcodec / -acodec 等，只要 flag 属于 codec 类且值为 "copy" 即命中。
     */
    private fun isStreamCopy(args: List<String>): Boolean =
        args.windowed(2).any { (flag, value) -> isCodecFlag(flag) && value == "copy" }

    private fun isCodecFlag(flag: String): Boolean =
        flag.startsWith("-c") || flag.startsWith("-vcodec") || flag.startsWith("-acodec")
}
