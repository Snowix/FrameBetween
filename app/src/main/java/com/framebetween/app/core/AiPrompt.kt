package com.framebetween.app.core

import java.util.Locale

/**
 * 发给模型的固定中文提示词与媒体信息摘要。
 *
 * 本文件的中文是**发给模型的任务内容**，不是界面文案，因此不参与应用内多语言翻译
 * （scripts/i18n.py 会把本文件列入豁免名单）。{in} / {out} 是留给 AI 的路径占位符，
 * 应用拿到命令后再替换成带引号的真实路径，所以模板里必须原样保留，不能被替换。
 */
object AiPrompt {

    /** [describe] 的输出标签：发给模型的「数据」，不能随界面语言切换。 */
    const val OUTPUT_SUBJECT = "输出"

    /** 「求助 AI」：只要求 A 最终命令 / B 风险与备选两段，命令用花括号标记。 */
    const val TEMPLATE = """你是一名严谨的 FFmpeg 命令行专家。请为我生成可在手机端使用bash直接执行的 FFmpeg 命令，用于处理输入视频 `{in}`，并在 `{out}` 产出符合我需求的结果。
【我的具体需求】
- 输入路径：{in}
- 输出路径：{out}
- 媒体信息：{媒体信息}
- 目标功能/效果：{剪辑需求}
- 目标平台：bash（不支持 shell 管道与重定向）
- 是否允许覆盖输出：<否>
【工作要求】
1. 确认必要的媒体信息。
2. 给出最终 FFmpeg 命令。命令必须：
- 使用 {in}、{out}代表输入和输出路径，正确处理空格和特殊字符；
- 明确使用 `-map` 选择流，避免意外丢失或多带流；
- 明确视频/音频编码器、码率控制方式、像素格式、容器格式和必要参数；
- 明确覆盖策略：允许覆盖用 `-y`，禁止覆盖用 `-n`；
- 不删除、不改写源文件；
- 尽量不使用不存在或当前环境不一定支持的滤镜/编码器；如依赖特定 FFmpeg 版本、硬件或字幕滤镜，必须标注前提。
3. 如果我的需求信息不足，先列出最多 3 个关键澄清问题；若可以用合理默认值继续，请明确写出“假设”，再给命令。
4. 对复杂滤镜，优先使用清晰可读的 `-filter_complex`；若命令过长，给出可保存为脚本/滤镜文件的方案。
5. 输出格式严格如下：
A. 最终执行命令（放在代码块中，可直接复制，尽可能给用户提供简洁的命令，例如用户只要求截取前10秒视频，就默认其他参数不变，不要给出调整码率、编码等参数花费时间重编码））
B. 风险与备选方案（如硬件编码不可用时的软件编码替代命令）
6. 不要编造 FFmpeg 参数。若某功能需要额外工具或无法由 FFmpeg 单独完成，请直接说明。
7. 最终只给专业、准确、通用、可执行的命令，不要给伪代码，最终代码以{ffmpeg命令}标记，最多给出3个方案。"""

    /**
     * 「AI 解析」：执行失败后把错误日志、脱敏命令、FFmpeg 版本与媒体参数一起交给模型排错。
     * 用户给的原文末尾以 ``` 开始命令代码块，这里补齐闭合，并追加错误信息段，
     * 以便需求三「把错误代码发送给 AI」有承载位置。
     */
    const val DIAGNOSE_TEMPLATE = """你是严谨的 FFmpeg 排错专家。我执行 FFmpeg 命令失败，请诊断原因并给出可直接执行的修正命令。
【目标】
- 媒体信息：{媒体信息}
- 目标功能/效果：{剪辑需求}
【环境】
- 操作系统与 Shell：手机软件内置ffmpeg（不支持 shell 管道与重定向）
- FFmpeg 版本：{FFmpeg版本}
【我执行的完整命令】
```
{脱敏命令}
```
【错误信息 / 失败日志】
```
{错误信息}
```
【回答要求】
- 先说明失败原因，再给出可直接执行的修正命令；命令使用 {in}、{out} 代表输入与输出路径，明确 `-map` 与 `-y` / `-n` 覆盖策略。
- 每条最终命令都用 {ffmpeg ...} 标记（花括号内为完整命令），最多 3 个方案；不要编造参数，不要使用 shell 管道与重定向。"""

    /**
     * 「AI效果评估」：处理成功后对比输入 / 输出文件的 ffprobe 结果，
     * 判断剪辑要求是否达成。四项信息按用户给定原文合并在
     * 【处理过程及目标】里，并要求回答简洁、不做多余假设。
     */
    const val EVALUATE_TEMPLATE = """你是一名FFmpeg 专家。我执行 FFmpeg 了命令，请对比处理前后文件信息，评估是否达到了我的剪辑要求，如果没有则指出偏差的地方，如果满足要求则考虑是否有可以改善地方。
【处理过程及目标】
- 输入媒体信息：{媒体信息}
- 剪辑要求：{剪辑需求}
- 我执行的完整命令：{脱敏命令}
- 输出媒体信息：{输出媒体信息}
【工作要求】
1、尽可能简洁地回答，不需要长篇大论。
2、如果没有较大的偏差，不要用太多假设性内容。"""

