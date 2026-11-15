package com.framebetween.app

import com.framebetween.app.core.AssStyle
import com.framebetween.app.core.Commands
import com.framebetween.app.core.FilterFix
import com.framebetween.app.util.formatClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SubtitleAndFilterTest {

    @Test
    fun assRenderContainsLibassSectionsAndChineseText() {
        val ass = AssStyle(text = "中文字幕", position = "bc", fontSizePx = 56).render()
        assertTrue(ass.contains("[Script Info]"))
        assertTrue(ass.contains("[V4+ Styles]"))
        assertTrue(ass.contains("[Events]"))
        assertTrue(ass.contains("Dialogue:"))
        assertTrue(ass.contains("中文字幕"))
        assertTrue(ass.contains("Noto Sans CJK SC"))
        assertTrue(ass.contains(",56,&H00FFFFFF"))
    }

    @Test
    fun assColorUsesBgrOrder() {
        val ass = AssStyle(colorHex = "#FF0000").render()
        assertTrue(ass.contains("&H000000FF"))
    }

    @Test
    fun drawtextStaysNativeAndGetsYuv420pPrefix() {
        val tokens = listOf(
            "-y", "-i", "in.mp4", "-vf",
            "drawtext=text='你好':fontsize=48:x=10:y=10", "-c:v", "copy", "out.mp4"
        )
        val result = FilterFix.apply(tokens)
        assertFalse(result.blocked)
        val chain = result.tokens[result.tokens.indexOf("-vf") + 1]
        assertTrue(chain.contains("drawtext="))
        assertTrue(chain.contains("你好"))
        assertTrue(chain.startsWith("format=yuv420p,"))
        assertFalse(chain.contains("subtitles=filename="))
        // 滤镜必须重编码，-c:v copy 会被改掉
        assertTrue(result.tokens.contains("libx265"))
        assertTrue(result.tokens.contains("-pix_fmt"))
    }

    @Test
    fun drawtextIsNotBlockedWithoutSystemFont() {
        // JVM 环境没有 /system/fonts，drawtext 仍应原样执行而不是被拦截 / 改写
        val tokens = listOf("-i", "in.mp4", "-vf", "drawtext=text=hi", "out.mp4")
        val result = FilterFix.apply(tokens)
        assertFalse(result.blocked)
        assertTrue(result.tokens.any { it.contains("drawtext=text=hi") })
        assertFalse(result.tokens.any { it.contains("subtitles=") })
    }

    @Test
    fun eqChainStillGetsFormatPrefixAndReencode() {
        val tokens = listOf(
            "-y", "-i", "in.mp4", "-vf", "eq=brightness=0.1",
            "-c:v", "copy", "out.mp4"
        )
        val result = FilterFix.apply(tokens)
        assertTrue(result.tokens.any { it.startsWith("format=yuv420p,eq=") })
        assertTrue(result.tokens.contains("libx265"))
        assertTrue(result.tokens.contains("yuv420p"))
    }

    @Test
    fun burnSubtitlesUsesNativeDrawtext() {
        val srt = File.createTempFile("fb-sub", ".srt")
        srt.writeText(
            "1\n00:00:01,000 --> 00:00:03,000\n你好，FrameBetween\n\n" +
                "2\n00:00:04,500 --> 00:00:06,000\nSecond: line\n",
            Charsets.UTF_8
        )
        val args = Commands.burnSubtitles("in.mp4", "out.mp4", srt.absolutePath, 2500)
        val chain = args[args.indexOf("-vf") + 1]
        assertTrue(chain.startsWith("format=yuv420p,drawtext="))
        assertTrue(chain.contains("你好，FrameBetween"))
        assertTrue(chain.contains("enable='between(t,1.000,3.000)'"))
        assertTrue(chain.contains("expansion=none"))
        assertFalse(chain.contains("subtitles=filename="))
        assertTrue(args.contains("libx265"))
        assertTrue(args.contains("2500k"))
        assertTrue(args.contains("out.mp4"))
        srt.delete()
    }

    @Test
    fun burnSubtitlesFallsBackToLibassForUnreadableSubtitle() {
        val args = Commands.burnSubtitles("in.mp4", "out.mp4", "/cache/missing.ass", 2500)
        val chain = args[args.indexOf("-vf") + 1]
        assertTrue(chain.contains("subtitles=filename=/cache/missing.ass"))
        assertTrue(chain.contains("fontsdir=/system/fonts"))
        assertTrue(args.contains("libx265"))
    }

    @Test
    fun formatClockDropsHourBelowOneHour() {
        assertEquals("00:00", formatClock(0))
        assertEquals("02:35", formatClock(155_000))
        assertEquals("01:02:35", formatClock(3_755_000))
    }

    @Test
    fun fromDrawtextParsesSizePositionAndText() {
        val style = AssStyle.fromDrawtext(
            "text='Hello:World':fontsize=64:fontcolor=white:x=(w-text_w)/2:y=h-text_h-10"
        )
        assertEquals(64, style.fontSizePx)
        assertEquals("bc", style.position)
        assertEquals("Hello:World", style.text)
    }
}
