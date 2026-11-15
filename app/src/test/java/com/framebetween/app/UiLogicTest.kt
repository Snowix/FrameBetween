package com.framebetween.app

import com.framebetween.app.core.AudioCutConfig
import com.framebetween.app.core.AudioOutputConfig
import com.framebetween.app.core.Codecs
import com.framebetween.app.core.Commands
import com.framebetween.app.core.Estimate
import com.framebetween.app.util.CommandInsert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 光标插入、码率档位与 4× 变速。 */
class UiLogicTest {

    @Test
    fun `insert into empty command`() {
        val r = CommandInsert.atCursor("", 0, 0, "/sdcard/in.mp4")
        assertEquals("/sdcard/in.mp4", r.text)
        assertEquals(r.text.length, r.caret)
    }

    @Test
    fun `insert at middle cursor keeps both sides and adds separator`() {
        val original = "-i a.mp4 -c:v libx265 out.mp4"
        val caret = original.indexOf("-c:v")
        val r = CommandInsert.atCursor(original, caret, caret, "-vf hflip")
        assertEquals("-i a.mp4 -vf hflip -c:v libx265 out.mp4", r.text)
        assertEquals(caret + "-vf hflip".length, r.caret)
    }

    @Test
    fun `insert replaces current selection`() {
        val original = "-i old.mp4 out.mp4"
        val start = original.indexOf("old.mp4")
        val end = start + "old.mp4".length
        val r = CommandInsert.atCursor(original, start, end, "new.mp4")
        assertEquals("-i new.mp4 out.mp4", r.text)
        assertEquals(start + "new.mp4".length, r.caret)
    }

    @Test
    fun `existing whitespace is not duplicated`() {
        val r = CommandInsert.atCursor("-i  -c:v copy", 4, 4, "-an")
        assertEquals("-i  -an -c:v copy", r.text)
        assertEquals(7, r.caret)
    }

    @Test
    fun `empty insert and out of range selection are safe`() {
        val empty = CommandInsert.atCursor("abc", 1, 2, "")
        assertEquals("abc", empty.text)
        assertEquals(1, empty.caret)
        val clamped = CommandInsert.atCursor("abc", -5, 99, "X")
        assertEquals("X", clamped.text)
        assertEquals(1, clamped.caret)
    }

    @Test
    fun `bitrate presets are exactly five discrete steps`() {
        assertEquals(listOf(0, 96, 128, 192, 320), Codecs.bitratePresets)
        assertEquals("不变", Codecs.bitrateLabel(0))
        assertEquals("320 kbps", Codecs.bitrateLabel(320))
    }

    @Test
    fun `unchanged bitrate omits -b-a from audio cut`() {
        val args = Commands.audioCut(
            "/data/in.mp3", "/data/out.mp3",
            AudioCutConfig(
                startMs = 0, endMs = 5_000, speed = 1f,
                output = AudioOutputConfig("mp3", 44100, 0)
            )
        )
        assertFalse(args.contains("-b:a"))
        assertTrue(args.contains("-ar"))
        assertEquals(
            Estimate.targetBytes(0, 192, 10_000L),
            Estimate.audioBytes(AudioOutputConfig("mp3", 44100, 0), 10_000L)
        )
    }

    @Test
    fun `speed 4x produces two chained atempo stages`() {
        val args = Commands.audioCut(
            "/data/in.mp3", "/data/out.mp3",
            AudioCutConfig(
                startMs = 0, endMs = 8_000, speed = 4f,
                output = AudioOutputConfig("mp3", 44100, 128)
            )
        )
        assertEquals("atempo=2.0000,atempo=2.0000", args[args.indexOf("-filter:a") + 1])
        assertTrue(args.contains("128k"))
    }
}
