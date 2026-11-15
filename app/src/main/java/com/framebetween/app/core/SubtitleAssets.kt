package com.framebetween.app.core
import com.framebetween.app.core.L10n

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 字幕 / 文字素材：把 ASS 文本与外部字幕文件写进应用缓存目录。
 * FFmpeg 9.0.2 起字幕烧录主路径使用原生 drawtext（见 SubtitleCues），
 * 这里的缓存文件供解析与 libass subtitles 滤镜兜底读取；
 * 缓存目录会被「清除软件缓存」整体回收。
 */
object SubtitleAssets {

    private fun dir(context: Context): File = File(context.cacheDir, "subtitles").apply { mkdirs() }

    fun create(context: Context, style: AssStyle): String {
        val key = listOf(
            style.text, style.fontSizePx, style.colorHex, style.outlineHex,
            style.position, style.startMs, style.endMs, style.playResX, style.playResY
        ).joinToString("|")
        val file = File(dir(context), "style_" + Integer.toHexString(key.hashCode()) + ".ass")
        file.writeText(style.render(), Charsets.UTF_8)
        return file.absolutePath
    }

    /**
     * 8.1.x 遗留：把 drawtext 参数串转成 ASS 字幕文件。
     * FFmpeg 9.0.2 已含原生 drawtext，主链路不再调用，仅保留兼容与单测覆盖。
     */
    fun fromDrawtext(context: Context, expr: String): String =
        create(context, AssStyle.fromDrawtext(expr))

    /** 导入 SRT / ASS / SSA / VTT，由 SubtitleCues 解析成 drawtext 链。 */
    fun importSubtitle(context: Context, uri: Uri, displayName: String?): String? = runCatching {
        val raw = displayName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val ext = raw.takeIf { it in setOf("srt", "ass", "ssa", "vtt") } ?: "srt"
        val file = File(dir(context), "import_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        file.absolutePath
    }.getOrNull()
}
