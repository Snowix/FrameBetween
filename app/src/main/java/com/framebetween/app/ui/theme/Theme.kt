package com.framebetween.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.framebetween.app.core.Settings
import com.framebetween.app.core.ThemeMode

/** 单一配色 + 日间 / 夜间 / 跟随系统，界面切换全部即时完成，不做任何过渡动画。 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val mode by Settings.theme.collectAsState()
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
