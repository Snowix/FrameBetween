package com.framebetween.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.framebetween.app.R

/** 品牌标识：按官方图标取资源，日间 / 夜间各一套，自定义项回退通用图标。 */
fun providerLogoRes(key: String, dark: Boolean): Int? = when (key) {
    "openai" -> if (dark) R.drawable.ai_openai_dark else R.drawable.ai_openai_light
    "deepseek" -> if (dark) R.drawable.ai_deepseek_dark else R.drawable.ai_deepseek_light
    "moonshot" -> if (dark) R.drawable.ai_moonshot_dark else R.drawable.ai_moonshot_light
    "kimi" -> if (dark) R.drawable.ai_kimi_dark else R.drawable.ai_kimi_light
    "siliconflow" -> if (dark) R.drawable.ai_siliconflow_dark else R.drawable.ai_siliconflow_light
    "zhipu" -> if (dark) R.drawable.ai_zhipu_dark else R.drawable.ai_zhipu_light
    "qwen" -> if (dark) R.drawable.ai_qwen_dark else R.drawable.ai_qwen_light
    "ollama" -> if (dark) R.drawable.ai_ollama_dark else R.drawable.ai_ollama_light
    "lmstudio" -> if (dark) R.drawable.ai_lmstudio_dark else R.drawable.ai_lmstudio_light
    else -> null
}

@Composable
fun ProviderLogo(key: String, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val res = providerLogoRes(key, dark)
    if (res != null) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = Modifier.size(size).then(modifier)
        )
    } else {
        Icon(
            Icons.Default.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size).then(modifier)
        )
    }
}
