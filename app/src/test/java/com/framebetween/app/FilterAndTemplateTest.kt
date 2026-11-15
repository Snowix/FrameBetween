package com.framebetween.app

import com.framebetween.app.core.BrightnessConfig
import com.framebetween.app.core.Commands
import com.framebetween.app.core.FilterFix
import com.framebetween.app.core.TrimConfig
import com.framebetween.app.core.TrimMode
import com.framebetween.app.core.WatermarkConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterAndTemplateTest {

    private val input = "/data/in.mp4"
    private val output = "/data/out.mp4"

    @Test
    fun `native drawtext is preserved instead of being rewritten to libass`() {
        val result = FilterFix.apply(
            listOf("-y", "-i", input, "-vf", "drawtext=text='FrameBetween'", output)
        )
        assertFalse(result.blocked)
        val chain = result.tokens[result.tokens.indexOf("-vf") + 1]
        assertTrue(chain.contains("drawtext="))
        assertTrue(chain.contains("FrameBetween"))
        assertTrue(chain.startsWith("format=yuv420p,"))
        assertFalse(result.tokens.any { it.contains("subtitles=filename=") })
    }

    @Test
    fun `eq filter gets yuv420p prefix and explicit pix_fmt`() {
        val result = FilterFix.apply(
            listOf("-y", "-i", input, "-vf", "eq=brightness=0.1", "-c:v", "libx264", output)
        )
        assertFalse(result.blocked)
        val chain = result.tokens[result.tokens.indexOf("-vf") + 1]
        assertEquals("format=yuv420p,eq=brightness=0.1", chain)
        assertTrue(result.tokens.contains("-pix_fmt"))
        assertTrue(result.tokens.contains("yuv420p"))
        assertEquals(output, result.tokens.last())
    }

    @Test
    fun `eq filter cannot be combined with stream copy`() {
        val result = FilterFix.apply(
            listOf("-y", "-i", input, "-vf", "eq=brightness=0.1", "-c", "copy", output)
        )
        assertFalse(result.blocked)
        assertFalse(result.tokens.any { it == "-c" })
        assertTrue(result.tokens.contains("libx265"))
        assertTrue(result.tokens.contains("-c:a"))
        assertTrue(result.notes.isNotEmpty())
    }

    @Test
    fun `brightness template uses a build compatible filter chain`() {
        val args = Commands.brightness(input, output, BrightnessConfig(), 2500)
        assertTrue(
            args.contains(
                "format=yuv420p,eq=" +
                    "brightness=0.06:contrast=1.10:saturation=1.05:gamma=1.00"
            )
        )
        assertTrue(args.contains("-pix_fmt"))
        assertTrue(args.contains("-c:v"))
        assertTrue(args.contains("libx265"))
        assertFalse(args.any { it.contains("drawtext") })
        assertEquals(output, args.last())
    }

    @Test
    fun `watermark template uses overlay png instead of drawtext`() {
        val args = Commands.watermark(input, output, WatermarkConfig("/tmp/wm.png", "br"), 0)
        assertTrue(args.contains("/tmp/wm.png"))
        assertTrue(args.contains("-filter_complex"))
        val chain = args[args.indexOf("-filter_complex") + 1]
        assertTrue(chain.contains("overlay=x=W-w-24:y=H-h-24"))
        assertTrue(chain.contains("format=yuv420p"))
        assertFalse(chain.contains("drawtext"))
        assertFalse(chain.contains(";"))
        assertEquals(output, args.last())
    }

    @Test
    fun `watermark overlay positions are distinct`() {
        val topLeft = Commands.watermark(input, output, WatermarkConfig("/tmp/wm.png", "tl"), 0)
        val center = Commands.watermark(input, output, WatermarkConfig("/tmp/wm.png", "mc"), 0)
        assertTrue(topLeft[topLeft.indexOf("-filter_complex") + 1].contains("x=24:y=24"))
        assertTrue(center[center.indexOf("-filter_complex") + 1].contains("x=(W-w)/2:y=(H-h)/2"))
    }

    @Test
    fun `trim defaults to fastest copy mode`() {
        assertEquals(TrimMode.COPY, TrimConfig(0L, 10_000L).mode)
        val args = Commands.trim(input, output, TrimConfig(0L, 10_000L), 0)
        assertTrue(args.contains("copy"))
        assertFalse(args.contains("libx264"))
    }
}
