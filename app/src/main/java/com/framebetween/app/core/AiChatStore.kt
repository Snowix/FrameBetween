package com.framebetween.app.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 「求助 AI」连续对话的消息存储。
 *
 * content 是发给模型的真实文本（提示词开启时首轮为模板全文），display 是气泡里
 * 给用户看的原文。对话存在 SharedPreferences 而非 cacheDir，因此切换开关、关闭
 * 弹窗、切换页面甚至重启应用都保留；只有【清屏】或【清除软件缓存】才会清空。
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val display: String = content
)

object AiChatStore {

    private const val PREF = "ffstudio_ai_chat"
    private const val KEY = "messages"
    private const val MAX = 120
    private lateinit var prefs: SharedPreferences

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        _messages.value = runCatching { decode(prefs.getString(KEY, null)) }.getOrDefault(emptyList())
    }

    fun append(vararg items: ChatMessage) = update { it + items }

    fun clear() = update { emptyList() }

    /** 发给 /chat/completions 的上下文，不含尚未发送的本轮。 */
    fun history(): List<AiClient.ChatTurn> =
        _messages.value.map { AiClient.ChatTurn(it.role, it.content) }

    private fun update(block: (List<ChatMessage>) -> List<ChatMessage>) {
        val next = block(_messages.value).takeLast(MAX)
        _messages.value = next
        if (::prefs.isInitialized) {
            prefs.edit().putString(KEY, encode(next)).apply()
        }
    }

    private fun encode(list: List<ChatMessage>): String = JSONArray().apply {
        list.forEach {
            put(
                JSONObject()
                    .put("role", it.role)
                    .put("content", it.content)
                    .put("display", it.display)
            )
        }
    }.toString()

    private fun decode(raw: String?): List<ChatMessage> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val role = item.optString("role", "")
            if (role != "user" && role != "assistant") return@mapNotNull null
            val content = item.optString("content", "")
            ChatMessage(role, content, item.optString("display", content))
        }
    }
}
