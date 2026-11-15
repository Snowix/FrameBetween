package com.framebetween.app.core

/**
 * 「AI 解析」发送前把命令里的真实路径换成中文占位标签，避免把本机目录结构发给模型。
 *
 * 发给模型的是「数据」而不是界面文案，因此本文件在 scripts/i18n.py 中列入豁免名单：
 * 这些标签必须固定为中文，不能随应用语言切换，也不能被包成 L10n.t()。
 * 纯 Kotlin、无 Android 依赖，便于 JVM 单元测试。
 */
object CommandRedactor {

    const val INPUT_LABEL = "输入路径"
    const val OUTPUT_LABEL = "输出路径"
    const val OTHER_LABEL = "其它路径"

    /** 至少两级的绝对 / content 路径；PTS/2、scale=-2:720 这类参数不会被误判。 */
    private val inlinePath = Regex(
        "(?:/[^\\s,;'\"=/]+){2,}[^\\s,;'\"=]*|" +
            "content://[^\\s,;'\"=]+|file://[^\\s,;'\"=]+"
    )
    private val windowsPath = Regex("^[A-Za-z]:[\\\\/]")

    private fun normalize(path: String): String =
        path.trim().trim('"').trim('\'').trim()

    /** 整个 token 就是一条路径（而非 `subtitles=filename=...` 这样的参数）。 */
    private fun isPurePath(token: String): Boolean =
        token.startsWith("/") ||
            token.startsWith("content://") ||
            token.startsWith("file://") ||
            windowsPath.containsMatchIn(token)

    private fun isPathLike(token: String): Boolean {
        if (token.isEmpty() || token.startsWith("-")) return false
        return isPurePath(token) || inlinePath.containsMatchIn(token)
    }

    /**
     * 把【命令输入】里的路径脱敏：
     * - `-i` 后的输入路径 → 输入路径
     * - 最后一个路径（输出路径） → 输出路径
     * - 其它路径（字幕 / 水印 / 封面等）→ 其它路径
     * - `{in}` / `{out}` 占位符原样保留，方便模型继续使用占位符给出修正命令
     */
    fun redact(command: String, inputPath: String = "", outputPath: String = ""): String {
        val tokens = CommandTokenizer.parse(command)
        if (tokens.isEmpty()) return ""
        val input = normalize(inputPath)
        val output = normalize(outputPath)
        val inputIndex = tokens.indexOfFirst { it.equals("-i", ignoreCase = true) }
            .let { if (it >= 0) it + 1 else -1 }
        // 输出只从「整条就是路径」的 token 里挑，避免把滤镜里的路径误认成输出
        val lastPath = tokens.indexOfLast { isPurePath(it) }
        val outputIndex = if (lastPath == inputIndex) -1 else lastPath

        val result = tokens.mapIndexed { index, token ->
            val plain = normalize(token)
            when {
                plain == "{in}" || plain == "{out}" -> token
                index == inputIndex && isPathLike(token) -> INPUT_LABEL
                index == outputIndex && isPathLike(token) -> OUTPUT_LABEL
                input.isNotBlank() && plain == input -> INPUT_LABEL
                output.isNotBlank() && plain == output -> OUTPUT_LABEL
                // 纯路径整体替换；`subtitles=filename=/sdcard/x.srt` 这类参数只替换路径片段，
                // 保留滤镜名，模型才能看明白失败命令在做什么。
                isPurePath(token) -> OTHER_LABEL
                inlinePath.containsMatchIn(token) -> inlinePath.replace(token, OTHER_LABEL)
                else -> token
            }
        }
        return result.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }

    /** FFmpeg 失败日志里同样会夹带真实路径，发送前统一脱敏。 */
    fun redactText(text: String, inputPath: String = "", outputPath: String = ""): String {
        var result = text
        listOf(inputPath to INPUT_LABEL, outputPath to OUTPUT_LABEL).forEach { (path, label) ->
            val plain = normalize(path)
            if (plain.length >= 4) result = result.replace(plain, label)
        }
        return inlinePath.replace(result, OTHER_LABEL)
    }
}
