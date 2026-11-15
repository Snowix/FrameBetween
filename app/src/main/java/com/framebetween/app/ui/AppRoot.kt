package com.framebetween.app.ui
import com.framebetween.app.core.L10n

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.framebetween.app.core.AppState
import com.framebetween.app.util.InsetsPolicy
import com.framebetween.app.ui.audio.AudioScreen
import com.framebetween.app.ui.audiocut.AudioCutScreen
import com.framebetween.app.ui.audiojoin.AudioJoinScreen
import com.framebetween.app.ui.common.NavigationTapGuard
import com.framebetween.app.ui.common.NowPlayingBar
import com.framebetween.app.ui.convert.ConvertScreen
import com.framebetween.app.ui.crop.CropScreen
import com.framebetween.app.ui.gif.GifScreen
import com.framebetween.app.ui.home.HomeScreen
import com.framebetween.app.ui.library.MediaLibraryScreen
import com.framebetween.app.ui.merge.MergeScreen
import com.framebetween.app.ui.settings.FolderSettingsScreen
import com.framebetween.app.ui.settings.SettingsPage
import com.framebetween.app.ui.settings.SettingsScreen
import com.framebetween.app.ui.speed.SpeedScreen
import com.framebetween.app.ui.tasks.TasksScreen
import com.framebetween.app.ui.terminal.TerminalScreen
import com.framebetween.app.ui.transform.TransformScreen
import com.framebetween.app.ui.trim.TrimScreen
import com.framebetween.app.ui.watermark.WatermarkScreen

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val TERMINAL = "terminal"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
    const val CONVERT = "convert"
    const val TRIM = "trim"
    const val CROP = "crop"
    const val MERGE = "merge"
    const val TRANSFORM = "transform"
    const val SPEED = "speed"
    const val WATERMARK = "watermark"
    const val AUDIO = "audio"
    const val GIF = "gif"
    const val AUDIO_JOIN = "audio_join"
    const val AUDIO_CUT = "audio_cut"
    const val FOLDER_SETTINGS = "folder_settings"
    const val SETTINGS_ADVANCED = "settings_advanced"
    const val SETTINGS_ABOUT = "settings_about"
    const val SETTINGS_LOGS = "settings_logs"
}

private fun SettingsPage.toRoute(): String = when (this) {
    SettingsPage.MAIN -> Routes.SETTINGS
    SettingsPage.ADVANCED -> Routes.SETTINGS_ADVANCED
    SettingsPage.ABOUT -> Routes.SETTINGS_ABOUT
    SettingsPage.LOGS -> Routes.SETTINGS_LOGS
}

private val settingsRoutes = setOf(
    Routes.SETTINGS, Routes.FOLDER_SETTINGS,
    Routes.SETTINGS_ADVANCED, Routes.SETTINGS_ABOUT, Routes.SETTINGS_LOGS
)

private val toolRoutes = setOf(
    Routes.TERMINAL, Routes.CONVERT, Routes.TRIM, Routes.CROP,
    Routes.MERGE, Routes.TRANSFORM, Routes.SPEED, Routes.WATERMARK,
    Routes.AUDIO, Routes.GIF, Routes.AUDIO_JOIN, Routes.AUDIO_CUT
)

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/**
 * 底部主入口始终同层级。切换 tab 时保存被弹出的整个首页返回栈（含其上的工具页），
 * 回来时恢复：工具页的参数与「当前文件」都不会因为切换导航而重置。
 */
