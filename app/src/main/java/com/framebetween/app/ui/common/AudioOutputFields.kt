package com.framebetween.app.ui.common
import com.framebetween.app.core.L10n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.Codecs
import kotlin.math.roundToInt

/** 音频拼接 / 剪切共用的输出参数：格式、采样率、码率档位。 */
@Composable
fun AudioOutputFields(
    format: String,
    onFormat: (String) -> Unit,
    sampleRate: Int,
    onSampleRate: (Int) -> Unit,
    bitrateKbps: Int,
    onBitrate: (Int) -> Unit
) {
    val lossless = format in setOf("wav", "flac")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StringDropdown(
            label = L10n.t("输出格式"),
            options = Codecs.audioFormats,
            selected = format,
            onSelect = onFormat,
            optionLabel = { it.uppercase() }
        )
        StringDropdown(
            label = L10n.t("采样率"),
            options = Codecs.sampleRates.map { it.toString() },
            selected = sampleRate.toString(),
            onSelect = { onSampleRate(it.toIntOrNull() ?: 44100) },
            optionLabel = { "$it Hz" }
        )
        if (lossless) {
            Text(
                L10n.t("无损格式无需设置码率，输出体积按 PCM 1411 kbps 估算。"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val presets = Codecs.bitratePresets
            val current = if (bitrateKbps in presets) bitrateKbps else 192
            val index = presets.indexOf(current)
            InfoRow(L10n.t("音频码率"), Codecs.bitrateLabel(current))
            // 只在 5 个档位之间切换，不做无极调节
            Slider(
                value = index.toFloat(),
                onValueChange = { value ->
                    onBitrate(presets[value.roundToInt().coerceIn(0, presets.lastIndex)])
                },
                valueRange = 0f..presets.lastIndex.toFloat(),
                steps = presets.size - 2,
                modifier = Modifier.fillMaxWidth()
            )
            // 原输入框改为下拉选择，避免出现档位之外的任意值
            StringDropdown(
                label = L10n.t("音频码率"),
                options = presets.map { it.toString() },
                selected = current.toString(),
                onSelect = { onBitrate(it.toIntOrNull() ?: 192) },
                optionLabel = { value ->
                    val kbps = value.toIntOrNull() ?: 0
                    if (kbps <= 0) L10n.t("不变（由编码器决定）") else Codecs.bitrateLabel(kbps)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                L10n.t("「不变」表示不指定 -b:a，由编码器按默认策略决定码率；体积预估按 192 kbps 粗算。"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}
