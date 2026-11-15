package com.framebetween.app

import com.framebetween.app.core.Commands
import com.framebetween.app.core.FrameRange
import com.framebetween.app.core.FrameRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRangesTest {

    @Test
    fun `parses comma and newline separated ranges`() {
        assertEquals(
            listOf(FrameRange(10, 20), FrameRange(50, 60)),
            FrameRanges.parse("10-20, 50-60")
        )
        assertEquals(
            listOf(FrameRange(3, 9), FrameRange(100, 120)),
            FrameRanges.parse("3~9\n100:120")
        )
    }

    @Test
    fun `invalid segments are ignored`() {
        assertEquals(listOf(FrameRange(5, 8)), FrameRanges.parse("20-10, abc, 5-8, , 7"))
        assertTrue(FrameRanges.parse("").isEmpty())
        assertTrue(FrameRanges.parse("not a range").isEmpty())
    }

    @Test
    fun `overlapping and adjacent ranges merge`() {
        assertEquals(
            listOf(FrameRange(0, 25)),
            FrameRanges.normalize(listOf(FrameRange(10, 20), FrameRange(0, 15), FrameRange(21, 25)))
        )
        assertEquals(11L, FrameRanges.removedFrames(listOf(FrameRange(10, 20))))
    }

    @Test
    fun `select expression covers every range`() {
        assertEquals(
            "not(between(n,10,20)+between(n,50,60))",
            FrameRanges.selectExpression(listOf(FrameRange(10, 20), FrameRange(50, 60)))
        )
        assertTrue(FrameRanges.selectExpression(emptyList()).isEmpty())
        assertEquals("10–20 · 50–60", FrameRanges.label(listOf(FrameRange(10, 20), FrameRange(50, 60))))
    }

    @Test
    fun `delete frames builds select plus setpts command`() {
        val args = Commands.deleteFrames(
            "/data/in.mp4", "/data/out.mp4",
            listOf(FrameRange(10, 20), FrameRange(50, 60)), 2500
        )
        val vf = args[args.indexOf("-vf") + 1]
        assertEquals(
            "select='not(between(n,10,20)+between(n,50,60))',setpts=N/FRAME_RATE/TB",
            vf
        )
        assertTrue(args.contains("libx265"))
        assertTrue(args.contains("2500k"))
        assertTrue(args.contains("-shortest"))
        assertTrue(args.contains("yuv420p"))
        assertEquals("/data/out.mp4", args.last())
    }

    @Test
    fun `empty ranges produce no command`() {
        assertTrue(Commands.deleteFrames("/data/in.mp4", "/data/out.mp4", emptyList()).isEmpty())
    }

    @Test
    fun `preview quotes the whole filter chain as one argument`() {
        val args = Commands.deleteFrames("/data/in.mp4", "/data/out.mp4", listOf(FrameRange(1, 2)))
        val preview = Commands.preview(args)
        assertTrue(preview.contains("\"select='not(between(n,1,2))',setpts=N/FRAME_RATE/TB\""))
    }
}
