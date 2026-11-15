package com.framebetween.app

import com.framebetween.app.core.HardwareCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareCodecTest {

    private val transcode = listOf("-y", "-i", "/a.mp4", "-c:v", "libx265", "/b.mp4")

    @Test
    fun `injects hwaccel right before the first input`() {
        assertEquals(
            listOf("-y", "-hwaccel", "mediacodec", "-i", "/a.mp4", "-c:v", "libx265", "/b.mp4"),
            HardwareCodec.withHardwareDecode(transcode, enabled = true, supported = true)
        )
    }

    @Test
    fun `keeps input seeking options in front of hwaccel`() {
        val seek = listOf("-y", "-ss", "5.0", "-i", "/a.mp4", "-t", "10.0", "-c", "copy", "/b.mp4")
        assertEquals(seek, HardwareCodec.withHardwareDecode(seek, true, true))
    }

    @Test
    fun `disabled or unsupported leaves command untouched`() {
        assertEquals(transcode, HardwareCodec.withHardwareDecode(transcode, false, true))
        assertEquals(transcode, HardwareCodec.withHardwareDecode(transcode, true, false))
        assertEquals(emptyList<String>(), HardwareCodec.withHardwareDecode(emptyList(), true, true))
    }

    @Test
    fun `stream copy and audio only commands are skipped`() {
        val copy = listOf("-y", "-i", "/a.mp4", "-c", "copy", "/b.mp4")
        val copyVideo = listOf("-y", "-i", "/a.mp4", "-c:v", "copy", "-c:a", "aac", "/b.mp4")
        val audioOnly = listOf("-y", "-i", "/a.mp4", "-vn", "-c:a", "aac", "/b.m4a")
        assertEquals(copy, HardwareCodec.withHardwareDecode(copy, true, true))
        assertEquals(copyVideo, HardwareCodec.withHardwareDecode(copyVideo, true, true))
        assertEquals(audioOnly, HardwareCodec.withHardwareDecode(audioOnly, true, true))
    }

    @Test
    fun `never doubles an explicit hwaccel and skips commands without -i`() {
        val explicit = listOf("-y", "-hwaccel", "auto", "-i", "/a.mp4", "-c:v", "libx264", "/b.mp4")
        assertEquals(explicit, HardwareCodec.withHardwareDecode(explicit, true, true))
        assertEquals(
            listOf("-hide_banner", "-encoders"),
            HardwareCodec.withHardwareDecode(listOf("-hide_banner", "-encoders"), true, true)
        )
    }

    @Test
    fun `filter chain commands are never injected with hwaccel`() {
        val vf = listOf("-y", "-i", "/a.mp4", "-vf", "scale=1280:720", "-c:v", "libx265", "/b.mp4")
        val complex = listOf(
            "-y", "-i", "/a.mp4", "-i", "/wm.png",
            "-filter_complex", "[0:v][1:v]overlay=0:0[outv]",
            "-map", "[outv]", "-c:v", "libx265", "/b.mp4"
        )
        assertEquals(vf, HardwareCodec.withHardwareDecode(vf, true, true))
        assertEquals(complex, HardwareCodec.withHardwareDecode(complex, true, true))
        val decision = HardwareCodec.decide(complex, true, true)
        assertEquals(false, decision.injected)
        assertTrue(decision.reason.isNotBlank())
    }

    @Test
    fun `extended stream copy spellings are recognized`() {
        val variants = listOf(
            listOf("-y", "-i", "/a.mp4", "-c:v:0", "copy", "/b.mp4"),
            listOf("-y", "-i", "/a.mp4", "-codec:v", "copy", "/b.mp4"),
            listOf("-y", "-i", "/a.mp4", "-codec:a", "copy", "/b.m4a"),
            listOf("-y", "-i", "/a.mp4", "-vcodec", "copy", "/b.mp4"),
            listOf("-y", "-i", "/a.mp4", "-acodec", "copy", "/b.m4a")
        )
        variants.forEach { args ->
            assertEquals(args, HardwareCodec.withHardwareDecode(args, true, true))
        }
    }

    @Test
    fun `decide reports whether hwaccel was injected`() {
        val injected = HardwareCodec.decide(transcode, true, true)
        assertEquals(true, injected.injected)
        assertTrue(injected.args.contains("-hwaccel"))
        assertTrue(injected.reason.isNotBlank())

        val off = HardwareCodec.decide(transcode, false, true)
        assertEquals(false, off.injected)
        assertEquals(transcode, off.args)
        assertTrue(off.reason.isNotBlank())
    }
}
