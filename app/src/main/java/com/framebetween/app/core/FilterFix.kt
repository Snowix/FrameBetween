package com.framebetween.app.core

/**
 * 自定义命令执行前的兼容性修正（纯 Kotlin，可 JVM 单测）。
 *
 * 自建 FFmpeg 9.0.2 AAR 已包含原生 drawtext（freetype / fontconfig / fribidi /
 * harfbuzz），因此不再沿用 8.1.x 时代把 drawtext 改写成 libass ASS 字幕的绕路：
 * 用户与 AI 生成的文字命令原样执行。这里只做三件事：
 *  - drawtext 缺 fontfile 时补本机可用的 CJK 字体，保证中文不是方框；
 *  - 滤镜链前补 format=yuv420p，避免 10-bit / yuvj 帧注入失败；
 *  - eq / drawtext 等滤镜不能与 -c copy 共用，并补 -pix_fmt yuv420p。
 */
object FilterFix {

    data class Outcome(
        val tokens: List<String>,
        val notes: List<String>,
        val blocked: Boolean = false,
        val reason: String = ""
    )

    private val FILTER_FLAGS = setOf("-vf", "-filter:v")

    fun apply(tokens: List<String>): Outcome {
        if (tokens.isEmpty()) return Outcome(tokens, emptyList())
        val notes = mutableListOf<String>()
        val font = SubtitleCues.systemFont()

        // 第一遍：drawtext 原样保留，只补字体与 format 前缀。
        // -filter_complex 的值常带 [0:v] 标签，在整串前面拼接会破坏语法，跳过前缀。
        val rewritten = tokens.map { token ->
            if (!token.contains("drawtext=", ignoreCase = true)) return@map token
            var out = token
            if (font != null && !out.contains("fontfile=", ignoreCase = true)) {
                out = out.replace(Regex("drawtext=", RegexOption.IGNORE_CASE)) {
                    "drawtext=fontfile='$font':"
                }
                notes += L10n.t("已为原生 drawtext 指定本机中文字体")
            }
            if (!out.startsWith("format=yuv420p", ignoreCase = true) &&
                !out.trimStart().startsWith("[")
            ) {
                out = "format=yuv420p," + out
                notes += L10n.t("已在滤镜链前加入 format=yuv420p，避免帧格式不兼容")
            }
            out
        }

        val needsReencode = rewritten.any {
            it.contains("eq=", ignoreCase = true) || it.contains("drawtext=", ignoreCase = true)
        }
        if (!needsReencode) return Outcome(rewritten, notes.distinct())

        val out = mutableListOf<String>()
        var i = 0
        while (i < rewritten.size) {
            val token = rewritten[i]
            val next = rewritten.getOrNull(i + 1)
            when {
                // 滤镜链前强制转成 8-bit yuv420p，避免 10-bit / yuvj 帧注入失败
                token in FILTER_FLAGS && next != null &&
                    (next.contains("eq=", ignoreCase = true) ||
                        next.contains("drawtext=", ignoreCase = true)) &&
                    !next.startsWith("format=yuv420p", ignoreCase = true) -> {
                    out += token
                    out += "format=yuv420p," + next
                    notes += L10n.t("已在滤镜链前加入 format=yuv420p，避免帧格式不兼容")
                    i += 2
                }
                // 滤镜必须重编码，不能与 -c copy / -c:v copy 共用
                (token == "-c" || token == "-c:v" || token == "-vcodec") && next == "copy" -> {
                    out += listOf("-c:v", Codecs.DEFAULT_VIDEO, "-preset", "veryfast", "-crf", "23")
                    if (token == "-c") out += listOf("-c:a", "copy")
                    notes += L10n.t("滤镜需要重编码，已把 -c copy 改为 H.265 编码")
                    i += 2
                }
                else -> {
                    out += token
                    i++
                }
            }
        }
        if (!out.contains("-pix_fmt")) {
            val insertAt = (out.size - 1).coerceAtLeast(0)
            out.add(insertAt, "-pix_fmt")
            out.add(insertAt + 1, "yuv420p")
            notes += L10n.t("已补充 -pix_fmt yuv420p")
        }
        return Outcome(out, notes.distinct())
    }
}
