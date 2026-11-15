package com.framebetween.app

import com.framebetween.app.core.ViewMode
import com.framebetween.app.util.InsetsPolicy
import com.framebetween.app.util.MediaCover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 输入法避让、封面比例与「所有文件」视图枚举。 */
class InsetsAndCoverTest {

    @Test
    fun `keyboard hidden adds no padding`() {
        assertEquals(0, InsetsPolicy.contentBottomPadding(false, 800, 48))
    }

    @Test
    fun `oem merged ime into navigation bars still pads once`() {
        // OEM 把 IME(400) + 导航栏(48) 一起报进 insets，扣掉锁定的导航栏后只留 400
        assertEquals(400, InsetsPolicy.contentBottomPadding(true, 448, 48))
    }

    @Test
    fun `standard device never over-pads`() {
        assertEquals(352, InsetsPolicy.contentBottomPadding(true, 400, 48))
    }

    @Test
    fun `ime smaller than nav bar yields no white band`() {
        assertEquals(0, InsetsPolicy.contentBottomPadding(true, 20, 48))
    }

    @Test
    fun `audio and video share the same canvas`() {
        assertEquals(1.5f, MediaCover.aspectRatio(isVideo = true), 0.0001f)
        assertEquals(1.5f, MediaCover.aspectRatio(isVideo = false, isAudio = true), 0.0001f)
        assertTrue(MediaCover.isWideCanvas(isVideo = false, isAudio = true))
        assertTrue(MediaCover.isWideCanvas(isVideo = true, isAudio = false))
    }

    @Test
    fun `other file types keep the square canvas`() {
        assertEquals(1f, MediaCover.aspectRatio(false, false), 0.0001f)
        assertTrue(!MediaCover.isWideCanvas(false, false))
    }

    @Test
    fun `all files is no longer a media view mode`() {
        assertEquals(3, ViewMode.values().size)
        assertTrue(ViewMode.values().none { it.name == "ALL_FILES" })
    }
}
