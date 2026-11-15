package com.framebetween.app.core

/**
 * 执行失败原因的稳定提取。
 *
 * 旧实现优先取 `session.failStackTrace.take(220)`，回退「当时已到达的最后一行」：
 * 前者是异步日志里累积的堆栈前 220 字符（常常是 banner / 版本号），后者取决于日志
 * 回调到达的时机，所以同一条失败命令刷新几次会得到完全不同的内容。
 *
 * 现在统一改成「等齐日志 → 剔除噪音 → 取末尾三行」，结果与回调时序无关。
 * 纯 Kotlin 实现，方便 JVM 单测覆盖。
 */
object FailureDigest {

    const val DEFAULT_LINES = 3
    private const val MAX_LINE_LENGTH = 240

    fun tail(lines: List<String>, limit: Int = DEFAULT_LINES): String {
        val count = limit.coerceAtLeast(1)
        return lines
            .asSequence()
            .map { it.trimEnd() }
            .filter(::isMeaningful)
            .toList()
            .takeLast(count)
            .joinToString("\n") { line ->
                if (line.length > MAX_LINE_LENGTH) line.take(MAX_LINE_LENGTH) + "…" else line
            }
    }

    /** 命令行回显、进度、banner 与编译配置都不是错误原因，需要剔除。 */
    private fun isMeaningful(line: String): Boolean {
        val t = line.trim()
        if (t.isBlank()) return false
        if (t.startsWith("$ ") || t.startsWith("frame=")) return false
        if (t.startsWith("ffmpeg version")) return false
        if (t.startsWith("configuration:")) return false
        if (t.startsWith("built with")) return false
        if (t.startsWith("libav")) return false
        if (t.startsWith("Copyright (c)")) return false
        if (t.startsWith("Input #") || t.startsWith("Output #")) return false
        if (t.startsWith("Stream mapping:") || t.startsWith("Press [q]")) return false
        return true
    }
}
