package com.framebetween.app.core

/**
 * 单个任务的进度跟踪（纯 Kotlin，无 Android 依赖，便于 JVM 单测）。
 *
 * 旧实现只在 `durationMs > 0` 时用 statistics.time / duration 计算，很多命令
 * （-c copy、纯音频、GIF、未探测到时长）durationMs 为 0，或 FFmpeg 根本不发
 * statistics 回调，进度就会一直停在 0%，直到完成时突然跳成 100%。
 *
 * 现在有三条互相兜底的信号，且进度单调不回退、运行时上限 0.99：
 * 1. 日志里的 `time=00:01:23.45` / `out_time_ms=` —— 流复制命令也会输出；
 * 2. statistics 回调的 time（毫秒）—— 以首次上报值作为基准，兼容 -ss 后置时
 *    FFmpeg 仍按输入时间轴上报的情况；
 * 3. 完全没有信号时，按已耗时做缓慢的软估算，至少让进度条持续推进，
 *    并可通过 [estimated] 让 UI 明确标注这是估算而不是假装精确。
 */
class ProgressTracker(expectedMs: Long = 0L) {

    private var expected = expectedMs.coerceAtLeast(0L)
    private var origin: Long? = null
    private var hasSignal = false
    private var softElapsedMs = 0L

    /** 0f..1f；运行期间不会达到 1f，完成由 [finish] 显式置位。 */
    var fraction: Float = 0f
        private set

    /** 当前进度是否来自耗时软估算（UI 可据此显示「估算」）。 */
    var estimated: Boolean = false
        private set

    /** 从 FFmpeg 的 banner 里读到 `Duration: 00:01:23.45` 时补齐分母。 */
    fun onDurationHint(durationMs: Long) {
        if (durationMs > 0) expected = durationMs
    }

    /**
     * 上报一次「已处理到的媒体时间」。返回更新后的进度。
     * 首次上报值作为基准，避免 -ss 放在 -i 之后时 time 从源起点开始导致进度虚高。
     */
    fun onTime(timeMs: Long): Float {
        if (timeMs < 0) return fraction
        val base = origin ?: timeMs.also { origin = it }
        hasSignal = true
        estimated = false
        val done = (timeMs - base).coerceAtLeast(0L)
        val value = if (expected > 0) {
            (done.toDouble() / expected.toDouble()).toFloat()
        } else {
            // 有真实时间但缺分母：用一个温和的对数曲线，避免瞬间冲到顶端
            (kotlin.math.ln(1.0 + done / 1000.0) / 8.0).toFloat()
        }
        return bump(value.coerceIn(0f, 0.99f))
    }

    /** 无日志 / 无统计回调时按耗时推进；每 500ms 调用一次即可。 */
    fun onTick(elapsedMs: Long): Float {
        if (hasSignal || elapsedMs <= softElapsedMs) return fraction
        softElapsedMs = elapsedMs
        // 25 秒时间常数，上限 0.90：长时间任务也不会假装快完成
        val soft = (1.0 - kotlin.math.exp(-elapsedMs / 25_000.0)).toFloat() * 0.90f
        estimated = true
        return bump(soft.coerceAtMost(0.90f))
    }

    fun finish(): Float {
        fraction = 1f
        estimated = false
        return fraction
    }

    private fun bump(next: Float): Float {
        // 单调不回退：FFmpeg 多线程/多输入时统计会短暂倒退，界面不该跟着抖
        if (next > fraction) fraction = next
        return fraction
    }
}

/** 从 FFmpeg 输出行里解析进度时间，纯字符串处理，便于单测。 */
object ProgressLog {
    private val CLOCK = Regex("time=\\s*(\\d+):(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?")
    private val OUT_MS = Regex("out_time_ms=\\s*(\\d+)")
    private val DURATION = Regex("Duration:\\s*(\\d+):(\\d{2}):(\\d{2})(?:\\.(\\d{1,2}))?")

    private fun hms(h: String, m: String, s: String, frac: String?): Long {
        val hh = h.toLongOrNull() ?: return -1L
        val mm = m.toLongOrNull() ?: return -1L
        val ss = s.toLongOrNull() ?: return -1L
        val ms = frac?.padEnd(3, '0')?.toLongOrNull() ?: 0L
        return ((hh * 3600 + mm * 60 + ss) * 1000L) + ms
    }

    fun timeMs(line: String): Long? {
        OUT_MS.find(line)?.let { return it.groupValues[1].toLongOrNull()?.div(1000) }
        val m = CLOCK.find(line) ?: return null
        return hms(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
            .takeIf { it >= 0 }
    }

    fun durationMs(line: String): Long? {
        val m = DURATION.find(line) ?: return null
        return hms(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
            .takeIf { it >= 0 }
    }
}
