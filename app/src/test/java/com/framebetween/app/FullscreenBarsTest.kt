package com.framebetween.app

import android.view.View
import android.view.WindowManager
import com.framebetween.app.ui.common.fullscreenFlagChanges
import com.framebetween.app.ui.common.immersiveSystemUiFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 全屏系统栏的确定性规则：flags 组合不依赖运行时，可在 JVM 单测里覆盖。 */
@Suppress("DEPRECATION")
class FullscreenBarsTest {

    @Test
    fun `fullscreen adds immersive flags and clears force-not-fullscreen`() {
        val changes = fullscreenFlagChanges(true)
        assertTrue(changes.add and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0)
        assertTrue(changes.add and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
        assertTrue(changes.clear and WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN != 0)
        assertEquals(0, changes.add and WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
    }

    @Test
    fun `leaving fullscreen removes immersive flags`() {
        val changes = fullscreenFlagChanges(false)
        assertEquals(0, changes.add)
        assertTrue(changes.clear and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0)
        assertTrue(changes.clear and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `immersive flags cover status bar navigation bar and sticky mode`() {
        val flags = immersiveSystemUiFlags()
        assertTrue(flags and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY != 0)
        assertTrue(flags and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
        assertTrue(flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
        assertTrue(flags and View.SYSTEM_UI_FLAG_LAYOUT_STABLE != 0)
        assertTrue(flags and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN != 0)
        assertTrue(flags and View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION != 0)
    }
}
