package com.framebetween.app.core

/**
 * FFmpegKit 接收的是参数字符串而不是 shell，因此需要自己做一次分词。
 * 只支持引号与反斜杠转义，不支持管道、重定向等 shell 语法。
 */
object CommandTokenizer {

    fun parse(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote = 0.toChar()
        var escaped = false
        var hasToken = false

        for (ch in input) {
            when {
                escaped -> {
                    current.append(ch)
                    escaped = false
                    hasToken = true
                }
                ch == '\\' -> {
                    escaped = true
                    hasToken = true
                }
                quote != 0.toChar() -> {
                    if (ch == quote) quote = 0.toChar() else {
                        current.append(ch)
                        hasToken = true
                    }
                }
                ch == '\'' || ch == '"' -> {
                    quote = ch
                    hasToken = true
                }
                ch.isWhitespace() -> {
                    if (hasToken) {
                        result.add(current.toString())
                        current.clear()
                        hasToken = false
                    }
                }
                else -> {
                    current.append(ch)
                    hasToken = true
                }
            }
        }
        if (escaped) current.append('\\')
        if (hasToken) result.add(current.toString())
        return result
    }

    /** 重新拼成 FFmpegKit 可解析的命令字符串。 */
    fun join(args: List<String>): String = args.joinToString(" ") { quote(it) }

    fun quote(value: String): String {
        if (value.isEmpty()) return "''"
        val plain = value.all { c ->
            c.isLetterOrDigit() || c in "/._-+:=,%*@"
        }
        if (plain) return value
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    val SHELL_META = charArrayOf(';', '|', '&', '`', '>', '<', '\n', '\r')

    fun containsShellMeta(input: String): Boolean = SHELL_META.any { input.contains(it) }

    /** 允许用户粘贴完整的 `ffmpeg -i ...` 命令行。 */
    fun stripBinaryName(tokens: List<String>): List<String> {
        val first = tokens.firstOrNull()?.lowercase()
        return if (first == "ffmpeg" || first == "ffmpeg.exe") tokens.drop(1) else tokens
    }
}
