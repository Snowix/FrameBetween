package com.framebetween.app

import com.framebetween.app.core.ProgressLog
import com.framebetween.app.core.ProgressTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTrackerTest {

    @Test
    fun `parses clock lines and out_time_ms`() {
        assertEquals(83_450L, ProgressLog.timeMs("frame= 10 time=00:01:23.45 bitrate=1000kbits/s"))
        assertEquals(5_000L, ProgressLog.timeMs("out_time_ms=5000000"))
        assertEquals(3_661_000L, ProgressLog.durationMs("Duration: 01:01:01.00, start: 0.000000"))
        assertNull(ProgressLog.timeMs("configuration: --enable-gpl --enable-libx265"))
        assertNull(ProgressLog.durationMs("Stream #0:0: Video: h264"))
    }

    @Test
    fun `progress advances with statistics and never goes backwards`() {
        val tracker = ProgressTracker(10_000)
        assertEquals(0f, tracker.fraction, 0f)
        assertEquals(0f, tracker.onTime(2_000), 0.0001f)
        val middle = tracker.onTime(5_000)
        assertEquals(0.3f, middle, 0.0001f)
        assertTrue(tracker.onTime(4_000) >= middle)
        assertTrue(tracker.onTime(9_000) <= 0.99f)
        assertEquals(1f, tracker.finish(), 0f)
        assertFalse(tracker.estimated)
    }

    @Test
    fun `duration hint from banner fills the denominator`() {
        val tracker = ProgressTracker(0)
        tracker.onDurationHint(10_000)
        tracker.onTime(0)
        assertEquals(0.5f, tracker.onTime(5_000), 0.0001f)
    }

    @Test
    fun `soft tick keeps the bar moving when no signal arrives`() {
        val tracker = ProgressTracker(0)
        val first = tracker.onTick(1_000)
        val second = tracker.onTick(5_000)
        assertTrue(first > 0f)
        assertTrue(second > first)
        assertTrue(tracker.estimated)
        assertTrue(second <= 0.90f)
        // 有真实时间信号后取消「估算」标记，且进度不回退
        val real = tracker.onTime(30_000)
        assertFalse(tracker.estimated)
        assertTrue(real >= second)
    }

    @Test
    fun `soft tick stops once a real signal is present`() {
        val tracker = ProgressTracker(10_000)
        tracker.onTime(0)
        val fromSignal = tracker.onTime(3_000)
        assertEquals(fromSignal, tracker.onTick(20_000), 0f)
        assertFalse(tracker.estimated)
    }

    @Test
    fun `no denominator still yields a bounded smooth estimate`() {
        val tracker = ProgressTracker(0)
        tracker.onTime(0)
        val value = tracker.onTime(60_000)
        assertTrue(value > 0f)
        assertTrue(value <= 0.99f)
    }
}
