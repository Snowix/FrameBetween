package com.framebetween.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * 导航后的点击守卫。
 *
 * navigation-compose 2.8.x 的 NavHost 默认带 700ms 的 fadeIn / fadeOut：
 * `popBackStack()` 之后，正在淡出的旧页面仍留在组合里并且**继续接收点击**，
 * 用户返回后马上点某个位置，命中的其实是旧页面上同一坐标的按钮——这正是
 * 「返回后快速点击仍点到原来页面选项」的根因。AppRoot 已把四个转场全部设为
 * None，这里再补一层短守卫，吞掉路由切换后第一时间的残留触摸（含 Dialog
 * 关闭后的透传点击），避免一帧的窗口期漏过去。
 */
const val NAV_TAP_GUARD_MS = 300L

/** 纯函数：距上次路由变化在守卫窗口内才吞掉触摸，便于 JVM 单测覆盖。 */
fun shouldGuardTap(elapsedMs: Long, guardMs: Long = NAV_TAP_GUARD_MS): Boolean =
    elapsedMs in 0..guardMs

@Composable
fun NavigationTapGuard(route: String?, content: @Composable () -> Unit) {
    var guarding by remember { mutableStateOf(false) }
    LaunchedEffect(route) {
        if (route == null) {
            guarding = false
            return@LaunchedEffect
        }
        guarding = true
        delay(NAV_TAP_GUARD_MS)
        guarding = false
    }
    Box(Modifier.fillMaxSize()) {
        content()
        if (guarding) {
            // 命中最上层后 Compose 默认不再把事件分享给同级兄弟节点；
            // 这里再在 Initial pass 全部 consume()，双重保证旧页面拿不到 down。
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            )
        }
    }
}
