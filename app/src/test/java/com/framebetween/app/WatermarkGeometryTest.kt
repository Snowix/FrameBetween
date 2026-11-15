package com.framebetween.app

import com.framebetween.app.core.WatermarkGeometry
import com.framebetween.app.core.WatermarkGeometry.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 去水印可视化选框的坐标换算，纯几何运算不依赖 Android。 */
class WatermarkGeometryTest {

    @Test
    fun `clamp keeps the region inside the frame`() {
        assertEquals(
            Region(0, 0, 2, 2),
            WatermarkGeometry.clamp(Region(-30, -20, 0, 0), 1920, 1080)
        )
        assertEquals(
            Region(1900, 1000, 20, 80),
            WatermarkGeometry.clamp(Region(1900, 1000, 200, 200), 1920, 1080)
        )
    }

    @Test
    fun `move translates screen drag back to video pixels`() {
        val start = Region(100, 50, 200, 100)
        // 预览里 1 个视频像素 = 0.5 屏幕像素，拖 50 屏幕像素 = 100 视频像素
        assertEquals(
            Region(200, 150, 200, 100),
            WatermarkGeometry.move(start, 50f, 50f, 0.5f, 0.5f, 1920, 1080)
        )
        // 拖出右边界会被夹住
        val edge = WatermarkGeometry.move(start, 100000f, 0f, 1f, 1f, 1920, 1080)
        assertEquals(1720, edge.x)
        assertEquals(start.width, edge.width)
    }

    @Test
    fun `resize keeps the opposite corner fixed`() {
        val start = Region(100, 50, 200, 100)
        val bottomRight = WatermarkGeometry.resize(start, 3, 40f, 20f, 1f, 1f, 1920, 1080)
        assertEquals(Region(100, 50, 240, 120), bottomRight)

        val topLeft = WatermarkGeometry.resize(start, 0, 40f, 20f, 1f, 1f, 1920, 1080)
        assertEquals(140, topLeft.x)
        assertEquals(70, topLeft.y)
        assertEquals(300, topLeft.right) // 右下角仍固定在 300 / 150
        assertEquals(150, topLeft.bottom)

        val topRight = WatermarkGeometry.resize(start, 1, 40f, 20f, 1f, 1f, 1920, 1080)
        assertEquals(100, topRight.x)
        assertEquals(240, topRight.width)
        assertEquals(150, topRight.bottom)

        val bottomLeft = WatermarkGeometry.resize(start, 2, 40f, 20f, 1f, 1f, 1920, 1080)
        assertEquals(140, bottomLeft.x)
        assertEquals(120, bottomLeft.height)
        assertEquals(300, bottomLeft.right)
    }

    @Test
    fun `zero unit scale is a no-op instead of dividing by zero`() {
        val start = Region(10, 10, 100, 50)
        assertEquals(start, WatermarkGeometry.move(start, 20f, 20f, 0f, 0f, 1920, 1080))
        assertEquals(start, WatermarkGeometry.resize(start, 3, 20f, 20f, 0f, 0f, 1920, 1080))
    }

    @Test
    fun `resize never shrinks below two pixels or leaves the frame`() {
        val tiny = WatermarkGeometry.resize(Region(100, 100, 40, 40), 0, 100000f, 100000f, 1f, 1f, 1920, 1080)
        assertEquals(2, tiny.width)
        assertEquals(2, tiny.height)
        val huge = WatermarkGeometry.resize(Region(100, 100, 40, 40), 3, 100000f, 100000f, 1f, 1f, 1920, 1080)
        assertEquals(1820, huge.width)
        assertEquals(980, huge.height)
    }

    @Test
    fun `pan to normalized overlay coordinates clamps at both ends`() {
        // rect.left = 20，可移动范围 400：left 在 20..420 映射到 0..1
        assertEquals(0f, WatermarkGeometry.normOf(20f, 20f, 400f), 0f)
        assertEquals(0.25f, WatermarkGeometry.normOf(120f, 20f, 400f), 0.0001f)
        assertEquals(1f, WatermarkGeometry.normOf(420f, 20f, 400f), 0f)
        // 拖出画面或可用范围为 0 时都不能产生非法坐标
        assertEquals(0f, WatermarkGeometry.normOf(-500f, 20f, 400f), 0f)
        assertEquals(1f, WatermarkGeometry.normOf(9000f, 20f, 400f), 0f)
        assertEquals(0f, WatermarkGeometry.normOf(20f, 20f, 0f), 0f)
    }

    @Test
    fun `preset positions map to nine distinct preview coordinates`() {
        val keys = listOf("tl", "tc", "tr", "ml", "mc", "mr", "bl", "bc", "br")
        val values = keys.map {
            WatermarkGeometry.presetNorm(it, 100f, 50f, 1000f, 500f, 20f)
        }
        // 修复前九个预设被 coerce 到 (0,0) 全部堆在左上角
        assertEquals(9, values.toSet().size)
        assertEquals(0.5f, values[4].first, 0.0001f)
        assertEquals(0.5f, values[4].second, 0.0001f)
        assertTrue("左上应在右上的左侧", values[0].first < values[2].first)
        assertTrue("左上应在左下的上方", values[0].second < values[6].second)
        assertTrue("右下应贴近画面右下", values[8].first > 0.9f && values[8].second > 0.9f)
    }

    @Test
    fun `preset margin shifts the corner inward without flipping`() {
        val flush = WatermarkGeometry.presetNorm("br", 100f, 50f, 1000f, 500f, 0f)
        val inset = WatermarkGeometry.presetNorm("br", 100f, 50f, 1000f, 500f, 80f)
        assertEquals(1f, flush.first, 0.0001f)
        assertTrue(inset.first < flush.first)
        assertTrue(inset.second < flush.second)
        // 超大边距最多推到中点，不会把左上角翻到右边 / 下边
        val clamped = WatermarkGeometry.presetNorm("tl", 100f, 50f, 1000f, 500f, 5000f)
        assertEquals(0.5f, clamped.first, 0.0001f)
        assertEquals(0.5f, clamped.second, 0.0001f)
    }

    @Test
    fun `unknown preset falls back to centered instead of top-left`() {
        val fallback = WatermarkGeometry.presetNorm("nope", 100f, 50f, 1000f, 500f, 20f)
        assertEquals(0.5f, fallback.first, 0.0001f)
        assertEquals(0.5f, fallback.second, 0.0001f)
    }
}
