package com.framebetween.app.ui.audiojoin
import com.framebetween.app.core.L10n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.AppState
import com.framebetween.app.core.AudioJoinConfig
import com.framebetween.app.core.AudioJoinMode
import com.framebetween.app.core.AudioOutputConfig
import com.framebetween.app.core.Commands
import com.framebetween.app.core.Estimate
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.ui.common.ActionButton
import com.framebetween.app.ui.common.AudioOutputFields
import com.framebetween.app.ui.common.AudioPreview
import com.framebetween.app.ui.common.CommandPreview
import com.framebetween.app.ui.common.Label
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.ui.common.SizeEstimateCard
import com.framebetween.app.ui.common.rememberMultiPicker
import com.framebetween.app.ui.common.nextOutput
import com.framebetween.app.ui.common.rememberOutput
import com.framebetween.app.util.formatBytes
import com.framebetween.app.util.formatClock
import java.util.Locale

@Composable
fun AudioJoinScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val files by AppState.audioJoinFiles.collectAsState()
    val pickMulti = rememberMultiPicker(arrayOf("audio/*")) { added ->
        AppState.setAudioJoinFiles((files + added).distinctBy { it.path })
    }

    var modeName by rememberSaveable { mutableStateOf(AudioJoinMode.SEQUENCE.name) }
    var volumeCsv by rememberSaveable { mutableStateOf("") }
    var crossfadeSec by rememberSaveable { mutableStateOf(0f) }
    var format by rememberSaveable { mutableStateOf("mp3") }
    var sampleRate by rememberSaveable { mutableStateOf(44100) }
    var bitrate by rememberSaveable { mutableStateOf(192) }
    // 以第一条音轨的文件名为前缀；格式或首个输入变化时自动重算
    var output by rememberOutput(format, "join", files.firstOrNull()?.displayName)

    val mode = runCatching { AudioJoinMode.valueOf(modeName) }
        .getOrDefault(AudioJoinMode.SEQUENCE)
    val parsedVolumes = volumeCsv.split(",").mapNotNull { it.toFloatOrNull() }
    fun volumeAt(index: Int): Float = parsedVolumes.getOrElse(index) { 1f }.coerceIn(0f, 2f)
    fun setVolume(index: Int, value: Float) {
        val next = MutableList(files.size) { volumeAt(it) }
        if (index in next.indices) next[index] = value.coerceIn(0f, 2f)
        volumeCsv = next.joinToString(",") { "%.2f".format(Locale.US, it) }
    }

    val audioOut = AudioOutputConfig(format, sampleRate, bitrate)
    val config = AudioJoinConfig(
        mode = mode,
        volumes = files.indices.map { volumeAt(it) },
        crossfadeMs = (crossfadeSec * 1000).toLong(),
        output = audioOut
    )
    val args = if (files.isEmpty()) emptyList() else {
        Commands.audioJoin(files.map { it.path }, output, config)
    }
    val durations = files.map { it.durationMs.coerceAtLeast(0L) }
    val overlap = if (mode == AudioJoinMode.SEQUENCE && files.size > 1) {
        config.crossfadeMs * (files.size - 1)
    } else 0L
    val totalMs = (if (mode == AudioJoinMode.MIX) {
        durations.maxOrNull() ?: 0L
    } else {
        durations.sum() - overlap
    }).coerceAtLeast(0L)
    val estimated = Estimate.audioBytes(audioOut, totalMs)
    val originalBytes = files.sumOf { it.sizeBytes }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(L10n.t("音频拼接"), L10n.t("顺序拼接或音轨合并 · 逐轨音量与交叉淡化"), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Label(L10n.t("音频列表（%1\$s）", files.size))
            SectionCard(L10n.t("添加的音频"), icon = Icons.AutoMirrored.Filled.PlaylistAdd) {
                Text(
                    L10n.t("每段音频可单独试听；用上下箭头调整顺序拼接的先后，音量 100% 为原始响度。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = pickMulti, modifier = Modifier.weight(1f)) {
                        Text(L10n.t("添加音频"))
                    }
                    OutlinedButton(
                        onClick = { AppState.setAudioJoinFiles(emptyList()); volumeCsv = "" },
                        enabled = files.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text(L10n.t("清空")) }
                }
                files.forEachIndexed { index, info ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AudioPreview(info.path)
                        Column(Modifier.weight(1f)) {
                            Text(info.displayName, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${formatClock(info.durationMs)} · ${formatBytes(info.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    AppState.setAudioJoinFiles(files.toMutableList().also {
                                        java.util.Collections.swap(it, index, index - 1)
                                    })
                                }
                            },
                            enabled = index > 0
                        ) { Icon(Icons.Default.KeyboardArrowUp, null, Modifier.size(18.dp)) }
                        IconButton(
                            onClick = {
                                if (index < files.lastIndex) {
                                    AppState.setAudioJoinFiles(files.toMutableList().also {
                                        java.util.Collections.swap(it, index, index + 1)
                                    })
                                }
                            },
                            enabled = index < files.lastIndex
                        ) { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp)) }
                        IconButton(onClick = {
                            AppState.setAudioJoinFiles(files.filterNot { it.path == info.path })
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = L10n.t("移除"),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        L10n.t("音量：%1\$s%%", (volumeAt(index) * 100).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = volumeAt(index),
                        onValueChange = { setVolume(index, it) },
                        valueRange = 0f..2f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SectionCard(L10n.t("拼接方式"), icon = Icons.Default.GraphicEq) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioJoinMode.entries.forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { modeName = item.name },
                            label = { Text(item.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    L10n.t("顺序：首尾相接；音轨合并：所有音频同时播放，长度取最长的一段。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    L10n.t("交叉淡入淡出：%1\$ss", "%.1f".format(Locale.US, crossfadeSec)),
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = crossfadeSec,
                    onValueChange = { crossfadeSec = it },
                    valueRange = 0f..5f,
                    enabled = mode == AudioJoinMode.SEQUENCE && files.size > 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    L10n.t("0 秒为直接拼接；设为 0.5–5 秒可在相邻音频间做平滑过渡（仅顺序模式）。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(L10n.t("输出设置")) {
                AudioOutputFields(
                    format = format,
                    onFormat = { format = it },
                    sampleRate = sampleRate,
                    onSampleRate = { sampleRate = it },
                    bitrateKbps = bitrate,
                    onBitrate = { bitrate = it }
                )
            }

            SizeEstimateCard(original = originalBytes, estimated = estimated, rough = false)
            CommandPreview(Commands.preview(args))
            ActionButton(
                text = L10n.t("开始拼接"),
                enabled = files.isNotEmpty() && args.isNotEmpty(),
                onClick = {
                    FFmpegEngine.submit(L10n.t("音频拼接"), args, output, totalMs)
                    output = nextOutput(format, "join", files.firstOrNull()?.displayName)
                    onSubmitted()
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