    /** 单次扫描替换：用户填写的内容里若恰好出现其它占位符，不会被二次替换。 */
    private fun render(template: String, values: Map<String, String>): String {
        val keys = values.keys.sortedByDescending { it.length }
        return buildString(template.length + values.values.sumOf { it.length } + 16) {
            var i = 0
            while (i < template.length) {
                val hit = keys.firstOrNull { template.startsWith(it, i) }
                if (hit != null) {
                    append(values[hit].orEmpty())
                    i += hit.length
                } else {
                    append(template[i])
                    i++
                }
            }
        }
    }

    /**
     * 只替换 {剪辑需求} 与 {媒体信息}；{in} / {out} / {ffmpeg命令} 必须原样留给 AI 与应用。
     */
    fun build(template: String, question: String, mediaInfo: String): String = render(
        template,
        mapOf(
            "{剪辑需求}" to question.trim()
                .ifBlank { "（未填写，请按通用默认值给出命令并写明假设）" },
            "{媒体信息}" to mediaInfo
                .ifBlank { "未选择输入文件，无法提供 ffprobe 媒体信息，请写明假设。" }
        )
    )

    /** 构建「AI 解析」的首轮提示词。追问走 AiClient 的多轮 history，不重复本模板。 */
    fun buildDiagnose(
        request: String,
        mediaInfo: String,
        ffmpegVersion: String,
        redactedCommand: String,
        errorText: String
    ): String = render(
        DIAGNOSE_TEMPLATE,
        mapOf(
            "{剪辑需求}" to request.trim()
                .ifBlank { "（未填写，请根据命令与错误信息推断目标）" },
            "{媒体信息}" to mediaInfo
                .ifBlank { "未选择输入文件，无法提供 ffprobe 媒体信息。" },
            "{FFmpeg版本}" to ffmpegVersion
                .ifBlank { "未知（手机内置 FFmpeg）" },
            "{脱敏命令}" to redactedCommand
                .ifBlank { "（命令为空）" },
            "{错误信息}" to errorText
                .ifBlank { "（没有可用的错误日志）" }
        )
    )

    /** 构建「AI 效果评估」首轮提示词：四项占位符一次扫描替换，互不串改。 */
    fun buildEvaluate(
        request: String,
        inputInfo: String,
        redactedCommand: String,
        outputInfo: String
    ): String = render(
        EVALUATE_TEMPLATE,
        mapOf(
            "{剪辑需求}" to request.trim()
                .ifBlank { "（未填写，请根据前后文件信息推断处理目标）" },
            "{媒体信息}" to inputInfo
                .ifBlank { "未选择输入文件，无法提供 ffprobe 媒体信息。" },
            "{脱敏命令}" to redactedCommand
                .ifBlank { "（命令为空）" },
            "{输出媒体信息}" to outputInfo
                .ifBlank { "输出文件不可读，无法提供 ffprobe 媒体信息。" }
        )
    )

    /**
     * 把 FFprobe 读到的 MediaInfo 展开成模型可直接核对的条目。
     * [subject] 为「输入」或「输出」，效果评估里输出文件也复用同一套字段，避免 AI 把前后信息混淆。
     */
    fun describe(info: MediaInfo?, subject: String = "输入"): String {
        if (info == null) return "未选择输入文件，无 ffprobe 媒体信息。"
        val lines = mutableListOf<String>()
        val streamTypes = buildList {
            if (info.hasVideo) add("视频")
            if (info.hasAudio) add("音频")
        }
        lines += "${subject}文件：${info.displayName}"
        lines += "${subject}流：${streamTypes.ifEmpty { listOf("未识别") }.joinToString(" + ")}（以下为 ffprobe 实测结果）"
        lines += "文件大小：${formatSize(info.sizeBytes)}"
        lines += "时长：${formatDuration(info.durationMs)}"
        lines += "容器格式：${info.formatName.ifBlank { "未知" }}"
        lines += "容器总码率：${info.overallBitrateKbps} kbps"
        val video = info.video
        if (video != null) {
            val fps = "%.3f".format(Locale.US, video.fps)
            val size = "${video.width}×${video.height}"
            lines += "视频流：编码器 ${video.codec.ifBlank { "未知" }}（profile ${video.profile.ifBlank { "未知" }}）；" +
                "分辨率 ${size}；帧率 ${fps} fps；像素格式 ${video.pixFmt.ifBlank { "未知" }}；" +
                "视频码率 ${video.bitrateKbps} kbps；显示宽高比 ${video.aspectRatio.ifBlank { "未知" }}"
        } else {
            lines += "视频流：无"
        }
        val audio = info.audio
        if (audio != null) {
            lines += "音频流：编码器 ${audio.codec.ifBlank { "未知" }}；采样率 ${audio.sampleRate} Hz；" +
                "声道 ${audio.channels}（${audio.channelLayout.ifBlank { "未知" }}）；" +
                "音频码率 ${audio.bitrateKbps} kbps"
        } else {
            lines += "音频流：无"
        }
        return lines.joinToString("\n")
    }

    private fun formatSize(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return "%.2f MB (%,d B)".format(Locale.US, safe / 1048576.0, safe)
    }

    private fun formatDuration(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val h = total / 3_600_000L
        val m = total % 3_600_000L / 60_000L
        val s = total % 60_000L / 1_000L
        val milli = total % 1_000L
        return "%02d:%02d:%02d.%03d（%d ms）".format(Locale.US, h, m, s, milli, total)
    }
}
