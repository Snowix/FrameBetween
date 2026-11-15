package com.framebetween.app.core

import java.util.Locale
import kotlin.math.roundToInt

/**
 * 去水印选框的几何运算。坐标一律是「视频像素」；预览里的屏幕位移先按
 * unitX / unitY（1 个视频像素对应的屏幕像素数）换算回来。
 * 纯 Kotlin，不依赖 Android / Compose，便于 JVM 单元测试。
 */
object WatermarkGeometry {

    data class Region(val x: Int, val y: Int, val width: Int, val height: Int) {
        val right: Int get() = x + width
        val bottom: Int get() = y + height
    }

    /** 把任意输入约束成「完全落在视频画面内且至少 2×2」的合法区域。 */
    fun clamp(region: Region, videoW: Int, videoH: Int): Region {
        val w = videoW.coerceAtLeast(2)
        val h = videoH.coerceAtLeast(2)
        val x = region.x.coerceIn(0, w - 2)
        val y = region.y.coerceIn(0, h - 2)
        return Region(
            x, y,
            region.width.coerceIn(2, (w - x).coerceAtLeast(2)),
            region.height.coerceIn(2, (h - y).coerceAtLeast(2))
        )
    }

    /** 预览像素坐标 → 0..1 归一化坐标，供 overlay 表达式使用。 */
    fun normOf(left: Float, origin: Float, max: Float): Float =
        if (max <= 0f) 0f else ((left - origin) / max).coerceIn(0f, 1f)

    /**
     * 九宫格预设位置 → 水印左上角的 0..1 归一化坐标，供视频预览定位。
     * 与 Commands.overlayExpression 的 x/y 表达式一一对应：左列 x=m、中列 (W-w)/2、
     * 右列 W-w-m；上排 y=m、中排 (H-h)/2、下排 H-h-m。margin 为预览屏幕像素。
     * 纯 Kotlin，便于 JVM 单测验证「选了位置预览就会动」。
     */
    fun presetNorm(
        position: String,
        wmW: Float,
        wmH: Float,
        boxW: Float,
        boxH: Float,
        margin: Float
    ): Pair<Float, Float> {
        val maxX = (boxW - wmW).coerceAtLeast(1f)
        val maxY = (boxH - wmH).coerceAtLeast(1f)
        // 边距最多吃掉半个可移动范围，避免超大输入把左列推到画面右侧（上下同理）
        val mx = margin.coerceIn(0f, maxX / 2f)
        val my = margin.coerceIn(0f, maxY / 2f)
        val key = position.lowercase(Locale.US)
        val x = when (key) {
            "tl", "ml", "bl" -> mx
            "tr", "mr", "br" -> maxX - mx
            else -> maxX / 2f
        }
        val y = when (key) {
            "tl", "tc", "tr" -> my
            "bl", "bc", "br" -> maxY - my
            else -> maxY / 2f
        }
        return (x / maxX).coerceIn(0f, 1f) to (y / maxY).coerceIn(0f, 1f)
    }

    /** 拖动选框内部：只平移，尺寸不变。 */
    fun move(
        region: Region,
        dxPixels: Float,
        dyPixels: Float,
        unitX: Float,
        unitY: Float,
        videoW: Int,
        videoH: Int
    ): Region {
        val r = clamp(region, videoW, videoH)
        if (unitX <= 0f || unitY <= 0f) return r
        val vx = (dxPixels / unitX).roundToInt()
        val vy = (dyPixels / unitY).roundToInt()
        val w = videoW.coerceAtLeast(2)
        val h = videoH.coerceAtLeast(2)
        return Region(
            (r.x + vx).coerceIn(0, (w - r.width).coerceAtLeast(0)),
            (r.y + vy).coerceIn(0, (h - r.height).coerceAtLeast(0)),
            r.width,
            r.height
        )
    }

    /**
     * 拖动四角把手改变尺寸，对角顶点固定不动。
     * corner: 0 左上 / 1 右上 / 2 左下 / 3 右下。
     */
    fun resize(
        region: Region,
        corner: Int,
        dxPixels: Float,
        dyPixels: Float,
        unitX: Float,
        unitY: Float,
        videoW: Int,
        videoH: Int
    ): Region {
        val r = clamp(region, videoW, videoH)
        if (unitX <= 0f || unitY <= 0f) return r
        val vx = (dxPixels / unitX).roundToInt()
        val vy = (dyPixels / unitY).roundToInt()
        val w = videoW.coerceAtLeast(2)
        val h = videoH.coerceAtLeast(2)
        return when (corner) {
            0 -> { // 左上：右下固定
                val nx = (r.x + vx).coerceIn(0, r.right - 2)
                val ny = (r.y + vy).coerceIn(0, r.bottom - 2)
                Region(nx, ny, r.right - nx, r.bottom - ny)
            }
            1 -> { // 右上：左下固定
                val nw = (r.width + vx).coerceIn(2, (w - r.x).coerceAtLeast(2))
                val ny = (r.y + vy).coerceIn(0, r.bottom - 2)
                Region(r.x, ny, nw, r.bottom - ny)
            }
            2 -> { // 左下：右上固定
                val nx = (r.x + vx).coerceIn(0, r.right - 2)
                val nh = (r.height + vy).coerceIn(2, (h - r.y).coerceAtLeast(2))
                Region(nx, r.y, r.right - nx, nh)
            }
            else -> { // 右下：左上固定
                Region(
                    r.x,
                    r.y,
                    (r.width + vx).coerceIn(2, (w - r.x).coerceAtLeast(2)),
                    (r.height + vy).coerceIn(2, (h - r.y).coerceAtLeast(2))
                )
            }
        }
    }
}
