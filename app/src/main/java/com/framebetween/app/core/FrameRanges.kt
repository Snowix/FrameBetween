package com.framebetween.app.core

/** 闭区间帧号（从 0 开始，含首尾）。 */
data class FrameRange(val start: Long, val end: Long) {
    val count: Long get() = end - start + 1
}

/**
 * 「删除帧」功能的区间解析与 select 表达式生成。
 *
 * 帧号区间用 `not(between(n,a,b)+...)` 交给 select 滤镜丢弃，再用
 * `setpts=N/FRAME_RATE/TB` 重排时间戳，避免删帧后留下静止画面或时间戳空洞。
 * 纯 Kotlin，不含 Android 依赖。
 */
object FrameRanges {

    /** 表达式过长会让 FFmpeg 解析变慢，限制最多 12 段。 */
    const val MAX_RANGES = 12
    private val SEPARATORS = Regex("[,，;；\\n\\r]+")
    private val RANGE_SEPARATOR = Regex("[-~～:：—]+")
    private val DIGITS = Regex("\\d+")

    /** 解析「10-20, 50-60」这类文本；非法段直接忽略，不抛异常。 */
    fun parse(text: String): List<FrameRange> {
        if (text.isBlank()) return emptyList()
        val out = mutableListOf<FrameRange>()
        SEPARATORS.split(text).forEach { chunk ->
            val piece = chunk.trim()
            if (piece.isEmpty()) return@forEach
            val parts = RANGE_SEPARATOR.split(piece).map { it.trim() }
            if (parts.size != 2) return@forEach
            val from = parts[0].takeIf { it.matches(DIGITS) }?.toLongOrNull() ?: return@forEach
            val to = parts[1].takeIf { it.matches(DIGITS) }?.toLongOrNull() ?: return@forEach
            if (from < 0 || to < from) return@forEach
            out += FrameRange(from, to)
        }
        return normalize(out).take(MAX_RANGES)
    }

    /** 按起点排序并合并重叠 / 相邻区间，避免生成冗余或互相矛盾的表达式。 */
    fun normalize(ranges: List<FrameRange>): List<FrameRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.start }
        val out = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            val last = out.last()
            if (next.start <= last.end + 1) {
                out[out.lastIndex] = FrameRange(last.start, maxOf(last.end, next.end))
            } else {
                out += next
            }
        }
        return out
    }

    /** 生成 select 的布尔表达式；外层单引号由命令层补齐。 */
    fun selectExpression(ranges: List<FrameRange>): String {
        val safe = normalize(ranges).take(MAX_RANGES)
        if (safe.isEmpty()) return ""
        return "not(" + safe.joinToString("+") { "between(n,${it.start},${it.end})" } + ")"
    }

    fun removedFrames(ranges: List<FrameRange>): Long = normalize(ranges).sumOf { it.count }

    fun label(ranges: List<FrameRange>): String =
        normalize(ranges).joinToString(" · ") { "${it.start}–${it.end}" }
}
