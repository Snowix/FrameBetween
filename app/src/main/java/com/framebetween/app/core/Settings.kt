package com.framebetween.app.core

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import com.framebetween.app.App
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class OutputTarget { EXTERNAL, GALLERY, CUSTOM }

/**
 * 轻量持久化设置。所有值同时暴露为 StateFlow，UI 直接收集，修改即时生效。
 * 语言同时同步给 [L10n]（纯 Kotlin，无 Android 依赖，方便 JVM 单测）。
 */
object Settings {

    private const val PREF = "ffstudio_settings"
    private lateinit var prefs: SharedPreferences

    private val _theme = MutableStateFlow(ThemeMode.SYSTEM)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _language = MutableStateFlow("zh")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _background = MutableStateFlow(true)
    val background: StateFlow<Boolean> = _background.asStateFlow()

    private val _outputTarget = MutableStateFlow(OutputTarget.EXTERNAL)
    val outputTarget: StateFlow<OutputTarget> = _outputTarget.asStateFlow()

    private val _customTree = MutableStateFlow<String?>(null)
    val customTree: StateFlow<String?> = _customTree.asStateFlow()

    /** 硬件编解码开关：开启后在支持的命令前注入 -hwaccel mediacodec（默认关闭，稳定优先）。 */
    private val _hardwareCodec = MutableStateFlow(false)
    val hardwareCodec: StateFlow<Boolean> = _hardwareCodec.asStateFlow()

    /** 「求助 AI」提示词开关：默认开启；关闭后用户输入原样发送，不做模板 / ffprobe 注入。 */
    private val _aiPromptEnabled = MutableStateFlow(true)
    val aiPromptEnabled: StateFlow<Boolean> = _aiPromptEnabled.asStateFlow()

    /** 「求助 AI」连续对话模式：切换后弹窗变为常规聊天对话框。 */
    private val _aiChatMode = MutableStateFlow(false)
    val aiChatMode: StateFlow<Boolean> = _aiChatMode.asStateFlow()

    /** 应用内日志开关：默认关闭；开启后 AppLog 才写内存缓冲与本机文件。 */
    private val _logEnabled = MutableStateFlow(false)
    val logEnabled: StateFlow<Boolean> = _logEnabled.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        _theme.value = runCatching {
            ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        _language.value = prefs.getString("language", "zh") ?: "zh"
        L10n.language = _language.value
        _background.value = prefs.getBoolean("background", true)
        _outputTarget.value = runCatching {
            OutputTarget.valueOf(prefs.getString("output", OutputTarget.EXTERNAL.name) ?: OutputTarget.EXTERNAL.name)
        }.getOrDefault(OutputTarget.EXTERNAL)
        _customTree.value = prefs.getString("custom_tree", null)
        _hardwareCodec.value = prefs.getBoolean("hardware_codec", false)
        _aiPromptEnabled.value = prefs.getBoolean("ai_prompt_enabled", true)
        _aiChatMode.value = prefs.getBoolean("ai_chat_mode", false)
        _logEnabled.value = prefs.getBoolean("log_enabled", false)
        // 冷启动与备份恢复都从这里把开关同步给日志核心
        AppLog.setEnabled(_logEnabled.value)
        applyLocale(_language.value)
    }

    /** 同步应用级资源语言，让通知渠道名、系统字符串资源等也跟随切换。 */
    @Suppress("DEPRECATION")
    private fun applyLocale(code: String) {
        val locale = if (code == "en") Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE
        Locale.setDefault(locale)
        runCatching {
            val res = App.instance.resources
            val config = Configuration(res.configuration)
            config.setLocale(locale)
            res.updateConfiguration(config, res.displayMetrics)
        }
    }

    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        if (::prefs.isInitialized) prefs.edit().putString("theme", mode.name).apply()
    }

    fun setLanguage(code: String) {
        _language.value = code
        L10n.language = code
        applyLocale(code)
        if (::prefs.isInitialized) prefs.edit().putString("language", code).apply()
    }

    fun setBackground(enabled: Boolean) {
        _background.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean("background", enabled).apply()
    }

    fun setOutputTarget(target: OutputTarget) {
        _outputTarget.value = target
        if (::prefs.isInitialized) prefs.edit().putString("output", target.name).apply()
    }

    fun setCustomTree(uri: String?) {
        _customTree.value = uri
        if (::prefs.isInitialized) {
            if (uri == null) prefs.edit().remove("custom_tree").apply()
            else prefs.edit().putString("custom_tree", uri).apply()
        }
    }

    fun setHardwareCodec(enabled: Boolean) {
        _hardwareCodec.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean("hardware_codec", enabled).apply()
    }

    fun setAiPromptEnabled(enabled: Boolean) {
        _aiPromptEnabled.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean("ai_prompt_enabled", enabled).apply()
    }

    fun setAiChatMode(enabled: Boolean) {
        _aiChatMode.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean("ai_chat_mode", enabled).apply()
    }

    /** 即时生效，不需要重启应用；关闭后所有日志写入都被跳过。 */
    fun setLogEnabled(enabled: Boolean) {
        _logEnabled.value = enabled
        AppLog.setEnabled(enabled)
        if (::prefs.isInitialized) prefs.edit().putBoolean("log_enabled", enabled).apply()
    }

}
