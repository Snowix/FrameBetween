package com.framebetween.app

import com.framebetween.app.core.AppLog
import com.framebetween.app.core.LogLevel
import com.framebetween.app.core.PlaybackCompat
import com.framebetween.app.ui.common.RESIZE_AUTO_FILL
import com.framebetween.app.ui.common.RESIZE_FIT
import com.framebetween.app.ui.common.RESIZE_ZOOM
import com.framebetween.app.ui.common.adaptiveResizeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAdaptiveTest {

    @Test
    fun `fullscreen fits screen without cropping when ratios differ`() {
        // 16:9 视频进入接近 4:3 的全屏画布，必须保持比例完整显示
        assertEquals(RESIZE_FIT, adaptiveResizeMode(1920, 1080, 1000, 800))
        // 竖屏视频进入横屏全屏同样不能裁剪
        assertEquals(RESIZE_FIT, adaptiveResizeMode(1080, 1920, 1920, 1080))
        // 尺寸未知时安全退回 FIT
        assertEquals(RESIZE_FIT, adaptiveResizeMode(0, 0, 1080, 1920))
        assertEquals(RESIZE_FIT, adaptiveResizeMode(1920, 1080, 0, 0))
    }

    @Test
    fun `fullscreen fills screen only when ratios almost match`() {
        assertEquals(RESIZE_AUTO_FILL, adaptiveResizeMode(1920, 1080, 1920, 1080))
        assertEquals(RESIZE_AUTO_FILL, adaptiveResizeMode(852, 480, 1065, 600))
        // 3.7% 偏差超过 2% 阈值，必须 FIT，不能再拉伸 / 裁剪
        assertEquals(RESIZE_FIT, adaptiveResizeMode(1920, 1080, 1920, 1120))
        // 2772×1272 与 2340×1080 相差约 0.6%，仍可铺满
        assertEquals(RESIZE_AUTO_FILL, adaptiveResizeMode(2772, 1272, 2340, 1080))
    }

    @Test
    fun `zoom keeps aspect ratio instead of stretching fill`() {
        // RESIZE_MODE_FILL 会直接拉伸画面，是 2772×1272 上比例失真的根因
        assertEquals(RESIZE_ZOOM, RESIZE_AUTO_FILL)
    }

    @Test
    fun `rotation and sample aspect ratio are applied before comparing`() {
        assertEquals(RESIZE_AUTO_FILL, adaptiveResizeMode(2772, 1272, 2772, 1272))
        // rotate=90 的录屏编码尺寸是横的，实际显示却是竖的
        assertEquals(RESIZE_FIT, adaptiveResizeMode(2772, 1272, 2772, 1272, 1f, 90))
        assertEquals(
            RESIZE_AUTO_FILL,
            adaptiveResizeMode(2772, 1272, 1272, 2772, 1f, 90)
        )
        // SAR=2 的非方形像素必须按显示宽度计算
        assertEquals(RESIZE_AUTO_FILL, adaptiveResizeMode(720, 480, 1440, 480, 2f))
        assertEquals(RESIZE_FIT, adaptiveResizeMode(720, 480, 1440, 480, 1f))
    }

    @Test
    fun `device without hevc decoder transcodes proactively`() {
        // test.mp4：HEVC Main 8-bit + AAC，设备没有 video/hevc 硬解
        assertTrue(
            PlaybackCompat.shouldTranscode("hevc", "yuv420p", "aac", setOf("video/avc"))
        )
        // 设备有 HEVC 解码器时无需转码，仍由播放器兜底保障
        assertFalse(
            PlaybackCompat.shouldTranscode(
                "hevc", "yuv420p", "aac",
                setOf("video/avc", "video/hevc")
            )
        )
    }

    @Test
    fun `high bit depth and unsupported audio always transcode`() {
        assertTrue(
            PlaybackCompat.shouldTranscode(
                "hevc", "yuv420p10le", "aac",
                setOf("video/hevc", "audio/mp4a-latm")
            )
        )
        assertTrue(
            PlaybackCompat.shouldTranscode(
                "h264", "yuv420p", "ac3", setOf("video/avc")
            )
        )
        assertFalse(
            PlaybackCompat.shouldTranscode(
                "h264", "yuv420p", "aac", setOf("video/avc")
            )
        )
        assertTrue(
            PlaybackCompat.shouldTranscode(
                "prores", "yuv422p10le", "pcm_s24le", setOf("video/avc")
            )
        )
    }

    @Test
    fun `codec to mime mapping`() {
        assertEquals("video/hevc", PlaybackCompat.videoMimeOf("hev1"))
        assertEquals("video/av01", PlaybackCompat.videoMimeOf("AV1"))
        assertEquals("audio/ac3", PlaybackCompat.audioMimeOf("AC3"))
        assertEquals(null, PlaybackCompat.videoMimeOf("h264"))
    }

    @Test
    fun `app log stays silent while disabled`() {
        AppLog.clear()
        AppLog.setEnabled(false)
        assertFalse(AppLog.isEnabled())
        AppLog.i("T", "hidden")
        assertTrue(AppLog.entries().isEmpty())
        AppLog.setEnabled(true)
        AppLog.i("T", "visible")
        assertEquals(1, AppLog.entries().size)
        AppLog.clear()
        AppLog.setEnabled(false)
    }

    @Test
    fun `app log filters by level and clears`() {
        AppLog.clear()
        AppLog.setEnabled(true)
        AppLog.v("T", "verbose")
        AppLog.d("T", "debug")
        AppLog.i("T", "info")
        AppLog.w("T", "warn")
        AppLog.e("T", "error")
        assertEquals(5, AppLog.entries(LogLevel.VERBOSE).size)
        assertEquals(3, AppLog.entries(LogLevel.INFO).size)
        assertEquals(1, AppLog.entries(LogLevel.ERROR).size)
        assertTrue(AppLog.text(LogLevel.WARN).contains("warn"))
        assertFalse(AppLog.text(LogLevel.WARN).contains("debug"))
        AppLog.clear()
        assertTrue(AppLog.entries(LogLevel.VERBOSE).isEmpty())
        assertTrue(AppLog.text(LogLevel.VERBOSE).contains("no log entries"))
        AppLog.setEnabled(false)
    }
}
