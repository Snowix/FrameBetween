package com.framebetween.app.ui.audio
import com.framebetween.app.core.L10n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.AppState
import com.framebetween.app.core.Codecs
import com.framebetween.app.core.Commands
import com.framebetween.app.core.Estimate
import com.framebetween.app.core.ExtractConfig
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.ui.common.ActionButton
import com.framebetween.app.ui.common.CommandPreview
import com.framebetween.app.ui.common.CurrentMediaCard
import com.framebetween.app.ui.common.InfoRow
import com.framebetween.app.ui.common.Label
import com.framebetween.app.ui.common.NumberField
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.ui.common.SizeEstimateCard
import com.framebetween.app.ui.common.StringDropdown
import com.framebetween.app.ui.common.nextOutput
import com.framebetween.app.ui.common.rememberMediaPicker
import com.framebetween.app.ui.common.rememberOutput

@Composable
fun AudioScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val media by AppState.current.collectAsState()
    val pick = rememberMediaPicker()

    var format by rememberSaveable { mutableStateOf("mp3") }
    var bitrate by rememberSaveable { mutableStateOf("192") }
    var startSec by rememberSaveable { mutableStateOf("") }
    var endSec by rememberSaveable { mutableStateOf("") }
    // 两个产物都按「原文件名 + MMddHHmmss」命名；切换容器时由 rememberOutput 自动重算
    var outputMute by rememberOutput("mp4", "mute")
    var outputAudio by rememberOutput(format, "audio")

    val lossless = format in setOf("wav", "flac")
    val startMs = ((startSec.toDoubleOrNull() ?: 0.0) * 1000).toLong()
    val endMs = endSec.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: -1L
    val config = ExtractConfig(
        format = format,
        bitrateKbps = bitrate.toIntOrNull() ?: 192,
        startMs = startMs,
        endMs = if (endMs > startMs) endMs else -1L
    )

    val muteArgs = media?.let { Commands.mute(it.path, outputMute) } ?: emptyList()
    val extractArgs = media?.let { Commands.extractAudio(it.path, outputAudio, config) } ?: emptyList()
    val audioDuration = if (config.endMs > 0) {
        config.endMs - config.startMs
    } else {
        ((media?.durationMs ?: 0L) - config.startMs).coerceAtLeast(0L)
    }
    val estimated = if (lossless) {
        Estimate.targetBytes(0, 1411, audioDuration)
    } else {
        Estimate.targetBytes(0, config.bitrateKbps, audioDuration)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(L10n.t("消音 / 音频提取"), L10n.t("视频静音与多格式音频导出"), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Label(L10n.t("输入文件"))
            CurrentMediaCard(onPick = pick)

            SectionCard(L10n.t("视频消音"), icon = Icons.AutoMirrored.Filled.VolumeOff) {
                Text(
                    L10n.t("去掉音轨但完整保留视频流，直接复制视频码流，几乎瞬时完成。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                CommandPreview(Commands.preview(muteArgs))
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    text = L10n.t("移除音轨"),
                    enabled = media != null && muteArgs.isNotEmpty(),
                    onClick = {
                        FFmpegEngine.submit(L10n.t("视频消音"), muteArgs, outputMute, 0)
                        outputMute = nextOutput("mp4", "mute")
                        onSubmitted()
                    }
                )
            }

            SectionCard(L10n.t("音频提取"), icon = Icons.Default.MusicNote) {
                StringDropdown(
                    label = L10n.t("输出格式"),
                    options = Codecs.audioFormats,
                    selected = format,
                    onSelect = { format = it },
                    optionLabel = { it.uppercase() }
                )
                Spacer(Modifier.height(10.dp))
                if (!lossless) {
                    InfoRow(L10n.t("音频码率"), "$bitrate kbps")
                    Slider(
                        value = (bitrate.toFloatOrNull() ?: 192f).coerceIn(32f, 320f),
                        onValueChange = { bitrate = it.toInt().toString() },
                        valueRange = 32f..320f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    NumberField(
                        label = L10n.t("音频码率"),
                        value = bitrate,
                        onValue = { bitrate = it },
                        suffix = "kbps",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        L10n.t("无损格式无需设置码率，输出体积按 PCM 1411 kbps 估算。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                Label(L10n.t("可选：只提取片段（秒，留空为全长）"))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondsField(L10n.t("起点秒"), startSec, { startSec = it }, Modifier.weight(1f))
                    SecondsField(L10n.t("终点秒"), endSec, { endSec = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                InfoRow(L10n.t("输出时长"), "${audioDuration / 1000}s")
                InfoRow(L10n.t("源音轨"), media?.audio?.codec?.uppercase() ?: L10n.t("无"))
            }

            SizeEstimateCard(
                original = media?.sizeBytes ?: 0L,
                estimated = estimated,
                rough = false
            )
            CommandPreview(Commands.preview(extractArgs))
            ActionButton(
                text = L10n.t("提取音频"),
                enabled = media != null && media?.hasAudio == true && extractArgs.isNotEmpty(),
                onClick = {
                    FFmpegEngine.submit(L10n.t("音频提取"), extractArgs, outputAudio, audioDuration)
                    outputAudio = nextOutput(format, "audio")
                    onSubmitted()
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SecondsField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) onValue(it) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}
