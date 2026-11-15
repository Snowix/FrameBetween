package com.framebetween.app.core
import com.framebetween.app.core.L10n

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.framebetween.app.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 统一的 FFmpeg 执行入口：所有功能页只提交参数数组，由这里负责会话、
 * 日志、进度、取消与前台保活，避免各自直接拼命令。
 */
object FFmpegEngine {

    private const val MAX_LOG_LINES = 400
    private val nextId = AtomicLong(1)
    private val lock = Any()

    private val sessionIds = ConcurrentHashMap<Long, Long>()
    private val cancelledIds: MutableSet<Long> =
        Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())
    private val pendingCancel: MutableSet<Long> =
        Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    private val _logs = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    val logs: StateFlow<Map<Long, List<String>>> = _logs.asStateFlow()

    /** 任务完成后的转存 / 清理放在单线程执行，避免阻塞 FFmpeg 回调线程。 */
    private val publisher = Executors.newSingleThreadExecutor()

    /** 每个运行中的任务一份进度跟踪器；完成后移除。 */
    private val trackers = ConcurrentHashMap<Long, ProgressTracker>()

    /**
     * 完全收不到日志 / 统计回调时（部分 -c copy、纯音频、GIF 命令），按耗时缓慢推进，
     * 避免进度一直 0% 直到完成瞬间跳成 100%。守护线程，不阻塞进程退出。
     */
    private val ticker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "framebetween-progress").apply { isDaemon = true }
    }

    init {
        ticker.scheduleWithFixedDelay({ tickAll() }, 500L, 500L, TimeUnit.MILLISECONDS)
    }

    private fun tickAll() {
        if (!hasRunningTasks()) return
        val now = System.currentTimeMillis()
        _tasks.value.filter { it.status == TaskStatus.RUNNING }.forEach { task ->
            val tracker = trackers[task.id] ?: return@forEach
            val value = tracker.onTick(now - task.startedAt)
            if (value != task.progress || tracker.estimated != task.estimated) {
                update(task.id) { it.copy(progress = value, estimated = tracker.estimated) }
            }
        }
    }

    fun hasRunningTasks(): Boolean = _tasks.value.any { it.status == TaskStatus.RUNNING }

    /** 仅运行中任务的产物：清缓存时必须保留，避免删掉 FFmpeg 正在写的文件。 */
    fun runningRefs(): Set<String> = _tasks.value
        .filter { it.status == TaskStatus.RUNNING }
        .map { it.output }
        .filter { it.isNotEmpty() }
        .toSet()

    fun submit(title: String, args: List<String>, output: String = "", durationMs: Long = 0): Long {
        val id = nextId.getAndIncrement()
        AppLog.i("Task", L10n.t("提交任务 #%1\$s：%2\$s", id, title))
        // 硬件编解码开关只作用于真正需要解码的命令；注入后的命令会原样记录到任务里，
        // 这样「AI 解析」拿到的命令与实际执行内容完全一致。
        val decision = HardwareCodec.decide(
            args,
            Settings.hardwareCodec.value,
            CodecCapabilities.mediaCodecSupported()
        )
        val effective = decision.args
        val command = CommandTokenizer.join(effective)
        val task = TaskItem(
            id = id,
            title = title,
            command = command,
            output = output,
            durationMs = durationMs
        )
        val header = mutableListOf<String>()
        // 仅在开关开启时写明硬解状态，让用户能直观看到开关是否生效；
        // 放在日志最前面，不会被失败摘要（末尾三行）误取。
        if (Settings.hardwareCodec.value) {
            header += "# " + L10n.t("硬件解码：%1\$s", decision.reason)
        }
        header += "${'$'} " + command
        synchronized(lock) {
            _tasks.value = listOf(task) + _tasks.value
            _logs.value = _logs.value + (id to header)
        }
        if (Settings.background.value) ProcessingService.start(App.instance, title)

        trackers[id] = ProgressTracker(durationMs)
        val session = FFmpegKit.executeAsync(
            command,
            { completed -> onComplete(id, completed) },
            { log ->
                val line = log.message.orEmpty()
                AppLog.v("FFmpeg", line)
                trackers[id]?.let { tracker ->
                    // FFmpeg 的 banner 自带 Duration，用它补齐分母；流复制 / 纯音频命令
                    // 也会输出 time= 或 out_time_ms=，这是 statistics 之外最可靠的进度源。
                    ProgressLog.durationMs(line)?.let { tracker.onDurationHint(it) }
                    ProgressLog.timeMs(line)?.let { t -> onProgress(id, tracker.onTime(t)) }
                }
                appendLog(id, line)
            },
            { stats ->
                val speedText = if (stats.speed > 0) "%.2fx".format(stats.speed) else ""
                onStatistics(id, stats.time.toLong(), speedText)
            }
        )
        // ffmpeg-kit-next 里 sessionId / returnCode 是 @JvmField protected 字段，
        // Kotlin 属性语法不可访问；用 Session 接口的 public getter。
        sessionIds[id] = session.getSessionId()
        if (pendingCancel.remove(id)) FFmpegKit.cancel(session.getSessionId())
        return id
    }

    fun cancel(taskId: Long) {
        cancelledIds.add(taskId)
        val sessionId = sessionIds[taskId]
        if (sessionId == null) pendingCancel.add(taskId) else FFmpegKit.cancel(sessionId)
    }

    fun clearFinished() {
        val finished = synchronized(lock) {
            val done = _tasks.value.filter { it.status != TaskStatus.RUNNING }
            _tasks.value = _tasks.value - done.toSet()
            _logs.value = _logs.value - done.map { it.id }.toSet()
            done
        }
        finished.forEach { trackers.remove(it.id) }
        // 删除日志的同时回收对应的输出文件，避免长期占用用户空间
        finished.forEach { MediaRepository.deleteOutput(it.output) }
        stopServiceIfIdle()
    }

    fun remove(taskId: Long) {
        val output = _tasks.value.firstOrNull { it.id == taskId }?.output.orEmpty()
        cancel(taskId)
        synchronized(lock) {
            _tasks.value = _tasks.value.filterNot { it.id == taskId }
            _logs.value = _logs.value - taskId
        }
        trackers.remove(taskId)
        MediaRepository.deleteOutput(output)
        stopServiceIfIdle()
    }

    fun linesFor(taskId: Long): List<String> = _logs.value[taskId].orEmpty()

    /** 所有进度写入都走这里：取最大值保证单调，运行时不会提前达到 100%。 */
    private fun onProgress(taskId: Long, fraction: Float) {
        update(taskId) { task ->
            task.copy(progress = maxOf(task.progress, fraction), estimated = false)
        }
    }

    private fun onStatistics(taskId: Long, timeMs: Long, speed: String) {
        val tracker = trackers[taskId] ?: return
        val fraction = tracker.onTime(timeMs)
        update(taskId) { task ->
            task.copy(
                progress = maxOf(task.progress, fraction),
                speed = speed,
                estimated = false
            )
        }
    }

    private fun onComplete(taskId: Long, session: FFmpegSession) {
        val cancelled = cancelledIds.remove(taskId) || pendingCancel.remove(taskId)
        sessionIds.remove(taskId)
        trackers.remove(taskId)?.finish()
        val returnCode = session.getReturnCode()
        val success = !cancelled && returnCode != null && ReturnCode.isSuccess(returnCode)
        val status = when {
            cancelled -> TaskStatus.CANCELLED
            success -> TaskStatus.SUCCESS
            else -> TaskStatus.FAILED
        }
        val message = when {
            cancelled -> L10n.t("已取消")
            success -> L10n.t("处理完成")
            else -> failureMessage(session, taskId, returnCode)
        }
        if (status == TaskStatus.FAILED) {
            AppLog.e("Task", L10n.t("任务 #%1\$s 失败：%2\$s", taskId, message))
        } else {
            AppLog.i("Task", L10n.t("任务 #%1\$s 结束：%2\$s", taskId, message))
        }
        val staging = _tasks.value.firstOrNull { it.id == taskId }?.output.orEmpty()
        update(taskId) {
            it.copy(
                status = status,
                progress = if (success) 1f else it.progress,
                estimated = false,
                endedAt = System.currentTimeMillis(),
                message = if (success && staging.isNotEmpty()) L10n.t("处理完成，正在导出…") else message
            )
        }
        if (success && staging.isNotEmpty()) {
            publisher.execute {
                val published = runCatching { MediaRepository.publish(staging) }.getOrDefault(staging)
                update(taskId) { it.copy(output = published, message = L10n.t("处理完成")) }
                stopServiceIfIdle()
            }
        } else {
            if (!success) MediaRepository.deleteOutput(staging)
            stopServiceIfIdle()
        }
    }

    private fun stopServiceIfIdle() {
        if (Settings.background.value && !hasRunningTasks()) ProcessingService.stop(App.instance)
    }

    private fun appendLog(taskId: Long, line: String) {
        if (line.isBlank() || line.startsWith("frame=")) return
        synchronized(lock) {
            val old = _logs.value[taskId].orEmpty()
            val next = (old + line).takeLast(MAX_LOG_LINES)
            _logs.value = _logs.value + (taskId to next)
        }
    }

    /**
     * 失败原因固定为「末尾三行」。
     *
     * 旧实现依赖异步日志回调当时已到达的最后一行，加上 failStackTrace 前 220 字符
     * （常常是 banner / 版本号），所以同一条失败命令刷新几次显示的内容都不一样。
     * 这里先用 getAllLogs(timeout) 等日志传输收齐，再交给 [FailureDigest] 过滤
     * 命令行回显、进度与 banner 后统一取末尾三行，与回调时序彻底解耦。
     */
    private fun failureMessage(
        session: FFmpegSession,
        taskId: Long,
        returnCode: ReturnCode?
    ): String {
        val logs = runCatching {
            session.getAllLogs(1000).map { it.message.orEmpty() }
        }.getOrDefault(emptyList())
        val source = logs.ifEmpty { linesFor(taskId) }
        return FailureDigest.tail(source)
            .ifBlank { L10n.t("执行失败（退出码 %1\$s）", returnCode?.value ?: -1) }
    }

    private fun update(taskId: Long, transform: (TaskItem) -> TaskItem) {
        synchronized(lock) {
            _tasks.value = _tasks.value.map { if (it.id == taskId) transform(it) else it }
        }
    }
}
