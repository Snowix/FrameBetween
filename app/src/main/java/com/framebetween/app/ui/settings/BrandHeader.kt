package com.framebetween.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.framebetween.app.R
import com.framebetween.app.core.AppBrand
import com.framebetween.app.core.Settings
import com.framebetween.app.core.ThemeMode
import kotlin.random.Random

/** 一款横幅字标 + 是否必须使用墨色画布（反白字标在浅底上不可见）。 */
private data class BrandStyle(val res: Int, val darkCanvas: Boolean)

/**
 * brand/vector/outline 的横幅字标，随包保存；点击画布随机切换当彩蛋。
 * stacked 竖版堆叠字标不再参与切换，避免画布高度忽大忽小。
 */
private val BRAND_STYLES = listOf(
    BrandStyle(R.drawable.brand_lockup, false),
    BrandStyle(R.drawable.brand_lockup_dark, true),
    BrandStyle(R.drawable.brand_lockup_mono, false),
    BrandStyle(R.drawable.brand_lockup_white, true)
)

private val LIGHT_CANVAS = Color(0xFFEEF0FA)
private val DARK_CANVAS = Color(0xFF16182D)

/**
 * 设置页顶部的品牌画布：
 * 浅色主题默认 framebetween-lockup.svg + #EEF0FA 画布；
 * 深色主题默认 framebetween-lockup-dark.svg + #16182D 墨色画布；
 * 切到深底反白横幅字标时，无论当前主题都强制使用 #16182D 墨色画布。
 */
@Composable
fun BrandHeader() {
    val mode by Settings.theme.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    var styleIndex by rememberSaveable { mutableStateOf(if (dark) 1 else 0) }
    // 切换主题时回到该主题的默认样式
    LaunchedEffect(dark) { styleIndex = if (dark) 1 else 0 }

    val index = styleIndex.coerceIn(0, BRAND_STYLES.lastIndex)
    val style = BRAND_STYLES[index]
    val canvas = if (dark || style.darkCanvas) DARK_CANVAS else LIGHT_CANVAS

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = canvas)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clickable {
                    var next = index
                    while (next == index && BRAND_STYLES.size > 1) {
                        next = Random.nextInt(BRAND_STYLES.size)
                    }
                    styleIndex = next
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(style.res),
                contentDescription = AppBrand.displayName(),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.86f).height(104.dp)
            )
        }
    }
}
