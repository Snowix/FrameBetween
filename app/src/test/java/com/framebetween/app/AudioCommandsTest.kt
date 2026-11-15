package com.framebetween.app

import com.framebetween.app.core.AudioCutConfig
import com.framebetween.app.core.AudioJoinConfig
import com.framebetween.app.core.AudioJoinMode
import com.framebetween.app.core.AudioOutputConfig
import com.framebetween.app.core.Codecs
import com.framebetween.app.core.Commands
import com.framebetween.app.core.ConvertConfig
import com.framebetween.app.core.Estimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCommandsTest {

    private val a = "/data/a.mp3"
    private val b = "/data/b.wav"
    private val output = "/data/out.mp3"

    private fun chain(args: List<String>): String =
        args[args.indexOf("-filter_complex") + 1]

    @Test
    fun `sequence join normalizes every track and concatenates`() {
        val args = Commands.audioJoin(
            listOf(a, b), output,
            AudioJoinConfig(
                mode = AudioJoinMode.SEQUENCE,
                volumes = listOf(0.8f, 1.4f),
                output = AudioOutputConfig("mp3", 48000, 192)
            )
        )
        assertTrue(args.contains(a))
        assertTrue(args.contains(b))
        val filter = chain(args)
        assertTrue(filter.contains("volume=0.80"))
        assertTrue(filter.contains("volume=1.40"))
        assertTrue(filter.contains("sample_rates=48000"))
        assertTrue(filter.contains("sample_fmts=fltp"))
        assertTrue(filter.contains("concat=n=2:v=0:a=1"))
        assertTrue(args.contains("-ar"))
        assertTrue(args.contains("48000"))
        assertTrue(args.contains("192k"))
        assertEquals(output, args.last())
    }

    @Test
    fun `mix join uses amix and ignores crossfade`() {
        val args = Commands.audioJoin(
            listOf(a, b), output,
            AudioJoinConfig(mode = AudioJoinMode.MIX, crossfadeMs = 2000)
        )
        val filter = chain(args)
        assertTrue(filter.contains("amix=inputs=2:duration=longest:normalize=0"))
        assertFalse(filter.contains("acrossfade"))
    }

    @Test
    fun `crossfade is applied between adjacent tracks only in sequence mode`() {
        val args = Commands.audioJoin(
            listOf(a, b), output,
            AudioJoinConfig(mode = AudioJoinMode.SEQUENCE, crossfadeMs = 1500)
        )
        val filter = chain(args)
        assertTrue(filter.contains("acrossfade=d=1.50"))
        assertFalse(filter.contains("concat=n=2"))
        assertTrue(filter.endsWith("[out]"))
    }

    @Test
    fun `audio cut trims and applies speed`() {
        val args = Commands.audioCut(
            a, output,
            AudioCutConfig(
                startMs = 2_000,
                endMs = 7_000,
                speed = 1.5f,
                output = AudioOutputConfig("m4a", 44100, 160)
            )
        )
        assertEquals("2.0", args[args.indexOf("-ss") + 1])
        assertEquals("5.0", args[args.indexOf("-t") + 1])
        assertEquals("atempo=1.5000", args[args.indexOf("-filter:a") + 1])
        assertTrue(args.contains("aac"))
        assertTrue(args.contains("160k"))
        assertEquals(output, args.last())
    }

    @Test
    fun `atempo chain covers extreme speeds`() {
        assertEquals("", Commands.atempoChain(1f))
        assertEquals("atempo=0.5000", Commands.atempoChain(0.5f))
        assertEquals("atempo=2.0000", Commands.atempoChain(2f))
        assertTrue(Commands.atempoChain(0.25f).contains(",atempo="))
        assertTrue(Commands.atempoChain(4f).contains(",atempo="))
    }

    @Test
    fun `default video codec is h265 everywhere`() {
        assertEquals("libx265", Codecs.DEFAULT_VIDEO)
        assertEquals("libx265", ConvertConfig().videoCodec)
        assertEquals("libx265", Commands.defaultCodecs("mp4").first)
        assertEquals("libx265", Commands.defaultCodecs("mkv").first)
        assertEquals("libvpx-vp9", Commands.defaultCodecs("webm").first)
        assertEquals("none", Commands.defaultCodecs("mp3").first)
    }

    @Test
    fun `audio size estimation handles lossless formats`() {
        assertEquals(Estimate.targetBytes(0, 192, 10_000L), Estimate.audioBytes(AudioOutputConfig(), 10_000L))
        assertEquals(Estimate.targetBytes(0, 1411, 10_000L), Estimate.audioBytes(AudioOutputConfig("wav"), 10_000L))
        assertEquals(0L, Estimate.audioBytes(AudioOutputConfig(), 0L))
    }
}
