package com.framebetween.app

import com.framebetween.app.core.PlaybackCompat
import com.framebetween.app.util.mimeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCompatTest {

    @Test
    fun `uncommon containers are still recognized for diagnostics`() {
        listOf("wmv", "asf", "avi", "rmvb", "flv", "vob", "mpg", "mpeg", "m2ts", "divx", "f4v")
            .forEach { ext ->
                assertTrue(
                    "$ext 应被标记为非常见容器",
                    PlaybackCompat.isUncommonContainer("clip.$ext")
                )
            }
    }

    @Test
    fun `native exoplayer containers are not flagged`() {
        listOf("mp4", "mkv", "mov", "webm", "m4v", "mp3", "flac", "wav")
            .forEach { ext ->
                assertFalse(
                    "$ext 是常见容器",
                    PlaybackCompat.isUncommonContainer("clip.$ext")
                )
            }
    }

    @Test
    fun `playback layer no longer exposes a pre-transcoding entry`() {
        // 播放直接打开源文件：锁死「不再有转码入口」这一约定，
        // 避免预览播放引入又慢又占存储的转码流程。
        val names = PlaybackCompat::class.java.declaredMethods.map { it.name }
        assertFalse("ensurePlayable 应已移除", "ensurePlayable" in names)
        assertFalse("convert 应已移除", "convert" in names)
        assertTrue("应保留零 IO 的播放源归一化", "playbackUri" in names)
        assertTrue("应保留历史缓存清理", "clearLegacyCache" in names)
    }

    @Test
    fun `new common extensions are recognized as media`() {
        assertEquals("video/x-ms-wmv", mimeOf("a.WMV"))
        assertEquals("video/mpeg", mimeOf("a.m2v"))
        assertEquals("video/divx", mimeOf("a.divx"))
        assertEquals("audio/x-wav", mimeOf("a.wv"))
        assertEquals("audio/mpeg", mimeOf("a.mp2"))
    }

    @Test
    fun `audio only containers are still recognized`() {
        assertTrue(PlaybackCompat.isAudioOnly("song.wma"))
        assertFalse(PlaybackCompat.isAudioOnly("movie.wmv"))
    }
}
