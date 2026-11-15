package com.framebetween.app.core

import java.io.File
import java.util.Locale

/** 一条字幕：起止毫秒与已去掉样式标签的纯文本。 */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * FFmpeg 9.0.2 原生 drawtext 字幕渲染。
 *
 * 8.1.x 的 AAR 没有编入 drawtext，工程只能把文字命令改写成 libass 的 ASS 字幕；
 * 9.0.2 已带 freetype / fontconfig / fribidi / harfbuzz，drawtext 可原生渲染中文。
 * 这里把 SRT / VTT / ASS / SSA 解析成逐条 drawtext（enable 时间轴 + 本机 CJK 字体），
 * 不再绕 libass。纯 Kotlin，可在 JVM 单测中验证解析、转义与字体选择。
 */
object SubtitleCues {

    /** 命令行参数不能无限长；超长字幕回落到 libass subtitles 滤镜。 */
    const val MAX_CUES = 150

    private val FONT_CANDIDATES = listOf(
        "/system/fonts/NotoSansCJK-Regular.ttc",
        "/system/fonts/NotoSansCJK-Regular.otf",
        "/system/fonts/NotoSansSC-Regular.otf",
        "/system/fonts/DroidSansFallback.ttf",
        "/system/fonts/Roboto-Regular.ttf"
    )

    /** 返回设备上真实存在的中文 capable 字体；找不到时交给 drawtext 的 fontconfig。 */
    fun systemFont(): String? = FONT_CANDIDATES.firstOrNull {
        runCatching { File(it).isFile }.getOrDefault(false)
    }

    fun parse(raw: String, name: String = ""): List<SubtitleCue> {
        val text = raw.replace("\uFEFF", "").replace("\r", "")
        val lower = name.lowercase(Locale.US)
        return when {
            lower.endsWith(".ass") || lower.endsWith(".ssa") ||
                text.contains("[Events]", ignoreCase = true) -> parseAss(text)
            else -> parseSrt(text)
        }
    }

    private val TIME = Regex("(\\d{1,3}):(\\d{1,2}):(\\d{1,2})[,.](\\d{1,3})")
    private val ARROW = Regex("\\s*--?>\\s*")

    private fun timeMs(s: String): Long? {
        val m = TIME.find(s) ?: return null
        val (h, mi, sec, frac) = m.destructured
        val ms = when (frac.length) {
            1 -> frac.toLong() * 100
            2 -> frac.toLong() * 10
            else -> frac.toLong()
        }
        return h.toLong() * 3_600_000 + mi.toLong() * 60_000 +
            sec.toLong() * 1000 + ms
    }

    /** SRT 与 WebVTT 共用：空行分块，时间行含 -->，忽略序号、WEBVTT 头与 NOTE 段。 */
    private fun parseSrt(text: String): List<SubtitleCue> {
        val out = mutableListOf<SubtitleCue>()
        for (block in text.split(Regex("\\n[ \\t]*\\n+"))) {
            val lines = block.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val timeLine = lines.firstOrNull { ARROW.containsMatchIn(it) } ?: continue
            val parts = timeLine.split(ARROW, limit = 2)
            if (parts.size != 2) continue
            val start = timeMs(parts[0]) ?: continue
            val end = timeMs(parts[1]) ?: continue
            val body = lines.drop(lines.indexOf(timeLine) + 1)
                .joinToString(" ")
                .replace(Regex("<[^>]*>"), "")
                .replace("&nbsp;", " ")
                .trim()
            if (body.isBlank() || end <= start) continue
            out += SubtitleCue(start, end, body)
        }
        return out
    }

    /** ASS/SSA：[Events] 段的 Dialogue 行，第 10 个逗号后才是正文（可含逗号）。 */
    private fun parseAss(text: String): List<SubtitleCue> {
        val out = mutableListOf<SubtitleCue>()
        var inEvents = false
        for (line in text.split("\n")) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                inEvents = trimmed.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents || !trimmed.startsWith("Dialogue:", ignoreCase = true)) continue
            val fields = trimmed.substringAfter("Dialogue:").trim().split(",", limit = 10)
            if (fields.size < 10) continue
            val start = assTime(fields[1]) ?: continue
            val end = assTime(fields[2]) ?: continue
            val body = fields[9]
                .replace(Regex("\\{[^}]*}"), "")
                .replace("\\N", " ")
                .replace("\\n", " ")
                .trim()
            if (body.isBlank() || end <= start) continue
            out += SubtitleCue(start, end, body)
        }
        return out
    }

    /** ASS 时间是 0:00:01.23（百分秒），SRT 是毫秒。 */
    private fun assTime(s: String): Long? {
        val m = Regex("(\\d+):(\\d{1,2}):(\\d{1,2})[.,](\\d{1,2})").find(s.trim())
            ?: return null
        val (h, mi, sec, cs) = m.destructured
        val centis = cs.toLong() * if (cs.length == 1) 100L else 10L
        return h.toLong() * 3_600_000 + mi.toLong() * 60_000 +
            sec.toLong() * 1000 + centis
    }

    /**
     * drawtext 的 text 参数转义：单引号内反斜杠与引号需转义，换行合并成一行；
     * 花括号换成圆括号，% 由 expansion=none 关闭表达式展开，不再需要逐字转义。
     */
    fun escapeText(s: String): String = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .replace("\r", "")
        .replace("{", "(")
        .replace("}", ")")
        .trim()

    /** 每条字幕一个 drawtext，用 enable='between(t,s,e)' 控制出现时间。 */
    fun drawtextChain(cues: List<SubtitleCue>, fontFile: String? = systemFont()): String {
        if (cues.isEmpty()) return ""
        val font = fontFile?.takeIf { it.isNotBlank() }?.let { "fontfile='$it':" } ?: ""
        return cues.take(MAX_CUES).joinToString(",") { cue ->
            val s = "%.3f".format(Locale.US, cue.startMs / 1000.0)
            val e = "%.3f".format(Locale.US, cue.endMs / 1000.0)
            "drawtext=${font}text='${escapeText(cue.text)}'" +
                ":fontsize=h/22:fontcolor=white" +
                ":borderw=2:bordercolor=black@0.9" +
                ":x=(w-text_w)/2:y=h-text_h-h*0.06" +
                ":expansion=none:enable='between(t,$s,$e)'"
        }
    }
}
