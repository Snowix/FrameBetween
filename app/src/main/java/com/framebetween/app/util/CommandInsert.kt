package com.framebetween.app.util

/**
 * 在命令输入框的「光标 / 选区」处插入一段文本，而不是追加到末尾。
 * 纯 Kotlin，不依赖 Android，便于 JVM 单测覆盖。
 */
data class InsertResult(val text: String, val caret: Int)

object CommandInsert {

    fun atCursor(
        original: String,
        selectionStart: Int,
        selectionEnd: Int,
        insert: String
    ): InsertResult {
        if (insert.isEmpty()) return InsertResult(original, selectionStart.coerceIn(0, original.length))
        val start = selectionStart.coerceIn(0, original.length)
        val end = selectionEnd.coerceIn(start, original.length)
        val before = original.substring(0, start)
        val after = original.substring(end)
        // 仅在必要时补空格，避免把光标两侧已有的分隔重复叠加
        val leading = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val trailing = if (after.isNotEmpty() && !after.first().isWhitespace()) " " else ""
        val piece = leading + insert + trailing
        val text = before + piece + after
        // 光标落在插入内容末尾；尾随空格留在光标之后
        val caret = (before.length + leading.length + insert.length).coerceIn(0, text.length)
        return InsertResult(text, caret)
    }
}
