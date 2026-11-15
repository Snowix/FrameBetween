package com.framebetween.app

import com.framebetween.app.core.AiCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCommandParserTest {

    @Test
    fun `extracts fenced ffmpeg arguments`() {
        val reply = "可以这样压缩：\n```bash\nffmpeg -i {in} -c:v libx264 -b:v 2000k {out}\n```\n这样能保持码率。"
        assertEquals(
            "-i {in} -c:v libx264 -b:v 2000k {out}",
            AiCommandParser.extract(reply)
        )
    }

    @Test
    fun `extracts plain command line and strips binary prefix`() {
        val reply = "使用下面的命令：\nffmpeg -i {in} -t 10 -c copy {out}"
        assertEquals("-i {in} -t 10 -c copy {out}", AiCommandParser.extract(reply))
    }

    @Test
    fun `handles shell prompt prefix`() {
        val reply = "${'$'} ffmpeg -i {in} -an -c:v copy {out}"
        assertEquals("-i {in} -an -c:v copy {out}", AiCommandParser.extract(reply))
    }

    @Test
    fun `rejects shell pipelines and redirection`() {
        val reply = "ffmpeg -i {in} -f mp4 pipe:1 | cat > out.mp4"
        assertTrue(AiCommandParser.extract(reply).isEmpty())
    }

    @Test
    fun `returns empty for prose only`() {
        assertTrue(AiCommandParser.extract("CRF 数值越小画质越好，建议先试 23。").isEmpty())
    }

    @Test
    fun `joins multi line fenced commands`() {
        val reply = "```\n-i {in}\n-vf scale=-2:720\n-c:v libx264 {out}\n```"
        assertEquals("-i {in} -vf scale=-2:720 -c:v libx264 {out}", AiCommandParser.extract(reply))
    }

    @Test
    fun `collects final command and fallback as selectable options`() {
        val reply = "A. 需求理解与假设\n" +
            "```bash\nffmpeg -i {in} -map 0:v:0 -map 0:a:0 -c:v libx265 -n {out}\n```\n" +
            "C. 输出验证命令\nffprobe -v error -show_entries format=duration {out}\n" +
            "D. 风险与备选方案\n" +
            "```bash\nffmpeg -i {in} -map 0:v:0 -map 0:a:0 -c:v libx264 -n {out}\n```"
        val all = AiCommandParser.extractAll(reply)
        assertEquals(2, all.size)
        assertEquals("-i {in} -map 0:v:0 -map 0:a:0 -c:v libx265 -n {out}", all[0])
        assertEquals("-i {in} -map 0:v:0 -map 0:a:0 -c:v libx264 -n {out}", all[1])
        assertEquals(all.first(), AiCommandParser.extract(reply))
    }

    @Test
    fun `deduplicates identical commands and ignores ffprobe verification`() {
        val reply = "```\nffmpeg -i {in} -c copy -n {out}\n```\n" +
            "ffprobe -v error -show_entries stream=codec_name {out}\n" +
            "```\nffmpeg -i {in} -c copy -n {out}\n```"
        val all = AiCommandParser.extractAll(reply)
        assertEquals(1, all.size)
        assertEquals("-i {in} -c copy -n {out}", all[0])
    }

    @Test
    fun `strips bash line continuations`() {
        val reply = "```bash\nffmpeg -i {in} \\\n-map 0:v:0 -c:v libx265 \\\n-n {out}\n```"
        assertEquals(
            "-i {in} -map 0:v:0 -c:v libx265 -n {out}",
            AiCommandParser.extract(reply)
        )
    }

    @Test
    fun `extracts braced commands with nested in out placeholders`() {
        val reply = "A. 最终执行命令\n" +
            "{ffmpeg -i {in} -map 0:v:0 -map 0:a:0 -c copy -n {out}}\n" +
            "B. 风险与备选方案\n" +
            "{ffmpeg -i {in} -c:v libx264 -n {out}}"
        val all = AiCommandParser.extractAll(reply)
        assertEquals(2, all.size)
        assertEquals("-i {in} -map 0:v:0 -map 0:a:0 -c copy -n {out}", all[0])
        assertEquals("-i {in} -c:v libx264 -n {out}", all[1])
    }

    @Test
    fun `ignores the literal marker and keeps at most three options`() {
        val reply = "最终代码以{ffmpeg命令}标记，最多给出3个方案\n" +
            "{ffmpeg -i {in} -t 10 -c copy -n {out}}\n" +
            "{ffmpeg -i {in} -t 10 -c:v libx264 -n {out}}\n" +
            "{ffmpeg -i {in} -t 10 -c:v libx265 -n {out}}\n" +
            "{ffmpeg -i {in} -t 10 -c:v libvpx-vp9 -n {out}}"
        val all = AiCommandParser.extractAll(reply)
        assertEquals(3, all.size)
        assertTrue(all.all { it.startsWith("-i {in}") && it.endsWith("{out}") })
    }

    @Test
    fun `braced and fenced commands are merged and deduplicated`() {
        val reply = "{ffmpeg -i {in} -t 10 -c copy -n {out}}\n" +
            "```bash\nffmpeg -i {in} -t 10 -c copy -n {out}\n```\n" +
            "```bash\nffmpeg -i {in} -t 10 -c:v libx264 -n {out}\n```"
        val all = AiCommandParser.extractAll(reply)
        assertEquals(2, all.size)
        assertEquals("-i {in} -t 10 -c copy -n {out}", all[0])
        assertEquals("-i {in} -t 10 -c:v libx264 -n {out}", all[1])
    }

    @Test
    fun `braced command spanning several lines is flattened`() {
        val reply = "A.\n{ffmpeg -i {in} \\\n-map 0:v:0 -c:v libx265 \\\n-n {out}}"
        assertEquals(
            "-i {in} -map 0:v:0 -c:v libx265 -n {out}",
            AiCommandParser.extract(reply)
        )
    }
}
