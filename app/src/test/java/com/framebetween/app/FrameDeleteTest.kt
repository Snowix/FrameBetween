package com.framebetween.app

import com.framebetween.app.core.Commands
import com.framebetween.app.core.FrameDelete
import com.framebetween.app.core.FrameDeleteMode
import com.framebetween.app.core.FrameRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameDeleteTest {

    @Test
    fun `audio select uses the same time windows as video`() {
        // 25fps：帧 10..20 对应 0.4000s ~ 0.8400s（闭区间尾部再补一帧）
        assertEquals(
            "not(between(t,0.4000,0.8400))",
            FrameDelete.audioSelectExpression(listOf(FrameRange(10, 20)), 25.0)
        )
        assertEquals("", FrameDelete.audioSelectExpression(listOf(FrameRange(10, 20)), 0.0))
        assertTrue(
            FrameDelete.audioSelectExpression(listOf(FrameRange(10, 20), FrameRange(50, 60)), 25.0)
                .contains("+between(t,")
        )
    }

    @Test
    fun `recode mode applies aselect and asetpts so audio is deleted too`() {
        val args = Commands.deleteFrames(
            "/data/in.mp4", "/data/out.mp4",
            listOf(FrameRange(10, 20)), 2000, 25.0, FrameDeleteMode.RECODE
        )
        val af = args[args.indexOf("-af") + 1]
        assertTrue(af.startsWith("aselect='not(between(t,"))
        assertTrue(af.endsWith("',asetpts=N/SR/TB"))
        assertTrue(args.contains("aac"))
        assertTrue(args.contains("libx265"))
        assertTrue(args.contains("-shortest"))
    }

    @Test
    fun `recode mode still works when fps is unknown`() {
        val args = Commands.deleteFrames(
            "/data/in.mp4", "/data/out.mp4", listOf(FrameRange(1, 2))
        )
        assertTrue(args.contains("-vf"))
        assertFalse(args.contains("-af"))
    }

    @Test
    fun `keep ranges are the complement of deleted ranges`() {
        assertEquals(
            listOf(FrameRange(0, 9), FrameRange(21, 49), FrameRange(61, 99)),
            FrameDelete.keepRanges(
                listOf(FrameRange(10, 20), FrameRange(50, 60)), 25.0, 100
            )
        )
        assertTrue(FrameDelete.keepRanges(emptyList(), 25.0, 100).isEmpty())
        assertEquals(
            listOf(FrameRange(0, 9)),
            FrameDelete.keepRanges(listOf(FrameRange(10, 99)), 25.0, 100)
        )
    }

    @Test
    fun `concat list escapes quotes and writes inpoint outpoint`() {
        val text = FrameDelete.concatList(
            "/data/a'b.mp4", listOf(FrameRange(10, 20)), 25.0, 50
        )
        assertTrue(text.startsWith("ffconcat version 1.0"))
        assertTrue(text.contains("file '/data/a'\\''b.mp4'"))
        assertTrue(text.contains("inpoint 0.0000"))
        assertTrue(text.contains("outpoint 0.4000"))
        assertTrue(text.contains("inpoint 0.8400"))
        assertEquals(2, text.lines().count { it.startsWith("file ") })
    }

    @Test
    fun `stream copy command never decodes or re-encodes`() {
        val args = Commands.deleteFrames(
            "/data/in.mp4", "/data/out.mp4", listOf(FrameRange(10, 20)),
            fps = 25.0,
            mode = FrameDeleteMode.STREAM_COPY,
            concatListPath = "/tmp/keep.txt",
            totalFrames = 100
        )
        assertTrue(args.contains("concat"))
        assertTrue(args.contains("copy"))
        assertFalse(args.contains("libx265"))
        assertFalse(args.contains("-vf"))
        assertFalse(args.contains("-af"))
        assertEquals("/data/out.mp4", args.last())
    }

    @Test
    fun `stream copy refuses to run without the required metadata`() {
        val base = listOf(FrameRange(1, 2))
        assertTrue(
            Commands.deleteFrames(
                "/data/in.mp4", "/data/out.mp4", base,
                fps = 25.0, mode = FrameDeleteMode.STREAM_COPY,
                concatListPath = "", totalFrames = 100
            ).isEmpty()
        )
        assertTrue(
            Commands.deleteFrames(
                "/data/in.mp4", "/data/out.mp4", base,
                fps = 0.0, mode = FrameDeleteMode.STREAM_COPY,
                concatListPath = "/tmp/keep.txt", totalFrames = 100
            ).isEmpty()
        )
        assertTrue(
            Commands.deleteFrames(
                "/data/in.mp4", "/data/out.mp4", base,
                fps = 25.0, mode = FrameDeleteMode.STREAM_COPY,
                concatListPath = "/tmp/keep.txt", totalFrames = 0
            ).isEmpty()
        )
    }
}
