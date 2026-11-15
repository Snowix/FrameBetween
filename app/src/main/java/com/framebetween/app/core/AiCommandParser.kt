package com.framebetween.app.core

/**
 * 从 AI 回复里抽取可直接执行的 ffmpeg 参数行。
 *
 * 提示词第 7 条要求「最终代码以{ffmpeg命令}标记，最多给出3个方案」，
 * 因此模型把每条命令放在一对花括号里，例如 `{ffmpeg -i {in} -t 10 -c copy {out}}`。
 * 命令本身还带着 {in} / {out} 占位符，所以 [closingBrace] 会成对跳过这些已知占位符，
 * 找到真正结束命令的那个 `}`。代码块 / 独立命令行仍作为兼容回退，最终结果最多 3 条。
 * 纯 Kotlin、无 Android 依赖，便于 JVM 单元测试覆盖。
 */
object AiCommandParser {

    const val MAX_OPTIONS = 3

    private val shellMeta = setOf(';', '|', '&', '>', '<')

    /** 花括号内可以安全嵌套、不算新层级的占位符 / 标记。 */
    private val nestedTokens = listOf(
        "{in}", "{out}", "{ffmpeg命令}", "{媒体信息}", "{剪辑需求}",
        "{FFmpeg版本}", "{脱敏命令}", "{错误信息}"
    )

    /** 兼容旧调用：只取第一条命令。 */
    fun extract(reply: String): String = extractAll(reply).firstOrNull().orEmpty()

    fun extractAll(reply: String): List<String> {
        // 花括号标记的命令优先；同一条命令也可能被模型重复写进代码块，去重后取前三。
        val braced = extractBraced(reply)
        // 已有花括号命令时不再收独立行：多行 `{ffmpeg ...}` 的续行会被误当成新命令，
        // 但 fenced code block 里的备选命令仍要保留。
        val candidates = braced + extractBlocks(reply, skipPlainLines = braced.isNotEmpty())
        return candidates.distinct().take(MAX_OPTIONS)
    }

    /** 解析 `{ffmpeg ... {in} ... {out}}` 这类带嵌套占位符的命令块。 */
    private fun extractBraced(reply: String): List<String> {
        val found = mutableListOf<String>()
        var i = 0
        while (i < reply.length) {
            if (reply[i] != '{') {
                i++
                continue
            }
            val end = closingBrace(reply, i)
            if (end < 0) {
                i++
                continue
            }
            val candidate = clean(reply.substring(i + 1, end))
            if (looksLikeCommand(candidate)) found += candidate
            i = end + 1
        }
        return found
    }

    /** 返回与 start 处 `{` 配对的 `}` 下标；内部的 {in} / {out} 只当作普通字符。 */
    private fun closingBrace(text: String, start: Int): Int {
        var depth = 0
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '{' -> {
                    val token = nestedTokens.firstOrNull { text.startsWith(it, i) }
                    if (token != null) {
                        i += token.length
                        continue
                    }
                    depth++
                    i++
                }
                '}' -> {
                    if (depth > 0) {
                        depth--
                        i++
                    } else {
                        return i
                    }
                }
                else -> i++
            }
        }
        return -1
    }

    /** 兼容旧版回复：fenced code block 与独立命令行。 */
    private fun extractBlocks(reply: String, skipPlainLines: Boolean = false): List<String> {
        val found = mutableListOf<String>()
        val fenced = mutableListOf<String>()
        var inFence = false
        fun flush() {
            val candidate = clean(fenced.joinToString(" "))
            if (looksLikeCommand(candidate)) found += candidate
            fenced.clear()
        }
        for (raw in reply.lines()) {
            val line = raw.trim()
            if (line.startsWith("```")) {
                if (inFence) {
                    flush()
                    inFence = false
                } else {
                    inFence = true
                    fenced.clear()
                }
                continue
            }
            if (inFence) {
                fenced += line
                continue
            }
            if (skipPlainLines) continue
            val candidate = clean(line)
            if (looksLikeCommand(candidate)) found += candidate
        }
        if (inFence) flush()
        return found
    }

    private fun clean(raw: String): String {
        var s = raw.trim().trim('`').trim()
        // 独立成行的 `{ffmpeg ...}` 也要能被代码块兼容路径识别，结果才能与花括号路径去重
        if (s.startsWith("{") && s.endsWith("}")) {
            s = s.substring(1, s.length - 1).trim()
        }
        if (s.startsWith("${'$'} ")) s = s.drop(2).trim()
        // bash 续行符：多行命令拼接后把 "\" 变成普通空格
        s = s.replace(Regex("\\s*\\\\\\s*"), " ")
        if (s.startsWith("ffmpeg ", ignoreCase = true)) s = s.substring(7).trim()
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun looksLikeCommand(s: String): Boolean {
        if (s.length < 8 || shellMeta.any { it in s }) return false
        // ffprobe 验证命令不是可填入命令框的执行命令
        if (s.startsWith("ffprobe", ignoreCase = true)) return false
        return s.startsWith("-") || s.contains("-i ") || s.contains("-i\t")
    }
}
