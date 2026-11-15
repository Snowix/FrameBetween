package com.framebetween.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 用 Android Canvas 把文字渲染成透明 PNG，再交给 FFmpeg 的 overlay 滤镜合成。
 * FFmpeg 9.0.2 已有原生 drawtext；文字水印仍走 PNG，是为了让预览支持拖动定位、
 * 双指缩放与透明度实时调节，并非因为缺少字体库。中文由系统字体绘制，不会出现方框。
 */
object WatermarkAssets {

    data class Style(
        val text: String,
        val fontSizePx: Int = 56,
        val color: Int = Color.WHITE,
        val opacity: Float = 0.9f,
        val strokeWidthPx: Float = 4f
    )

    fun create(context: Context, config: Style): String {
        val lines = config.text.replace("\r", "").split("\n")
            .map { it.ifBlank { " " } }
            .ifEmpty { listOf("FrameBetween") }
        val size = config.fontSizePx.coerceIn(16, 240).toFloat()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textSize = size
            color = config.color
            alpha = (255 * config.opacity.coerceIn(0.1f, 1f)).toInt()
            this.style = Paint.Style.FILL
        }
        val stroke = Paint(fill).apply {
            color = Color.BLACK
            alpha = 170
            this.style = Paint.Style.STROKE
            strokeWidth = config.strokeWidthPx.coerceIn(1f, 18f)
            strokeJoin = Paint.Join.ROUND
        }
        val bounds = Rect()
        var maxWidth = 0
        lines.forEach { line ->
            fill.getTextBounds(line, 0, line.length, bounds)
            maxWidth = maxOf(maxWidth, bounds.width())
        }
        val lineHeight = (size * 1.35f).toInt()
        val pad = (size * 0.35f).toInt().coerceAtLeast(12)
        val width = (maxWidth + pad * 2).coerceAtLeast(2)
        val height = (lineHeight * lines.size + pad * 2).coerceAtLeast(2)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val baseY = pad - bounds.top + (lineHeight - (bounds.bottom - bounds.top)) / 2f
        lines.forEachIndexed { index, line ->
            val y = baseY + index * lineHeight
            canvas.drawText(line, pad.toFloat(), y, stroke)
            canvas.drawText(line, pad.toFloat(), y, fill)
        }

        val dir = File(context.cacheDir, "tmp").apply { mkdirs() }
        // 按内容哈希命名：用户连续输入文字时不会在缓存里堆出一串重复 PNG
        val file = File(dir, "watermark_${Integer.toHexString(config.hashCode())}.png")
        if (!file.exists()) {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bitmap.recycle()
        return file.absolutePath
    }

    /** 导入图片水印：按 mime 保留扩展名，避免 FFmpeg 靠内容猜错格式。 */
    fun importImage(context: Context, uri: Uri): String? = runCatching {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val ext = when {
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("webp") -> "webp"
            mime.contains("bmp") -> "bmp"
            else -> "png"
        }
        val dir = File(context.cacheDir, "tmp").apply { mkdirs() }
        val file = File(dir, "wm_import_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        file.absolutePath
    }.getOrNull()

}
