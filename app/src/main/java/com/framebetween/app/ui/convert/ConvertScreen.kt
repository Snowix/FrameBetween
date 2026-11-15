package com.framebetween.app.ui.convert
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.AppState
import com.framebetween.app.core.Codecs
import com.framebetween.app.core.Commands
import com.framebetween.app.core.ConvertConfig
import com.framebetween.app.core.Estimate
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.MediaInfo
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.ui.common.ActionButton
import com.framebetween.app.ui.common.CommandPreview
import com.framebetween.app.ui.common.CurrentMediaCard
import com.framebetween.app.ui.common.InfoRow
import com.framebetween.app.ui.common.Label
import com.framebetween.app.ui.common.LabeledDropdown
import com.framebetween.app.ui.common.NumberField
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.ui.common.SizeEstimateCard
import com.framebetween.app.ui.common.StringDropdown
import com.framebetween.app.ui.common.rememberMediaPicker
import com.framebetween.app.ui.common.rememberMultiPicker
import com.framebetween.app.ui.common.rememberOutput
import com.framebetween.app.util.isAudioContainer

private data class SpeedPreset(val name: String, val preset: String, val crf: Int)

private val speedPresets = listOf(
    SpeedPreset(L10n.t("极速"), "ultrafast", 28),
    SpeedPreset(L10n.t("均衡"), "veryfast", 23),
    SpeedPreset(L10n.t("高画质"), "medium", 18)
)

