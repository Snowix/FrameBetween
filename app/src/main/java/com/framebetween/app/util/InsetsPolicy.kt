package com.framebetween.app.util

/**
 * 输入法避让策略（纯 Kotlin，便于 JVM 单测）。
 *
 * 部分厂商在键盘可见时会把 IME 高度并入 navigationBars insets。如果 Scaffold 仍用默认的
 * contentWindowInsets，再叠加一次 imePadding，内容区会被抬起接近两个键盘高度，
 * 光标与输入法之间就露出一大块 windowBackground 白色横幅（反馈约 400px）。
 *
 * 因此 Scaffold 显式关闭 contentWindowInsets，内容区只按本函数的结果留白一次；
 * 扣掉锁定的系统导航栏高度后宁可略少抬升，也绝不多留白色空隙。
 */
object InsetsPolicy {
    fun contentBottomPadding(
        imeVisible: Boolean,
        imeBottomPx: Int,
        stableNavBottomPx: Int
    ): Int = if (!imeVisible) {
        0
    } else {
        (imeBottomPx - stableNavBottomPx).coerceAtLeast(0)
    }
}
