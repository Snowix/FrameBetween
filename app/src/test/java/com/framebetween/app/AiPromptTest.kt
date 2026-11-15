package com.framebetween.app

import com.framebetween.app.core.AiPrompt
import com.framebetween.app.core.AudioStream
import com.framebetween.app.core.MediaInfo
import com.framebetween.app.core.VideoStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 提示词占位符替换与 ffprobe 媒体信息摘要。 */
class AiPromptTest {

    @Test
    fun `replaces request and media info but keeps in out placeholders`() {
        val prompt = AiPrompt.build(AiPrompt.TEMPLATE, "把视频压缩到 50MB", "文件大小：120 MB")
        assertTrue(prompt.contains("- 目标功能/效果：把视频压缩到 50MB"))
        assertTrue(prompt.contains("- 媒体信息：文件大小：120 MB"))
        assertTrue(prompt.contains("`{in}`"))
        assertTrue(prompt.contains("- 输入路径：{in}"))
        assertTrue(prompt.contains("- 输出路径：{out}"))
        assertFalse(prompt.contains("{剪辑需求}"))
        assertFalse(prompt.contains("{媒体信息}"))
        assertTrue(prompt.contains("- 是否允许覆盖输出：<否>"))
    }

    @Test
    fun `user text containing a placeholder is never substituted twice`() {
        val prompt = AiPrompt.build(AiPrompt.TEMPLATE, "需求里带 {媒体信息} 字样", "媒体信息正文")
        assertTrue(prompt.contains("- 目标功能/效果：需求里带 {媒体信息} 字样"))
        assertTrue(prompt.contains("- 媒体信息：媒体信息正文"))
        assertFalse(prompt.contains("- 目标功能/效果：需求里带 媒体信息正文 字样"))
    }

    @Test
    fun `blank request degrades to an explicit assumption hint`() {
        val prompt = AiPrompt.build(AiPrompt.TEMPLATE, "  ", "")
        assertTrue(prompt.contains("假设"))
        assertTrue(prompt.contains("未选择输入文件"))
    }

    @Test
    fun `template carries two output sections and the brace command marker`() {
        listOf(
            "A. 最终执行命令",
            "B. 风险与备选方案",
            "- 目标平台：bash（不支持 shell 管道与重定向）",
            "最终代码以{ffmpeg命令}标记，最多给出3个方案",
            "尽可能给用户提供简洁的命令",
            "明确使用 `-map` 选择流",
            "禁止覆盖用 `-n`"
        ).forEach { assertTrue("缺少：$it", AiPrompt.TEMPLATE.contains(it)) }
        assertFalse(AiPrompt.TEMPLATE.contains("A. 需求理解与假设"))
        assertFalse(AiPrompt.TEMPLATE.contains("C. 输出验证命令/检查项"))
    }

    @Test
    fun `diagnose template replaces all five placeholders`() {
        val prompt = AiPrompt.buildDiagnose(
            "把视频转成 H.265",
            "时长：00:01:30",
            "FFmpegKitNext 9.0.2 / FFmpeg 9.0.2",
            "-i 输入路径 -c:v libx265 输出路径",
            "Unknown encoder 'libx266'"
        )
        listOf(
            "- 目标功能/效果：把视频转成 H.265",
            "- 媒体信息：时长：00:01:30",
            "- FFmpeg 版本：FFmpegKitNext 9.0.2 / FFmpeg 9.0.2",
            "-i 输入路径 -c:v libx265 输出路径",
            "Unknown encoder 'libx266'",
            "手机软件内置ffmpeg（不支持 shell 管道与重定向）"
        ).forEach { assertTrue("缺少：$it", prompt.contains(it)) }
        listOf(
            "{媒体信息}", "{剪辑需求}", "{FFmpeg版本}", "{脱敏命令}", "{错误信息}"
        ).forEach { assertFalse("未替换：$it", prompt.contains(it)) }
    }

    @Test
    fun `diagnose request containing a placeholder is not substituted twice`() {
        val prompt = AiPrompt.buildDiagnose(
            "需求里有 {媒体信息} 字样", "媒体信息正文", "9.0.2", "-i 输入路径 输出路径", "err"
        )
        assertTrue(prompt.contains("- 目标功能/效果：需求里有 {媒体信息} 字样"))
        assertTrue(prompt.contains("- 媒体信息：媒体信息正文"))
    }

    @Test
    fun `blank diagnose fields degrade to explicit hints`() {
        val prompt = AiPrompt.buildDiagnose("  ", "", "", "", "")
        assertTrue(prompt.contains("请根据命令与错误信息推断目标"))
        assertTrue(prompt.contains("无法提供 ffprobe 媒体信息"))
        assertTrue(prompt.contains("（命令为空）"))
        assertTrue(prompt.contains("没有可用的错误日志"))
    }

