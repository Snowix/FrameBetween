package com.framebetween.app.ui.common

import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 全屏播放时的系统栏控制：状态栏、导航栏 / 全面屏手势提示条、刘海区域。
 *
 * 只对 Compose Dialog 自己的 Window 调用一次 hide() 并不足够，原因有三：
 *  - Activity 的 Window 没有隐藏系统栏。全面屏手势导航与 Android 15 的强制边到边
 *    下，系统栏由宿主窗口绘制，只隐藏 Dialog 的面板窗口会被系统还原，状态栏图标
 *    与手势条会重新出现在画面上。
 *  - 横屏切换、窗口重新获得焦点、从屏幕边缘滑动（快进手势容易触发）都会让
 *    系统重新显示系统栏，一次性的 hide() 不具备恢复能力。
 *  - 挖孔屏未设置 layoutInDisplayCutoutMode，状态栏区域被系统保留。
 *
 * 现在的做法：Activity Window 与 Dialog Window 一起进入沉浸式，并在 Dialog 的
 * decorView 上监听 insets，系统栏一旦重新可见就在下一帧重新隐藏；退出全屏时按
 * 进入前的快照逐项恢复，不影响小窗播放器与应用其余页面。
 */
data class WindowFlagChanges(val add: Int, val clear: Int)

/**
 * 纯函数：进入 / 退出全屏时要对 Window 增删的 flags，便于单测覆盖。
 * FLAG_FULLSCREEN / FLAG_FORCE_NOT_FULLSCREEN 在新版已弃用，但部分厂商 ROM
 * 仍据此决定是否绘制状态栏，作为 WindowInsetsController 之外的兼容层保留。
 */
@Suppress("DEPRECATION")
fun fullscreenFlagChanges(fullscreen: Boolean): WindowFlagChanges = if (fullscreen) {
    WindowFlagChanges(
        add = WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        clear = WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN
    )
} else {
    WindowFlagChanges(
        add = 0,
        clear = WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    )
}

/** 纯函数：旧 API（API 26–29）沉浸式标志，必须包含 HIDE_NAVIGATION 才能藏住手势条。 */
@Suppress("DEPRECATION")
fun immersiveSystemUiFlags(): Int =
    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

private data class SystemBarsSnapshot(
    val statusBarColor: Int,
    val navigationBarColor: Int,
    val cutoutMode: Int,
    val contrastEnforced: Boolean,
    val systemUiVisibility: Int
)

@Suppress("DEPRECATION")
private fun Window.captureSystemBars(): SystemBarsSnapshot = SystemBarsSnapshot(
    statusBarColor = statusBarColor,
    navigationBarColor = navigationBarColor,
    cutoutMode = if (Build.VERSION.SDK_INT >= 28) {
        attributes.layoutInDisplayCutoutMode
    } else {
        0
    },
    contrastEnforced = if (Build.VERSION.SDK_INT >= 29) {
        isNavigationBarContrastEnforced
    } else {
        false
    },
    systemUiVisibility = decorView.systemUiVisibility
)

/** 单次应用沉浸式隐藏。可被守卫反复调用，因此必须保持幂等。 */
@Suppress("DEPRECATION")
private fun Window.applyFullscreenBars() {
    val changes = fullscreenFlagChanges(true)
    addFlags(changes.add)
    clearFlags(changes.clear)
    WindowCompat.setDecorFitsSystemWindows(this, false)
    val controller = WindowCompat.getInsetsController(this, decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
    decorView.systemUiVisibility = immersiveSystemUiFlags()
    statusBarColor = AndroidColor.TRANSPARENT
    navigationBarColor = AndroidColor.TRANSPARENT
    if (Build.VERSION.SDK_INT >= 28) {
        val lp = attributes
        lp.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        attributes = lp
    }
    if (Build.VERSION.SDK_INT >= 29) isNavigationBarContrastEnforced = false
}

@Suppress("DEPRECATION")
private fun Window.restoreSystemBars(snapshot: SystemBarsSnapshot) {
    val changes = fullscreenFlagChanges(false)
    addFlags(changes.add)
    clearFlags(changes.clear)
    WindowCompat.setDecorFitsSystemWindows(this, true)
    WindowCompat.getInsetsController(this, decorView)
        .show(WindowInsetsCompat.Type.systemBars())
    decorView.systemUiVisibility = snapshot.systemUiVisibility
    statusBarColor = snapshot.statusBarColor
    navigationBarColor = snapshot.navigationBarColor
    if (Build.VERSION.SDK_INT >= 28) {
        val lp = attributes
        lp.layoutInDisplayCutoutMode = snapshot.cutoutMode
        attributes = lp
    }
    if (Build.VERSION.SDK_INT >= 29) isNavigationBarContrastEnforced = snapshot.contrastEnforced
}

/**
 * 一次全屏会话：统一管理所有相关 Window 的进入与恢复。
 *
 * @param windows 需要隐藏系统栏的窗口（Activity Window + Dialog Window）
 * @param guardView 监听系统栏重新可见的 decorView；为空时只做延迟补隐藏
 */
internal class FullscreenBarsSession(
    private val windows: List<Window>,
    private val guardView: View? = null
) {
    private val snapshots = windows.associateWith { it.captureSystemBars() }
    private var removeGuard: (() -> Unit)? = null

    fun enter() {
        windows.forEach { it.applyFullscreenBars() }
        val decor = guardView
        // 窗口 attach 完成、横屏旋转结束后再补一次，避免过早 hide() 被系统忽略
        val rehide = Runnable {
            if (windows.all { it.decorView.isAttachedToWindow }) {
                windows.forEach { it.applyFullscreenBars() }
            }
        }
        // 窗口 attach 完成与横屏旋转稳定后各补一次，覆盖旋转导致的系统栏还原
        decor?.post(rehide)
        decor?.postDelayed(rehide, 300L)
        if (decor != null) {
            // 全面屏手势滑动、窗口重新获得焦点、DialogProperties 重设 decorFits 都会
            // 触发 insets 分发；检测到系统栏仍可见就重新隐藏，而不是只隐藏一次。
            ViewCompat.setOnApplyWindowInsetsListener(decor) { v, insets ->
                if (insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
                    v.post(rehide)
                }
                insets
            }
            removeGuard = {
                decor.removeCallbacks(rehide)
                ViewCompat.setOnApplyWindowInsetsListener(decor, null)
            }
        }
    }

    fun exit() {
        removeGuard?.invoke()
        removeGuard = null
        windows.forEach { it.restoreSystemBars(snapshots.getValue(it)) }
    }
}
