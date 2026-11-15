package com.framebetween.app.core
import com.framebetween.app.core.L10n

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 内置均为 OpenAI 兼容 /chat/completions 端点，用户可自行改成任意兼容服务。
 * icon 是 UI 层用来取品牌标识的资源键（与 id 同名），避免 core 依赖 Compose。
 */
data class AiProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val icon: String = "custom",
    val models: List<String> = emptyList()
)

data class AiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val label: String
) {
    /** 本地 / 局域网模型（Ollama、LM Studio 等）通常不需要密钥，只要有端点与模型即可。 */
    val isValid: Boolean
        get() = (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) &&
            model.isNotBlank()

    val needsKey: Boolean get() = apiKey.isNotBlank()
}

/**
 * AI 助手配置：只保存在本机 SharedPreferences。
 * 每个供应商独立保存 API 地址 / 密钥 / 模型，切换预设时互不覆盖。
 */
object AiSettings {

    const val CUSTOM = "custom"

    val presets = listOf(
        AiProvider(
            "openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "openai",
            listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "o3-mini")
        ),
        AiProvider(
            "deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "deepseek",
            listOf("deepseek-chat", "deepseek-reasoner")
        ),
        AiProvider(
            "moonshot", "Moonshot", "https://api.moonshot.cn/v1", "moonshot-v1-8k", "moonshot",
            listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
        ),
        AiProvider(
            "kimi", "Kimi", "https://api.moonshot.cn/v1", "kimi-k2-turbo-preview", "kimi",
            listOf("kimi-k2-turbo-preview", "kimi-k2-0711-preview", "moonshot-v1-8k")
        ),
        AiProvider(
            "siliconflow", "SiliconFlow", "https://api.siliconflow.cn/v1",
            "Qwen/Qwen2.5-7B-Instruct", "siliconflow",
            listOf(
                "Qwen/Qwen2.5-7B-Instruct", "Qwen/Qwen3-8B",
                "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B", "THUDM/glm-4-9b-chat"
            )
        ),
        AiProvider(
            "zhipu", "Zhipu", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "zhipu",
            listOf("glm-4-flash", "glm-4-plus", "glm-z1-flash")
        ),
        AiProvider(
            "qwen", "Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "qwen",
            listOf("qwen-plus", "qwen-turbo", "qwen-max", "qwen3-8b")
        ),
        AiProvider(
            "ollama", L10n.t("Ollama（本地 / 局域网）"), "http://192.168.1.100:11434/v1",
            "qwen2.5:7b", "ollama", listOf("qwen2.5:7b", "llama3.1:8b", "deepseek-r1:7b")
        ),
        AiProvider(
            "lmstudio", L10n.t("LM Studio（本地 / 局域网）"), "http://192.168.1.100:1234/v1",
            "local-model", "lmstudio", listOf("local-model")
        ),
        AiProvider(CUSTOM, L10n.t("自定义"), "", "", "custom")
    )

    fun suggestions(id: String): List<String> =
        presets.firstOrNull { it.id == id }?.models.orEmpty()

    private const val PREF = "ffstudio_ai"
    private lateinit var appContext: Context

    private val _provider = MutableStateFlow("openai")
    val provider: StateFlow<String> = _provider.asStateFlow()

    private val _baseUrl = MutableStateFlow(presets[0].baseUrl)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _model = MutableStateFlow(presets[0].defaultModel)
    val model: StateFlow<String> = _model.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private fun sp() = appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun field(name: String, id: String): String = "${name}_$id"

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        if (!::appContext.isInitialized) return
        val editor = sp().edit()
        editor.block()
        editor.apply()
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = sp()
        _provider.value = prefs.getString("provider", "openai") ?: "openai"
        // 兼容只保存一套全局配置的旧数据，按当前选中的供应商名下读取
        val current = _provider.value
        if (prefs.contains("base_url") && !prefs.contains(field("base_url", current))) {
            edit {
                putString(field("base_url", current), prefs.getString("base_url", "").orEmpty())
                putString(field("model", current), prefs.getString("model", "").orEmpty())
                putString(field("api_key", current), prefs.getString("api_key", "").orEmpty())
            }
        }
        loadProvider(current)
    }

    /** 读取某个供应商自己的配置；没有存过时回落到预设默认值，密钥永远为空。 */
    private fun loadProvider(id: String) {
        if (!::appContext.isInitialized) return
        val prefs = sp()
        val preset = presets.firstOrNull { it.id == id }
        _baseUrl.value = prefs.getString(field("base_url", id), null) ?: preset?.baseUrl.orEmpty()
        _model.value = prefs.getString(field("model", id), null) ?: preset?.defaultModel.orEmpty()
        _apiKey.value = prefs.getString(field("api_key", id), null).orEmpty()
    }

    /** 只切换当前供应商，不写入其它供应商的任何字段。 */
    fun selectProvider(id: String) {
        _provider.value = id
        edit { putString("provider", id) }
        loadProvider(id)
    }

    fun setBaseUrl(value: String) {
        _baseUrl.value = value
        edit { putString(field("base_url", _provider.value), value) }
    }

    fun setModel(value: String) {
        _model.value = value
        edit { putString(field("model", _provider.value), value) }
    }

    fun setApiKey(value: String) {
        _apiKey.value = value
        edit { putString(field("api_key", _provider.value), value) }
    }

    fun effective(): AiConfig = AiConfig(
        baseUrl = _baseUrl.value.trim(),
        apiKey = _apiKey.value.trim(),
        model = _model.value.trim(),
        label = presets.firstOrNull { it.id == _provider.value }?.label ?: "Custom"
    )
}
