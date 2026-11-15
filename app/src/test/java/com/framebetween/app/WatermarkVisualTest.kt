package com.framebetween.app

import com.framebetween.app.core.Commands
import com.framebetween.app.core.WatermarkConfig
import com.framebetween.app.core.WatermarkMotion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 随机显示 / 边缘反弹、透明度、缩放、显示时间段与拖动定位。 */
class WatermarkVisualTest {

    private fun chain(cfg: WatermarkConfig, motion: WatermarkMotion = WatermarkMotion.STATIC): String {
        val args = Commands.watermarkDynamic("/data/in.mp4", "/data/out.mp4", cfg, motion)
        return args[args.indexOf("-filter_complex") + 1]
    }

    @Test
    fun `default config keeps the v1_14 command shape`() {
        val c = chain(WatermarkConfig("/data/wm.png", "br", 24))
        assertTrue(c.startsWith("[0:v][1:v]overlay="))
        assertFalse(c.contains("[wm]"))
        assertFalse(c.contains("colorchannelmixer"))
        assertFalse(c.contains("between("))
        assertTrue(c.contains("x=W-w-24"))
    }

    @Test
    fun `random and bounce motions produce valid time expressions`() {
        val cfg = WatermarkConfig("/data/wm.png")
        val random = chain(cfg, WatermarkMotion.RANDOM)
        assertTrue(random.contains("7919"))
        assertTrue(random.contains("104729"))
        assertTrue(random.contains("enable="))
        assertFalse(random.contains("random("))

        val bounce = chain(cfg, WatermarkMotion.BOUNCE)
        assertTrue(bounce.contains("abs(1-mod(t/4,2))"))
        assertTrue(bounce.contains("abs(1-mod(t/6,2))"))
        assertTrue(bounce.contains("eval=frame"))
    }

    @Test
    fun `opacity and scale build one prepared watermark layer`() {
        val c = chain(WatermarkConfig("/data/wm.png", opacity = 0.4f, scale = 1.5f))
        assertTrue(
            c.startsWith(
                "[1:v]format=rgba,colorchannelmixer=aa=0.4000," +
                    "scale=iw*1.5000:ih*1.5000[wm];[0:v][wm]overlay="
            )
        )
    }

    @Test
    fun `display window combines with motion enable`() {
        val c = chain(
            WatermarkConfig("/data/wm.png", startSec = 2.0, endSec = 8.5),
            WatermarkMotion.BLINK
        )
        assertTrue(c.contains("enable='lt(mod(t,2),1)*between(t,2.0000,8.5000)'"))
    }

    @Test
    fun `dragged position overrides the nine grid preset`() {
        val c = chain(WatermarkConfig("/data/wm.png", normX = 0.25f, normY = 0.75f))
        assertTrue(c.contains("x=0.2500*(W-w):y=0.7500*(H-h)"))
        assertFalse(c.contains("x=W-w-24"))
    }

    @Test
    fun `invalid window and out of range values are clamped`() {
        val c = chain(
            WatermarkConfig(
                "/data/wm.png",
                opacity = 5f,
                scale = 0.01f,
                startSec = 9.0,
                endSec = 2.0,
                normX = 2f,
                normY = -3f
            )
        )
        assertFalse(c.contains("colorchannelmixer"))
        assertFalse(c.contains("between("))
        assertTrue(c.contains("scale=iw*0.1000:ih*0.1000"))
        assertTrue(c.contains("x=W-w-24")) // 负的 normY 让整组自定义坐标失效，回落预设
    }
}
