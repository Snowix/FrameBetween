package com.framebetween.app.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** 日志级别，数值与 android.util.Log 保持一致，方便按「不低于某级」筛选。 */
enum class LogLevel(val priority: Int) {
    VERBOSE(2), DEBUG(3), INFO(4), WARN(5), ERROR(6)
}

data class LogEntry(
    val timeMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    fun format(): String {
        val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
            .format(Date(timeMillis))
        return "$stamp ${level.name.first()}/$tag: $message"
    }
}

/**
 * 应用内分级日志：内存环形缓冲 + 本机文件，全部留在设备里。
 * 默认不开启，必须由用户在【设置 → 高级 → 日志】里打开开关；关闭时 log() 直接返回。
 *
 * 不依赖 Android Context（init 接收 filesDir），因此 JVM 单元测试可直接验证
 * 开关、分级筛选、清空与格式化。设置页【日志】的实时查看 / 分享 / 导出 / 清空都读这里。
 */
object AppLog {

    private const val MAX_ENTRIES = 2000
    private const val MAX_FILE_BYTES = 1024L * 1024L
    private const val FILE_NAME = "framebetween.log"

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>()
    @Volatile
    private var logFile: File? = null

    /**
     * 日志总开关，默认关闭。Android 侧由 Settings.init 读取用户偏好后设置；
     * 关闭时不写内存、不写磁盘，崩溃处理器同样不会记录。
     */
    @Volatile
    private var enabled = false

    fun setEnabled(value: Boolean) { enabled = value }

    fun isEnabled(): Boolean = enabled

    /** 由 Application.onCreate 传入 filesDir；重复调用是幂等的。 */
    fun init(filesDir: File) {
        val dir = File(filesDir, "logs").apply { mkdirs() }
        logFile = File(dir, FILE_NAME)
    }

    fun attachForTest(file: File) {
        logFile = file
    }

    fun log(level: LogLevel, tag: String, message: String) {
        if (!enabled) return
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            val target = logFile
            if (target != null) {
                runCatching {
                    if (target.isFile && target.length() > MAX_FILE_BYTES) target.delete()
                    target.parentFile?.mkdirs()
                    target.appendText(entry.format() + "\n", Charsets.UTF_8)
                }
            }
        }
    }

    fun v(tag: String, message: String) = log(LogLevel.VERBOSE, tag, message)
    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    /** 返回不低于 minLevel 的日志快照；「详细」= VERBOSE 即全部。 */
    fun entries(minLevel: LogLevel = LogLevel.VERBOSE): List<LogEntry> = synchronized(lock) {
        buffer.filter { it.level.priority >= minLevel.priority }
    }

    fun size(): Int = synchronized(lock) { buffer.size }

    /** 导出用的纯文本，最新的在最后。空日志给出可读占位。 */
    fun text(minLevel: LogLevel = LogLevel.VERBOSE): String {
        val lines = entries(minLevel).map { it.format() }
        return if (lines.isEmpty()) {
            "(no log entries for ${minLevel.name})"
        } else {
            lines.joinToString("\n") + "\n"
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            runCatching { logFile?.delete() }
        }
    }

    /** 记录未捕获异常后仍交回系统默认处理，不改变崩溃行为。 */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            e("Crash", "${thread.name}: ${error.stackTraceToString().take(2000)}")
            previous?.uncaughtException(thread, error)
        }
    }
}
