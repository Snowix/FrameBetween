package com.framebetween.app.core

import java.util.Locale

/**
 * ASS 字幕样式（纯 Kotlin，便于 JVM 单测）。
 *
 * 自建 FFmpeg 9.0.2 AAR 同时含原生 drawtext 与 libass。文字叠加已改用原生 drawtext，
 * 这里保留 ASS 样式生成，供 libass subtitles 滤镜兜底（超长 / 无法解析的字幕文件）。
 */
data class AssStyle(
    val text: String = "FrameBetween",
    val fontSizePx: Int = 48,
    val colorHex: String = "#FFFFFF",
    val outlineHex: String = "#000000",
    /** 九宫格：tl/tc/tr/ml/mc/mr/bl/bc/br */
    val position: String = "bc",
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val playResX: Int = 1920,
    val playResY: Int = 1080
) {
    /** ASS alignment 采用小键盘布局：1 左下 … 9 右上。 */
    private val alignment: Int
        get() = when (position.lowercase(Locale.US)) {
            "tl" -> 7; "tc" -> 8; "tr" -> 9
            "ml" -> 4; "mc" -> 5; "mr" -> 6
            "bl" -> 1; "bc" -> 2; "br" -> 3
            else -> 2
        }

    /** #RRGGBB → ASS 的 &HAABBGGRR（A 为透明度，00 表示不透明）。 */
    private fun assColor(hex: String, alpha: String = "00"): String {
        val c = hex.removePrefix("#").removePrefix("0x")
        if (c.length != 6 || c.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
            return "&H${alpha}FFFFFF"
        }
        val r = c.substring(0, 2)
        val g = c.substring(2, 4)
        val b = c.substring(4, 6)
        return "&H$alpha$b$g$r".uppercase(Locale.US)
    }

    private fun timestamp(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        val h = t / 3_600_000
        val m = (t % 3_600_000) / 60_000
        val s = (t % 60_000) / 1_000
        val cs = (t % 1_000) / 10
        return "%d:%02d:%02d.%02d".format(Locale.US, h, m, s, cs)
    }

    /** ASS 没有花括号转义，替换掉会破坏覆盖标签的字符；换行用 \N。 */
    private fun escape(text: String): String = text
        .replace("{", "(")
        .replace("}", ")")
        .replace("\r", "")
        .replace("\n", "\\N")

    fun render(): String {
        val start = timestamp(startMs)
        val end = if (endMs > startMs) timestamp(endMs) else timestamp(startMs + 3_600_000L)
        val size = fontSizePx.coerceIn(8, 400)
        return buildString {
            appendLine("[Script Info]")
            appendLine("ScriptType: v4.00+")
            appendLine("PlayResX: $playResX")
            appendLine("PlayResY: $playResY")
            appendLine("WrapStyle: 2")
            appendLine("ScaledBorderAndShadow: yes")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            appendLine(
                "Style: Default,Noto Sans CJK SC,$size,${assColor(colorHex)},&H000000FF," +
                    "${assColor(outlineHex)},&H80000000,-1,0,0,0,100,100,0,0,1,2,1," +
                    "$alignment,24,24,32,1"
            )
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            appendLine("Dialogue: 0,$start,$end,Default,,0,0,0,,${escape(text)}")
        }
    }

    companion object {
        private val OPT = Regex("([A-Za-z_]+)=('(?:[^']*)'|\"(?:[^\"]*)\"|[^:]*)")

        /** 把常见 drawtext=... 参数转换成等价的 ASS 样式。 */
        fun fromDrawtext(expr: String): AssStyle {
            val opts = OPT.findAll(expr).associate { m ->
                m.groupValues[1].lowercase(Locale.US) to
                    m.groupValues[2].trim().removeSurrounding("'").removeSurrounding("\"")
            }
            val col = when {
                opts["x"] == null -> "c"
                opts["x"]!!.contains("/2", true) -> "c"
                opts["x"]!!.contains("W-w", true) || opts["x"]!!.contains("w-text_w", true) -> "r"
                else -> "l"
            }
            val row = when {
                opts["y"] == null -> "m"
                opts["y"]!!.contains("/2", true) -> "m"
                opts["y"]!!.contains("H-h", true) || opts["y"]!!.contains("h-text_h", true) -> "b"
                else -> "t"
            }
            val rawColor = opts["fontcolor"].orEmpty()
            val color = when (rawColor.lowercase(Locale.US)) {
                "black" -> "#000000"
                "red" -> "#FF0000"
                "blue" -> "#0000FF"
                "green" -> "#00FF00"
                "yellow" -> "#FFFF00"
                else -> if (rawColor.isBlank()) "#FFFFFF" else rawColor
            }
            return AssStyle(
                text = opts["text"]?.takeUnless { it.isBlank() } ?: "FrameBetween",
                fontSizePx = opts["fontsize"]?.toIntOrNull()?.coerceIn(8, 400) ?: 48,
                colorHex = color,
                outlineHex = opts["bordercolor"]?.takeUnless { it.isBlank() } ?: "#000000",
                position = row + col
            )
        }
    }
}
