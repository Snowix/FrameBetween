package com.framebetween.app

import com.framebetween.app.ui.common.NAV_TAP_GUARD_MS
import com.framebetween.app.ui.common.shouldGuardTap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTapGuardTest {

    @Test
    fun `taps right after a route change are swallowed`() {
        // 返回后立刻点击、以及守卫窗口内的点击，都应该被吞掉，不能落到正在退场的旧页面
        assertTrue(shouldGuardTap(0))
        assertTrue(shouldGuardTap(150))
        assertTrue(shouldGuardTap(NAV_TAP_GUARD_MS))
    }

    @Test
    fun `taps after the guard window pass through`() {
        assertFalse(shouldGuardTap(NAV_TAP_GUARD_MS + 1))
        assertFalse(shouldGuardTap(700))
        assertFalse(shouldGuardTap(-1))
    }

    @Test
    fun `guard window honours a custom duration`() {
        assertTrue(shouldGuardTap(80, 100))
        assertTrue(shouldGuardTap(100, 100))
        assertFalse(shouldGuardTap(101, 100))
    }
}
