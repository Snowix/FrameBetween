package com.framebetween.app

import com.framebetween.app.core.Codecs
import com.framebetween.app.core.CommandTokenizer
import com.framebetween.app.core.Commands
import com.framebetween.app.core.ConvertConfig
import com.framebetween.app.core.CropConfig
import com.framebetween.app.core.GifConfig
import com.framebetween.app.core.Estimate
import com.framebetween.app.core.ExtractConfig
import com.framebetween.app.core.MediaInfo
import com.framebetween.app.core.MergeConfig
import com.framebetween.app.core.TrimConfig
import com.framebetween.app.core.TrimMode
import com.framebetween.app.core.VideoStream
import com.framebetween.app.core.AudioStream
import com.framebetween.app.core.FlipOp
import com.framebetween.app.util.even
import com.framebetween.app.util.frameToMs
import com.framebetween.app.util.msToFrame
import com.framebetween.app.util.secondsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandsTest {

    private val input = "/data/input.mp4"
    private val output = "/data/output.mp4"

    private val media = MediaInfo(
        path = input,
        displayName = "input.mp4",
        sizeBytes = 20_000_000L,
        durationMs = 60_000L,
        video = VideoStream(codec = "h264", width = 1920, height = 1080, fps = 30.0, bitrateKbps = 2500),
        audio = AudioStream(codec = "aac", bitrateKbps = 128)
    )

    @Test
    fun `bitrate estimation matches kbps times duration`() {
        assertEquals(19_710_000L, Estimate.targetBytes(2500, 128, 60_000L))
        assertEquals(0L, Estimate.targetBytes(2500, 128, 0L))
        assertEquals(19_710_000L, Estimate.convertBytes(ConvertConfig(), media))
    }

    @Test
    fun `convert builds h265 and mp3 commands`() {
        val h265 = Commands.convert(
            input, output,
            ConvertConfig(videoCodec = "libx265", crf = 28, preset = "fast"),
            hasVideo = true, hasAudio = true
        )
        assertTrue(h265.contains("libx265"))
        assertTrue(h265.contains("28"))
        assertTrue(h265.contains("-crf"))
        assertFalse(h265.contains("-b:v"))
        assertEquals(output, h265.last())

        val mp3 = Commands.convert(
            input, "/data/out.mp3",
            Commands.defaultCodecs("mp3").let { (v, a) -> ConvertConfig(container = "mp3", videoCodec = v, audioCodec = a) },
            hasVideo = true, hasAudio = true
        )
        assertTrue(mp3.contains("-vn"))
        assertTrue(mp3.contains("libmp3lame"))
        assertFalse(mp3.contains("-c:v"))
    }

    @Test
    fun `convert scales to even height and keeps faststart for mp4`() {
        val args = Commands.convert(
            input, output,
            ConvertConfig(targetHeight = 721, videoCodec = "libx264"),
            hasVideo = true, hasAudio = true
        )
        assertTrue(args.contains("scale=-2:720"))
        assertTrue(args.contains("+faststart"))
        assertTrue(args.contains("yuv420p"))
    }

    @Test
    fun `trim modes place ss correctly and keep source bitrate`() {
        val copy = Commands.trim(input, output, TrimConfig(5_000, 15_000, TrimMode.COPY))
        assertTrue(copy.indexOf("-ss") < copy.indexOf("-i"))
        assertTrue(copy.contains("copy"))
        assertTrue(copy.contains("10.0"))

        val fast = Commands.trim(input, output, TrimConfig(5_000, 15_000, TrimMode.FAST_ENCODE), 2500)
        assertTrue(fast.contains("2500k"))
        assertTrue(fast.contains("-maxrate"))
        assertTrue(fast.contains("-bufsize"))
        assertFalse(fast.contains("-crf"))
        assertTrue(fast.contains("-c:a"))
        assertTrue(fast[fast.indexOf("-c:a") + 1] == "copy")

        val precise = Commands.trim(input, output, TrimConfig(5_000, 15_000, TrimMode.PRECISE), 2500)
        assertTrue(precise.indexOf("-i") < precise.indexOf("-ss"))
        assertTrue(precise.contains("2500k"))
        assertFalse(precise.contains("-crf"))
    }

    @Test
    fun `crop transform and merge keep source bitrate`() {
        val crop = Commands.crop(input, output, CropConfig(11, 9, 641, 483), 2500)
        assertTrue(crop.contains("2500k"))
        assertFalse(crop.contains("-crf"))

        val flip = Commands.transform(input, output, FlipOp.HFLIP, false, 2500)
        assertTrue(flip.contains("2500k"))
        assertFalse(flip.contains("-crf"))

        val merge = Commands.merge("/tmp/list.txt", output, MergeConfig(true), 2500)
        assertTrue(merge.contains("2500k"))
        assertFalse(merge.contains("-crf"))
    }

    @Test
    fun `gif uses palette and time range`() {
        val args = Commands.gif(input, "/data/out.gif", GifConfig(2_000, 7_000, 480, 12, 0))
        assertTrue(args.contains("-filter_complex"))
        val filter = args[args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("palettegen"))
        assertTrue(filter.contains("paletteuse"))
        assertTrue(filter.contains("fps=12"))
        assertTrue(filter.contains("scale=480:-1"))
        assertTrue(args.contains("5.0"))
        assertTrue(args.contains("-loop"))
        assertEquals("/data/out.gif", args.last())
        assertEquals(2500L, media.videoKbps)
        assertTrue(Estimate.gifBytes(480, 12, 5_000) > 0)
    }

    @Test
    fun `crop filter uses even values`() {
        val args = Commands.crop(input, output, CropConfig(11, 9, 641, 483))
        val filter = args[args.indexOf("-vf") + 1]
        assertEquals("crop=640:482:10:8", filter)
        assertTrue(args.contains("-noautorotate"))
    }

    @Test
    fun `concat list and merge modes`() {
        val list = Commands.concatList(listOf("/a/one.mp4", "/b/two.mp4"))
        assertTrue(list.contains("file '/a/one.mp4'"))
        assertTrue(list.endsWith("\n"))

        val copy = Commands.merge("/tmp/list.txt", output, MergeConfig(false))
        assertTrue(copy.contains("concat"))
        assertTrue(copy.contains("copy"))
        val reencode = Commands.merge("/tmp/list.txt", output, MergeConfig(true))
        assertTrue(reencode.contains(Codecs.DEFAULT_VIDEO))
        assertTrue(reencode.contains("libx265"))
        assertTrue(reencode.contains("-crf"))
    }

    @Test
    fun `transform metadata rotation does not reencode`() {
        val metadata = Commands.transform(input, output, FlipOp.ROT90, metadataOnly = true)
        assertTrue(metadata.contains("rotate=90"))
        assertFalse(metadata.contains("-vf"))
        val pixels = Commands.transform(input, output, FlipOp.ROT90, metadataOnly = false)
        assertTrue(pixels.contains("transpose=1"))
        assertTrue(pixels.contains("-c:v"))
    }

    @Test
    fun `mute and audio extraction`() {
        val mute = Commands.mute(input, output)
        assertTrue(mute.contains("-an"))
        assertTrue(mute.contains("-c:v"))

        val mp3 = Commands.extractAudio(input, "/data/out.mp3", ExtractConfig("mp3", 192))
        assertTrue(mp3.contains("libmp3lame"))
        assertTrue(mp3.contains("192k"))
        val flac = Commands.extractAudio(input, "/data/out.flac", ExtractConfig("flac", 192))
        assertTrue(flac.contains("flac"))
        assertFalse(flac.contains("-b:a"))
        val segment = Commands.extractAudio(input, "/data/out.mp3", ExtractConfig("mp3", 128, 2_000, 7_000))
        assertTrue(segment.contains("-ss"))
        assertTrue(segment.contains("-t"))
        assertTrue(segment.contains("5.0"))
    }

    @Test
    fun `tokenizer handles quotes and shell meta`() {
        val tokens = CommandTokenizer.parse("-i \"/sdcard/my video.mp4\" -vf 'hflip,scale=720:-2' out.mp4")
        assertEquals(listOf("-i", "/sdcard/my video.mp4", "-vf", "hflip,scale=720:-2", "out.mp4"), tokens)
        assertTrue(CommandTokenizer.containsShellMeta("-i a.mp4 | cat"))
        assertFalse(CommandTokenizer.containsShellMeta("-i a.mp4 -c copy b.mp4"))
        assertEquals(listOf("-i", "a.mp4"), CommandTokenizer.stripBinaryName(listOf("ffmpeg", "-i", "a.mp4")))
    }

    @Test
    fun `time helpers`() {
        assertEquals(10, even(11))
        assertEquals(30L, msToFrame(1_000L, 30.0))
        assertEquals(1_000L, frameToMs(30L, 30.0))
        assertEquals("1.5", secondsString(1_500L))
    }
}
