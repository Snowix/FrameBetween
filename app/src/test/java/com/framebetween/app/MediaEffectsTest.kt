package com.framebetween.app

import com.framebetween.app.core.Commands
import com.framebetween.app.core.RemoveMode
import com.framebetween.app.core.RemoveWatermarkConfig
import com.framebetween.app.core.WatermarkConfig
import com.framebetween.app.core.WatermarkMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaEffectsTest {

    @Test
    fun `speed scales video and audio by the same factor`() {
        val args = Commands.speed("/data/in.mp4", "/data/out.mp4", 2f, 2000, true)
        assertEquals("setpts=PTS/2.0000", args[args.indexOf("-vf") + 1])
        assertEquals("atempo=2.0000", args[args.indexOf("-af") + 1])
        assertTrue(args.contains("2000k"))
        assertTrue(args.contains("libx265"))
        assertEquals("/data/out.mp4", args.last())
    }

    @Test
    fun `speed beyond 2x chains two atempo stages`() {
        val args = Commands.speed("/data/in.mp4", "/data/out.mp4", 4f)
        assertEquals("atempo=2.0000,atempo=2.0000", args[args.indexOf("-af") + 1])
        val slow = Commands.speed("/data/in.mp4", "/data/out.mp4", 0.25f)
        assertTrue(slow[slow.indexOf("-af") + 1].startsWith("atempo=0.5000"))
    }

    @Test
    fun `speed clamps to range and skips audio filter when there is no audio`() {
        val args = Commands.speed("/data/in.mp4", "/data/out.mp4", 10f, 0, false)
        assertEquals("setpts=PTS/4.0000", args[args.indexOf("-vf") + 1])
        assertFalse(args.contains("-af"))
        val floor = Commands.speed("/data/in.mp4", "/data/out.mp4", 0.01f, 0, false)
        assertEquals("setpts=PTS/0.2500", floor[floor.indexOf("-vf") + 1])
    }

    @Test
    fun `unit speed needs no filters`() {
        val args = Commands.speed("/data/in.mp4", "/data/out.mp4", 1f)
        assertFalse(args.contains("-vf"))
        assertFalse(args.contains("-af"))
    }

    @Test
    fun `subtitle extract embed and burn build distinct commands`() {
        val extract = Commands.extractSubtitle("/data/in.mkv", "/data/out.srt")
        assertTrue(extract.contains("0:s:0"))
        assertEquals("/data/out.srt", extract.last())

        val mp4 = Commands.embedSubtitle("/data/in.mp4", "/data/sub.srt", "/data/out.mp4", "mp4")
        assertTrue(mp4.contains("mov_text"))
        assertTrue(mp4.contains("copy"))
        assertTrue(mp4.contains("-movflags"))

        val mkv = Commands.embedSubtitle("/data/in.mkv", "/data/sub.srt", "/data/out.mkv", "mkv")
        assertTrue(mkv.contains("srt"))
        assertFalse(mkv.contains("mov_text"))

        // 字幕文件读不到时回落到 libass subtitles 兜底；正常文件走原生 drawtext
        val burn = Commands.burnSubtitles("/data/in.mp4", "/data/out.mp4", "/data/sub.srt", 2000)
        assertTrue(burn[burn.indexOf("-vf") + 1].startsWith("format=yuv420p,subtitles="))
        assertTrue(burn.contains("2000k"))
    }

    @Test
    fun `dynamic watermark expressions vary over time`() {
        val cfg = WatermarkConfig("/data/wm.png", "br", 24)
        val slide = Commands.watermarkDynamic("/data/in.mp4", "/data/out.mp4", cfg, WatermarkMotion.SLIDE, 2000)
        val slideChain = slide[slide.indexOf("-filter_complex") + 1]
        assertTrue(slideChain.contains("t*60"))
        assertTrue(slideChain.contains("eval=frame"))

        val floating = Commands.watermarkDynamic("/data/in.mp4", "/data/out.mp4", cfg, WatermarkMotion.FLOAT)
        assertTrue(floating[floating.indexOf("-filter_complex") + 1].contains("sin(t*2)"))

        val blink = Commands.watermarkDynamic("/data/in.mp4", "/data/out.mp4", cfg, WatermarkMotion.BLINK)
        assertTrue(blink[blink.indexOf("-filter_complex") + 1].contains("enable="))

        val fixed = Commands.watermarkDynamic("/data/in.mp4", "/data/out.mp4", cfg, WatermarkMotion.STATIC)
        assertTrue(fixed[fixed.indexOf("-filter_complex") + 1].contains("x=W-w-24"))
    }

    @Test
    fun `remove watermark supports blur mosaic and delogo`() {
        val blur = Commands.removeWatermark(
            "/data/in.mp4", "/data/out.mp4",
            RemoveWatermarkConfig(10, 20, 100, 50, RemoveMode.BLUR, 8)
        )
        val blurChain = blur[blur.indexOf("-filter_complex") + 1]
        assertTrue(blurChain.contains("boxblur=8:2"))
        assertTrue(blurChain.contains("crop=100:50:10:20"))
        assertTrue(blur.contains("[outv]"))
        assertTrue(blur.contains("0:a?"))

        val mosaic = Commands.removeWatermark(
            "/data/in.mp4", "/data/out.mp4",
            RemoveWatermarkConfig(10, 20, 100, 50, RemoveMode.MOSAIC)
        )
        assertTrue(mosaic[mosaic.indexOf("-filter_complex") + 1].contains("flags=neighbor"))

        val inpaint = Commands.removeWatermark(
            "/data/in.mp4", "/data/out.mp4",
            RemoveWatermarkConfig(10, 20, 100, 50, RemoveMode.INPAINT)
        )
        val inpaintChain = inpaint[inpaint.indexOf("-filter_complex") + 1]
        assertTrue(inpaintChain.startsWith("[0:v]format=yuv420p,delogo="))
        assertTrue(inpaintChain.contains("delogo=x=10:y=20:w=100:h=50"))
    }

    @Test
    fun `remove watermark clamps invalid regions`() {
        val args = Commands.removeWatermark(
            "/data/in.mp4", "/data/out.mp4",
            RemoveWatermarkConfig(-10, -10, 0, 0, RemoveMode.BLUR, 999)
        )
        val chain = args[args.indexOf("-filter_complex") + 1]
        // delogo 需要四周参考带：x/y 被抬到 2，宽高夹到 2
        assertTrue(chain.contains("crop=2:2:2:2"))
        assertTrue(chain.startsWith("[0:v]format=yuv420p,split"))
        assertTrue(chain.contains("boxblur=30:2"))
    }

    @Test
    fun `remove watermark evenizes odd delogo geometry`() {
        val args = Commands.removeWatermark(
            "/data/in.mp4", "/data/out.mp4",
            RemoveWatermarkConfig(11, 21, 101, 51, RemoveMode.INPAINT)
        )
        val chain = args[args.indexOf("-filter_complex") + 1]
        assertTrue(chain.contains("delogo=x=10:y=20:w=100:h=50"))
    }

    @Test
    fun `remove watermark clamps region inside frame with a two pixel reference band`() {
        val frameW = 320
        val frameH = 240
        val args = Commands.removeWatermark(
            "/data/in.mp4", "/data/out.mp4",
            RemoveWatermarkConfig(5000, 5000, 4000, 4000, RemoveMode.INPAINT),
            videoWidth = frameW, videoHeight = frameH
        )
        val chain = args[args.indexOf("-filter_complex") + 1]
        val match = Regex("""delogo=x=(\d+):y=(\d+):w=(\d+):h=(\d+)""").find(chain)!!
        val (x, y, rw, rh) = match.destructured.toList().map { it.toInt() }
        assertTrue(x >= 2 && y >= 2)
        assertTrue(rw >= 2 && rh >= 2)
        assertTrue(x + rw <= frameW - 2)
        assertTrue(y + rh <= frameH - 2)
    }

    @Test
    fun `remove watermark disables autorotate and prefixes delogo with yuv420p`() {
        val args = Commands.removeWatermark(
            "/data/in.mp4", "/data/out.mp4",
            RemoveWatermarkConfig(10, 20, 100, 50, RemoveMode.INPAINT)
        )
        assertTrue(args.contains("-noautorotate"))
        val chain = args[args.indexOf("-filter_complex") + 1]
        assertTrue(chain.startsWith("[0:v]format=yuv420p,delogo="))
        // FFmpeg 9.0.2 已移除 delogo 的 band 选项，带上会直接报 Option not found
        assertTrue(!chain.contains("band="))
    }
}
