package com.framebetween.app.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 设置备份 / 恢复：把 4 个 SharedPreferences 序列化成可读的 XML，或从 XML 还原。
 *
 * XML 的编解码是纯 Kotlin 实现，不依赖 android.util.Xml，encode / decode 可直接在
 * JVM 单元测试里跑；export / import 才接触 Android 的 Context / SharedPreferences。
 * 值统一用 XML 转义放在 <value> / <item> 里，避免 CDATA 结束符 "]]>" 的边界问题。
 */
object SettingsBackup {

    /** 只备份 / 恢复这 4 个我们自己创建的文件，绝不触碰其它 SharedPreferences。 */
    private val PREF_NAMES = listOf(
        "ffstudio_settings",
        "ffstudio_ai",
        "ffstudio_ai_chat",
        "ffstudio_library"
    )

    private const val VERSION = "1"
    private const val PACKAGE = "com.framebetween.app"

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun unescape(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    private fun typeOf(v: Any?): String = when (v) {
        is Boolean -> "boolean"
        is Int -> "int"
        is Long -> "long"
        is Float -> "float"
        is Set<*> -> "string-set"
        else -> "string"
    }

    private fun scalar(v: Any?): String = when (v) {
        null -> ""
        is Set<*> -> ""
        else -> v.toString()
    }

    /** 纯函数：把若干 prefs 分组编码成 XML 文本。 */
    fun encode(groups: Map<String, Map<String, Any?>>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<framebetween-settings version=\"").append(VERSION)
            .append("\" package=\"").append(PACKAGE).append("\">\n")
        groups.forEach { (name, entries) ->
            sb.append("  <prefs name=\"").append(escape(name)).append("\">\n")
            entries.forEach { (key, value) ->
                val type = typeOf(value)
                sb.append("    <entry key=\"").append(escape(key))
                    .append("\" type=\"").append(type).append("\">")
                if (type == "string-set") {
                    (value as? Set<*>)?.forEach { item ->
                        sb.append("<item>").append(escape(item?.toString() ?: "")).append("</item>")
                    }
                } else {
                    sb.append("<value>").append(escape(scalar(value))).append("</value>")
                }
                sb.append("</entry>\n")
            }
            sb.append("  </prefs>\n")
        }
        sb.append("</framebetween-settings>\n")
        return sb.toString()
    }

    private val prefsRegex = Regex("<prefs\\s+name=\"([^\"]*)\"\\s*>([\\s\\S]*?)</prefs>")
    private val entryRegex = Regex("<entry\\s+key=\"([^\"]*)\"\\s+type=\"([^\"]*)\"\\s*>([\\s\\S]*?)</entry>")
    private val valueRegex = Regex("<value>([\\s\\S]*?)</value>")
    private val itemRegex = Regex("<item>([\\s\\S]*?)</item>")

    private fun valueText(body: String): String =
        valueRegex.find(body)?.groupValues?.getOrNull(1)?.let { unescape(it) } ?: ""

    /** 纯函数：把 XML 文本还原成 prefs 分组；未知类型回落成 String。 */
    fun decode(xml: String): Map<String, Map<String, Any?>> {
        val root = Regex("<framebetween-settings\\b([^>]*)>").find(xml)
            ?: throw IllegalArgumentException(L10n.t("根节点不是 framebetween-settings"))
        val version = Regex("version=\"([^\"]*)\"")
            .find(root.groupValues[1])?.groupValues?.getOrNull(1)
        if (version != VERSION) {
            throw IllegalArgumentException(L10n.t("不支持的备份版本：%1\$s", version ?: ""))
        }
        val result = LinkedHashMap<String, Map<String, Any?>>()
        for (prefs in prefsRegex.findAll(xml)) {
            val name = unescape(prefs.groupValues[1])
            val entries = LinkedHashMap<String, Any?>()
            for (entry in entryRegex.findAll(prefs.groupValues[2])) {
                val key = unescape(entry.groupValues[1])
                val type = entry.groupValues[2]
                val body = entry.groupValues[3]
                entries[key] = when (type) {
                    "boolean" -> valueText(body) == "true"
                    "int" -> valueText(body).toIntOrNull() ?: 0
                    "long" -> valueText(body).toLongOrNull() ?: 0L
                    "float" -> valueText(body).toFloatOrNull() ?: 0f
                    "string-set" -> itemRegex.findAll(body)
                        .map { unescape(it.groupValues[1]) }
                        .toSet()
                    else -> valueText(body)
                }
            }
            result[name] = entries
        }
        return result
    }

    /** 把 4 个 prefs 的内容导出为 XML。 */
    fun export(context: Context): String {
        val groups = LinkedHashMap<String, Map<String, Any?>>()
        PREF_NAMES.forEach { name ->
            val src = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            val map = LinkedHashMap<String, Any?>()
            src.forEach { (k, v) -> map[k] = v }
            groups[name] = map
        }
        return encode(groups)
    }

    /**
     * 从 XML 恢复设置：只写回白名单里的 4 个 prefs，先 clear 再按类型 put，commit 同步提交。
     * 返回恢复的条目总数；解析失败返回 failure。
     */
    fun import(context: Context, xml: String): Result<Int> = runCatching {
        val groups = decode(xml)
        var total = 0
        groups.forEach { (name, entries) ->
            // 拒绝非白名单名字，避免写入其它 SharedPreferences
            if (name !in PREF_NAMES) return@forEach
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            entries.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(
                        key,
                        value.map { it?.toString() ?: "" }.toSet()
                    )
                    else -> editor.putString(key, value?.toString() ?: "")
                }
                total++
            }
            editor.commit()
        }
        total
    }

    fun fileName(): String =
        "FrameBetween_settings_" +
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date()) + ".xml"
}
