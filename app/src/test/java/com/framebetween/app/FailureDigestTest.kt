package com.framebetween.app

import com.framebetween.app.core.FailureDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureDigestTest {

    @Test
    fun `always takes the last three meaningful lines`() {
        val lines = listOf(
            "$ ffmpeg -y -i a.mp4 out.mp4",
            "ffmpeg version 9.0.2 Copyright (c) 2000-2025",
            "configuration: --enable-gpl --enable-libx265",
            "libavutil      59.  8.100 / 59.  8.100",
            "built with clang",
            "Input #0, mov,mp4,m4a,3gp,3g2,mj2",
            "Stream mapping:",
            "frame=  120 fps= 30 q=28.0 size=   256kB",
            "[h264 @ 0x123] Invalid NAL unit size",
            "Error while decoding stream #0:0",
            "Conversion failed!"
        )
        assertEquals(
            "[h264 @ 0x123] Invalid NAL unit size\nError while decoding stream #0:0\nConversion failed!",
            FailureDigest.tail(lines)
        )
    }

    @Test
    fun `result is independent of callback timing`() {
        // 模拟日志异步分批到达：只要末尾三条错误都到了，结果就固定
        val partial = listOf("$ ffmpeg -i a.mp4", "banner", "Error A", "Error B", "Error C")
        val complete = listOf("$ ffmpeg -i a.mp4", "banner", "Error A", "Error B", "Error C", "frame= 1")
        assertEquals(FailureDigest.tail(partial), FailureDigest.tail(complete))
    }

    @Test
    fun `fewer than three lines returns what exists`() {
        assertEquals("Only one error", FailureDigest.tail(listOf("$ cmd", "Only one error")))
    }

    @Test
    fun `banner only input yields empty so caller can fall back to exit code`() {
        val banner = listOf(
            "ffmpeg version 9.0.2", "configuration: --enable-gpl",
            "libavcodec 60.3.100", "built with gcc", "Copyright (c) 2000-2025"
        )
        assertTrue(FailureDigest.tail(banner).isEmpty())
        assertTrue(FailureDigest.tail(emptyList()).isEmpty())
        assertTrue(FailureDigest.tail(listOf("$ cmd", "frame= 10")).isEmpty())
    }

    @Test
    fun `very long lines are truncated and custom limit respected`() {
        val long = "x".repeat(600)
        val result = FailureDigest.tail(listOf("first", "second", long), limit = 2)
        assertEquals(2, result.lines().size)
        assertTrue(result.lines().last().length <= 241)
        assertTrue(result.lines().last().endsWith("…"))
    }
}