@Composable
fun ConvertScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val media by AppState.current.collectAsState()
    val pick = rememberMediaPicker()
    val batch by AppState.batchInputs.collectAsState()
    val pickMulti = rememberMultiPicker { added ->
        AppState.setBatchInputs((batch + added).distinctBy { it.path })
    }
    val inputs = remember(media, batch) {
        (listOfNotNull(media) + batch).distinctBy { it.path }
    }

    var container by rememberSaveable { mutableStateOf("mp4") }
    var videoCodec by rememberSaveable { mutableStateOf(Codecs.DEFAULT_VIDEO) }
    var audioCodec by rememberSaveable { mutableStateOf("aac") }
    var resolution by rememberSaveable { mutableStateOf("0") }
    var customWidth by rememberSaveable { mutableStateOf("") }
    var customHeight by rememberSaveable { mutableStateOf("") }
    var crfMode by rememberSaveable { mutableStateOf(false) }
    var videoBitrate by rememberSaveable { mutableStateOf("2500") }
    var crf by rememberSaveable { mutableStateOf(23f) }
    var audioBitrate by rememberSaveable { mutableStateOf("128") }
    var preset by rememberSaveable { mutableStateOf("veryfast") }
    var fpsLimit by rememberSaveable { mutableStateOf("") }
    var output by rememberOutput(container, "convert")

    // 仅当用户真正切换容器时带出默认编码；恢复页面时保留已选参数
    var initializedContainer by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(container) {
        if (initializedContainer == container) return@LaunchedEffect
        initializedContainer = container
        val defaults = Commands.defaultCodecs(container)
        videoCodec = defaults.first
        audioCodec = defaults.second
        // 输出路径由 rememberOutput 绑定容器变化统一重算，这里不再重复生成
    }

    val config = ConvertConfig(
        container = container,
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        targetWidth = customWidth.toIntOrNull() ?: 0,
        targetHeight = customHeight.toIntOrNull() ?: (resolution.toIntOrNull() ?: 0),
        videoBitrateKbps = videoBitrate.toIntOrNull() ?: 2500,
        audioBitrateKbps = audioBitrate.toIntOrNull() ?: 128,
        crf = if (crfMode) crf.toInt() else 0,
        preset = preset,
        fpsLimit = fpsLimit.toIntOrNull() ?: 0
    )
    val audioOnly = config.audioOnly || isAudioContainer(container) ||
        (inputs.isNotEmpty() && inputs.all { !it.hasVideo })
    val first = inputs.firstOrNull()
    val args = first?.let {
        Commands.convert(it.path, output, config, it.hasVideo, it.hasAudio)
    } ?: emptyList()
    val estimated = inputs.sumOf { Estimate.convertBytes(config, it) }
    val originalBytes = inputs.sumOf { it.sizeBytes }
    val rough = Estimate.isEstimateRough(config) && !audioOnly

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(L10n.t("压缩 / 格式转换"), L10n.t("参数联动 · 实时预估输出体积"), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Label(L10n.t("输入文件"))
            CurrentMediaCard(onPick = pick)

            SectionCard(L10n.t("批量文件（%1\$s）", inputs.size), icon = Icons.AutoMirrored.Filled.PlaylistAdd) {
                Text(
                    L10n.t("当前文件作为第 1 项；添加的文件按相同参数依次处理，各自生成独立输出。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = pickMulti, modifier = Modifier.weight(1f)) {
                        Text(L10n.t("添加文件"))
                    }
                    OutlinedButton(
                        onClick = { AppState.setBatchInputs(emptyList()) },
                        enabled = batch.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text(L10n.t("清空附加")) }
                }
                inputs.forEachIndexed { index, info ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}. ${info.displayName}",
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (info.path == media?.path) AppState.removeCurrent(info.path)
                            else AppState.setBatchInputs(batch.filterNot { it.path == info.path })
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = L10n.t("移除"),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            SectionCard(L10n.t("目标格式"), icon = Icons.Default.Tune) {
                StringDropdown(
                    label = L10n.t("封装格式"),
                    options = Codecs.containers,
                    selected = container,
                    onSelect = { container = it },
                    optionLabel = { it.uppercase() }
                )
                Spacer(Modifier.height(10.dp))
                if (!audioOnly) {
                    LabeledDropdown(
                        label = L10n.t("视频编码"),
                        options = Codecs.video,
                        selected = videoCodec,
                        onSelect = { videoCodec = it }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                LabeledDropdown(
                    label = L10n.t("音频编码"),
                    options = Codecs.audio,
                    selected = audioCodec,
                    onSelect = { audioCodec = it }
                )
                if (videoCodec in setOf("libx264", "libx265") && !audioOnly) {
                    Spacer(Modifier.height(10.dp))
                    StringDropdown(
                        label = L10n.t("编码预设（速度 ↔ 压缩率）"),
                        options = Codecs.presets,
                        selected = preset,
                        onSelect = { preset = it }
                    )
                }
            }

            if (!audioOnly) {
                SectionCard(L10n.t("分辨率与帧率")) {
                    LabeledDropdown(
                        label = L10n.t("分辨率（按高度等比缩放）"),
                        options = Codecs.resolutions,
                        selected = resolution,
                        onSelect = { resolution = it }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            label = L10n.t("自定义宽"),
                            value = customWidth,
                            onValue = { customWidth = it },
                            suffix = "px",
                            modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            label = L10n.t("自定义高"),
                            value = customHeight,
                            onValue = { customHeight = it },
                            suffix = "px",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        L10n.t("自定义宽高优先于分辨率预设，留空则保持比例。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    NumberField(
                        label = L10n.t("最大帧率（0 / 留空为不限制）"),
                        value = fpsLimit,
                        onValue = { fpsLimit = it },
                        suffix = "fps",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SectionCard(L10n.t("速度与质量控制"), icon = Icons.Default.Speed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    speedPresets.forEach { item ->
                        FilterChip(
                            selected = preset == item.preset && crfMode && crf.toInt() == item.crf,
                            onClick = {
                                preset = item.preset
                                crfMode = true
                                crf = item.crf.toFloat()
                            },
                            label = { Text(item.name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    L10n.t("预设越慢，同画质下体积越小；直接复制流（copy）最快但无法改变编码参数。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(L10n.t("码率 / 质量")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L10n.t("CRF 恒定质量模式"), modifier = Modifier.weight(1f))
                    Switch(checked = crfMode, onCheckedChange = { crfMode = it })
                }
                Spacer(Modifier.height(8.dp))
                if (crfMode) {
                    InfoRow("CRF", crf.toInt().toString())
                    Slider(
                        value = crf,
                        onValueChange = { crf = it },
                        valueRange = 16f..34f,
                        steps = 17,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        L10n.t("数值越小画质越好、体积越大。该模式无固定码率，体积为经验估算。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    InfoRow(L10n.t("视频码率"), "$videoBitrate kbps")
                    Slider(
                        value = (videoBitrate.toFloatOrNull() ?: 2500f).coerceIn(200f, 12000f),
                        onValueChange = { videoBitrate = it.toInt().toString() },
                        valueRange = 200f..12000f,
                        enabled = !audioOnly,
                        modifier = Modifier.fillMaxWidth()
                    )
                    NumberField(
                        label = L10n.t("视频码率"),
                        value = videoBitrate,
                        onValue = { videoBitrate = it },
                        suffix = "kbps",
                        enabled = !audioOnly,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(10.dp))
                InfoRow(L10n.t("音频码率"), "$audioBitrate kbps")
                Slider(
                    value = (audioBitrate.toFloatOrNull() ?: 128f).coerceIn(32f, 320f),
                    onValueChange = { audioBitrate = it.toInt().toString() },
                    valueRange = 32f..320f,
                    enabled = audioCodec !in setOf("copy", "none", "flac", "pcm_s16le"),
                    modifier = Modifier.fillMaxWidth()
                )
                NumberField(
                    label = L10n.t("音频码率"),
                    value = audioBitrate,
                    onValue = { audioBitrate = it },
                    suffix = "kbps",
                    enabled = audioCodec !in setOf("copy", "none", "flac", "pcm_s16le"),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SizeEstimateCard(
                original = originalBytes,
                estimated = estimated,
                rough = rough
            )
            CommandPreview(
                Commands.preview(args) +
                    if (inputs.size > 1) L10n.t("\n… 共 %1\$s 个任务，其余文件使用相同参数", inputs.size) else ""
            )
            ActionButton(
                text = if (inputs.size > 1) {
                    L10n.t("开始批量处理（%1\$s）", inputs.size)
                } else {
                    L10n.t("开始处理")
                },
                enabled = inputs.isNotEmpty() && args.isNotEmpty(),
                onClick = {
                    inputs.forEach { info ->
                        // 每个任务都在提交这一刻按「原文件名 + 当前 MMddHHmmss」生成，批量第 1 个也不能例外
                        val out = MediaRepository.newOutput(container, sourceName = info.displayName)
                        val cmd = Commands.convert(info.path, out, config, info.hasVideo, info.hasAudio)
                        FFmpegEngine.submit(L10n.t("压缩 / 格式转换"), cmd, out, info.durationMs)
                    }
                    output = MediaRepository.newOutput(container, "convert", inputs.firstOrNull()?.displayName)
                    onSubmitted()
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