    @Test
    fun `evaluate template replaces all four placeholders`() {
        val prompt = AiPrompt.buildEvaluate(
            "把 1080p 压到 720p 且不超过 20MB",
            "输入文件：a.mp4\n文件大小：120.00 MB",
            "-i 输入路径 -vf scale=-2:720 -c:v libx265 输出路径",
            "输出文件：a_1110123456.mp4\n文件大小：18.00 MB"
        )
        listOf(
            "- 剪辑要求：把 1080p 压到 720p 且不超过 20MB",
            "- 输入媒体信息：输入文件：a.mp4",
            "- 输出媒体信息：输出文件：a_1110123456.mp4",
            "- 我执行的完整命令：-i 输入路径 -vf scale=-2:720 -c:v libx265 输出路径",
            "对比处理前后文件信息",
            "如果满足要求则考虑是否有可以改善地方",
            "【处理过程及目标】",
            "1、尽可能简洁地回答，不需要长篇大论。",
            "2、如果没有较大的偏差，不要用太多假设性内容。"
        ).forEach { assertTrue("缺少：$it", prompt.contains(it)) }
        listOf(
            "{剪辑需求}", "{媒体信息}", "{脱敏命令}", "{输出媒体信息}"
        ).forEach { assertFalse("未替换：$it", prompt.contains(it)) }
    }

    @Test
    fun `evaluate request containing placeholders is not substituted twice`() {
        val prompt = AiPrompt.buildEvaluate(
            "需求里有 {媒体信息} 与 {输出媒体信息} 字样", "输入摘要", "-i 输入路径 输出路径", "输出摘要"
        )
        assertTrue(prompt.contains("- 剪辑要求：需求里有 {媒体信息} 与 {输出媒体信息} 字样"))
        assertTrue(prompt.contains("- 输入媒体信息：输入摘要"))
        assertTrue(prompt.contains("- 输出媒体信息：输出摘要"))
    }

    @Test
    fun `blank evaluate fields degrade to explicit hints`() {
        val prompt = AiPrompt.buildEvaluate("  ", "", "", "")
        assertTrue(prompt.contains("请根据前后文件信息推断处理目标"))
        assertTrue(prompt.contains("未选择输入文件"))
        assertTrue(prompt.contains("（命令为空）"))
        assertTrue(prompt.contains("输出文件不可读"))
    }

    @Test
    fun `output probe summary uses output labels instead of input labels`() {
        val info = MediaInfo(path = "/sdcard/out.mp4", displayName = "out.mp4", audio = AudioStream(codec = "aac"))
        val text = AiPrompt.describe(info, "输出")
        assertTrue(text.contains("输出文件：out.mp4"))
        assertTrue(text.contains("输出流：音频"))
        assertFalse(text.contains("输入文件："))
        assertEquals("输入文件：out.mp4", AiPrompt.describe(info).lines().first())
    }

    @Test
    fun `media info describes every probe field`() {
        val info = MediaInfo(
            path = "/sdcard/a.mp4",
            displayName = "a.mp4",
            sizeBytes = 12_345_678L,
            durationMs = 125_400L,
            formatName = "mov,mp4",
            formatBitrateKbps = 4200,
            video = VideoStream(
                codec = "h264", profile = "High", pixFmt = "yuv420p",
                width = 1920, height = 1080, fps = 29.97, bitrateKbps = 4000,
                aspectRatio = "16:9"
            ),
            audio = AudioStream(
                codec = "aac", sampleRate = 48000, channels = 2,
                bitrateKbps = 128, channelLayout = "stereo"
            )
        )
        val text = AiPrompt.describe(info)
        listOf(
            "输入流：视频 + 音频", "文件大小：", "12,345,678 B", "时长：",
            "容器总码率：4200 kbps", "h264", "1920×1080", "29.970 fps",
            "yuv420p", "视频码率 4000 kbps", "aac", "48000 Hz",
            "音频码率 128 kbps", "stereo"
        ).forEach { assertTrue("缺少：$it", text.contains(it)) }
    }

    @Test
    fun `audio only file explicitly reports missing video stream`() {
        val text = AiPrompt.describe(
            MediaInfo(path = "/sdcard/b.mp3", displayName = "b.mp3", audio = AudioStream(codec = "mp3"))
        )
        assertTrue(text.contains("视频流：无"))
        assertTrue(text.contains("输入流：音频"))
        assertEquals("未选择输入文件，无 ffprobe 媒体信息。", AiPrompt.describe(null))
    }
}
