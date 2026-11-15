package com.framebetween.app.core

/**
 * 「删除帧」的两种实现方式（纯 Kotlin，便于 JVM 单测）。
 *
 * - [RECODE]：select + setpts 重排视频时间戳，aselect + asetpts 对音频按同一时间区间删除，
 *   音视频同步、帧级精确，但需要重编码。
 * - [STREAM_COPY]：把「保留区间」写成 concat demuxer 的 inpoint/outpoint，用 -c copy
 *   直接做码流级拼接，不解码不重编码、速度极快；代价是切点会吸附到关键帧，精度有限。
 */
enum class FrameDeleteMode(val label: String, val hint: String) {
    RECODE(L10n.t("精确 · 重编码"), L10n.t("音视频同步删除，帧级精确，速度较慢")),
    STREAM_COPY(L10n.t("极速 · 码流级"), L10n.t("不重编码直接拼接保留片段，切点对齐关键帧"))
}

data class FrameTimeRange(val startSec: Double, val endSec: Double)

/** 把帧号区间换算成时间区间，并生成音视频滤镜表达式与 concat 列表。 */
object FrameDelete {

    /** 保留区间：从删除区间的补集得到，用于码流级 concat。 */
    fun keepRanges(ranges: List<FrameRange>, fps: Double, totalFrames: Long): List<FrameRange> {
        if (fps <= 0 || ranges.isEmpty()) return emptyList()
        val deleted = FrameRanges.normalize(ranges).take(FrameRanges.MAX_RANGES)
        val out = mutableListOf<FrameRange>()
        var cursor = 0L
        deleted.forEach { r ->
            if (r.start > cursor) out += FrameRange(cursor, r.start - 1)
            cursor = maxOf(cursor, r.end + 1)
        }
        if (totalFrames > cursor) out += FrameRange(cursor, totalFrames - 1)
        return out
    }

    /** 帧号 → 秒；区间是闭区间，因此尾部再加一帧时长。 */
    fun toTimes(ranges: List<FrameRange>, fps: Double): List<FrameTimeRange> {
        if (fps <= 0) return emptyList()
        return FrameRanges.normalize(ranges).take(FrameRanges.MAX_RANGES).map {
            FrameTimeRange(it.start / fps, (it.end + 1) / fps)
        }
    }

    private fun sec(value: Double): String = "%.4f".format(java.util.Locale.US, value)

    /** 音频 aselect：按 t（秒）丢弃与视频被删帧相同的时间段。 */
    fun audioSelectExpression(ranges: List<FrameRange>, fps: Double): String {
        val times = toTimes(ranges, fps)
        if (times.isEmpty()) return ""
        return "not(" + times.joinToString("+") { "between(t,${sec(it.startSec)},${sec(it.endSec)})" } + ")"
    }

    /**
     * concat demuxer 列表：每个保留区间引用同一个输入文件，用 inpoint / outpoint 限定范围。
     * 路径里的单引号按 concat 语法转义，避免含撇号的目录让解析失败。
     */
    fun concatList(input: String, ranges: List<FrameRange>, fps: Double, totalFrames: Long): String {
        val keep = keepRanges(ranges, fps, totalFrames)
        if (keep.isEmpty()) return ""
        val escaped = input.replace("'", "'\\''")
        return buildString {
            appendLine("ffconcat version 1.0")
            keep.forEach { r ->
                val start = r.start / fps
                val end = (r.end + 1) / fps
                appendLine("file '$escaped'")
                appendLine("inpoint ${sec(start)}")
                appendLine("outpoint ${sec(end)}")
            }
        }
    }
}