private fun NavHostController.switchTab(target: String) {
    if (currentDestination?.route == target) return
    navigate(target) {
        popUpTo(Routes.HOME) {
            inclusive = false
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.goTasks() = switchTab(Routes.TASKS)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppRoot(snackbarHostState: SnackbarHostState) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        Tab(Routes.HOME, L10n.t("首页"), Icons.Default.Home),
        Tab(Routes.LIBRARY, L10n.t("媒体库"), Icons.Default.VideoLibrary),
        Tab(Routes.TASKS, L10n.t("任务"), Icons.AutoMirrored.Filled.List),
        Tab(Routes.SETTINGS, L10n.t("设置"), Icons.Default.Settings)
    )

    // 部分厂商在输入法可见时会把 IME 高度并入 navigationBars insets。若直接在 bottomBar
    // 使用 navigationBarsPadding()，导航栏会被撑高并在 windowBackground 上露出一条白色横幅。
    // 这里只在输入法隐藏时采样一次，之后锁定该值，输入法弹出/收起都不改变底部导航占位。
    val density = LocalDensity.current
    val imeVisible = WindowInsets.isImeVisible
    val navBottomPx = WindowInsets.navigationBars.getBottom(density)
    var stableNavBottomPx by remember { mutableIntStateOf(navBottomPx) }
    LaunchedEffect(imeVisible, navBottomPx) {
        if (!imeVisible) stableNavBottomPx = navBottomPx
    }
    // 内容区只留白一次。部分 OEM 把 IME 高度并入 navigationBars，
    // Scaffold 默认 contentWindowInsets 会把这份高度算进 padding，再叠加 imePadding
    // 就会多抬升约一个键盘高度，在光标与输入法之间露出白色横幅。
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val contentBottomPx = InsetsPolicy.contentBottomPadding(
        imeVisible, imeBottomPx, stableNavBottomPx
    )

    Scaffold(
        // 显式关闭系统窗口 insets：窗口本身非 edge-to-edge，底部占位由 bottomBar 计算，
        // 内容区不需要再读取会随输入法变化的 navigationBars insets。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val playing by AppState.nowPlaying.collectAsState()
            // 系统导航栏区域用锁定的固定值留白：既避免三键栏 / 手势条盖住底部导航，
            // 也不会因输入法弹出被撑出白色横幅。NavigationBar 内部 insets 关闭，
            // 让 72dp 全部用于图标与文字。
            Column(Modifier.padding(bottom = with(density) { stableNavBottomPx.toDp() })) {
                if (playing.isNotEmpty()) {
                    NowPlayingBar(items = playing, onStop = { AppState.stopAudio() })
                }
                NavigationBar(
                    modifier = Modifier.height(72.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route ||
                                (tab.route == Routes.HOME && route in toolRoutes) ||
                                (tab.route == Routes.SETTINGS && route in settingsRoutes),
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        // 返回后快速点击误触上一页的根因是 NavHost 默认 700ms 淡出期间旧页面仍
        // 留在组合里并接收点击。四个转场全部设为 None，再叠一层短触摸守卫。
        NavigationTapGuard(route = route) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
                // adjustNothing 下窗口不随输入法缩放；内容区只按真实 IME 高度留白一次，
                // 让输入框能滚动到键盘上方，同时不再出现双重抬升的白色横幅。
                modifier = Modifier
                    .padding(padding)
                    .padding(bottom = with(density) { contentBottomPx.toDp() })
            ) {
            composable(Routes.HOME) { HomeScreen(onOpen = { navController.navigate(it) }) }
            composable(Routes.LIBRARY) { MediaLibraryScreen() }
            composable(Routes.TERMINAL) { TerminalScreen() }
            composable(Routes.TASKS) { TasksScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenFolderSettings = { navController.navigate(Routes.FOLDER_SETTINGS) },
                    onOpenPage = { navController.navigate(it.toRoute()) }
                )
            }
            composable(Routes.FOLDER_SETTINGS) {
                FolderSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS_ADVANCED) {
                SettingsScreen(
                    onOpenFolderSettings = { navController.navigate(Routes.FOLDER_SETTINGS) },
                    page = SettingsPage.ADVANCED,
                    onOpenPage = { navController.navigate(it.toRoute()) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS_ABOUT) {
                SettingsScreen(
                    onOpenFolderSettings = { navController.navigate(Routes.FOLDER_SETTINGS) },
                    page = SettingsPage.ABOUT,
                    onOpenPage = { navController.navigate(it.toRoute()) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS_LOGS) {
                SettingsScreen(
                    onOpenFolderSettings = { navController.navigate(Routes.FOLDER_SETTINGS) },
                    page = SettingsPage.LOGS,
                    onOpenPage = { navController.navigate(it.toRoute()) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.CONVERT) {
                ConvertScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitted = { navController.goTasks() }
                )
            }
            composable(Routes.TRIM) {
                TrimScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitted = { navController.goTasks() }
                )
            }
            composable(Routes.CROP) {
                CropScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitted = { navController.goTasks() }
                )
            }
            composable(Routes.MERGE) {
                MergeScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitted = { navController.goTasks() }
                )
            }
            composable(Routes.TRANSFORM) {
                TransformScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitted = { navController.goTasks() }
                )
            }
            composable(Routes.SPEED) {
                SpeedScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitted = { navController.goTasks() }
                )
            }
            composable(Routes.WATERMARK) {
                WatermarkScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitted = { navController.goTasks() }
                )
            }
            composable(Routes.AUDIO) {
                AudioScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitted = { navController.goTasks() }
                )
            }
            composable(Routes.GIF) {
                GifScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitted = { navController.goTasks() }
                )
            }
            composable(Routes.AUDIO_JOIN) {
                AudioJoinScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitted = { navController.goTasks() }
                )
            }
            composable(Routes.AUDIO_CUT) {
                AudioCutScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitted = { navController.goTasks() }
                )
            }
            }
        }
    }
}
