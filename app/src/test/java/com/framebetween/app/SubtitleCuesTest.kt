package com.framebetween.app

import com.framebetween.app.core.SubtitleCue
import com.framebetween.app.core.SubtitleCues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCuesTest {

    @Test
    fun `srt cues keep start end and chinese text`() {
        val cues = SubtitleCues.parse(
            "1\n00:00:01,000 --> 00:00:03,000\n你好，世界\n\n" +
                "2\n00:00:04,500 --> 00:00:06,000\nSecond: line\n",
            "sub.srt"
        )
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(3000L, cues[0].endMs)
        assertEquals("你好，世界", cues[0].text)
        assertEquals(4500L, cues[1].startMs)
        assertEquals("Second: line", cues[1].text)
    }

    @Test
    fun `vtt header tags and cue settings are ignored`() {
        val cues = SubtitleCues.parse(
            "WEBVTT\n\n1\n00:00:01.000 --> 00:00:03.000 align:middle\n" +
                "<i>Hello</i>\n\n2\n00:00:04.000 --> 00:00:05.000\nBye\n",
            "sub.vtt"
        )
        assertEquals(2, cues.size)
        assertEquals("Hello", cues[0].text)
        assertEquals(4000L, cues[1].startMs)
    }

    @Test
    fun `ass dialogue strips override tags and line breaks`() {
        val cues = SubtitleCues.parse(
            "[Script Info]\nScriptType: v4.00+\n\n[Events]\n" +
                "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n" +
                "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,{\\b1}你好\\N第二行\n",
            "sub.ass"
        )
        assertEquals(1, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(3000L, cues[0].endMs)
        assertEquals("你好 第二行", cues[0].text)
    }

    @Test
    fun `drawtext chain escapes text and gates each cue by time`() {
        val chain = SubtitleCues.drawtextChain(
            listOf(SubtitleCue(1000L, 3000L, "It's 50%: ok")), null
        )
        assertTrue(chain.startsWith("drawtext=text='"))
        assertTrue(chain.contains("It\\'s 50%: ok"))
        assertTrue(chain.contains("enable='between(t,1.000,3.000)'"))
        assertTrue(chain.contains("expansion=none"))
        assertTrue(chain.contains("fontsize=h/22"))
        assertFalse(chain.contains("fontfile="))
    }

    @Test
    fun `drawtext chain is capped to avoid oversized command lines`() {
        val cues = (1..200).map { SubtitleCue(it * 1000L, it * 1000L + 500L, "L$it") }
        val chain = SubtitleCues.drawtextChain(cues, null)
        assertEquals(SubtitleCues.MAX_CUES, chain.split("drawtext=").size - 1)
    }

    @Test
    fun `escapeText protects quotes and backslashes`() {
        assertEquals("a\\\\b\\'c(d)", SubtitleCues.escapeText("a\\b'c{d}"))
    }
}
