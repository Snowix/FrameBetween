package com.framebetween.app.ui.gif
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
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
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
import com.framebetween.app.core.Commands
import com.framebetween.app.core.Estimate
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.GifConfig
import com.framebetween.app.ui.common.ActionButton
import com.framebetween.app.ui.common.CommandPreview
import com.framebetween.app.ui.common.CurrentMediaCard
import com.framebetween.app.ui.common.InfoRow
import com.framebetween.app.ui.common.Label
import com.framebetween.app.ui.common.LabeledDropdown
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.ui.common.SizeEstimateCard
import com.framebetween.app.ui.common.VideoPreview
import com.framebetween.app.ui.common.nextOutput
import com.framebetween.app.ui.common.rememberMediaPicker
import com.framebetween.app.ui.common.rememberOutput
import com.framebetween.app.util.formatClock
import com.framebetween.app.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val media by AppState.current.collectAsState()
    val pick = rememberMediaPicker()

    var startMs by rememberSaveable { mutableStateOf(0L) }
    var endMs by rememberSaveable { mutableStateOf(0L) }
    var width by rememberSaveable { mutableStateOf("480") }
    var fps by rememberSaveable { mutableStateOf(12) }
    var loop by rememberSaveable { mutableStateOf(0) }
    var highQuality by rememberSaveable { mutableStateOf(true) }
    var output by rememberOutput("gif", "gif")

    val duration = (media?.durationMs ?: 0L).coerceAtLeast(1L)
    var initializedPath by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(media?.path) {
        if (initializedPath == media?.path) return@LaunchedEffect
        initializedPath = media?.path
        startMs = 0L
        endMs = duration
    }

    fun updateRange(fromMs: Long, toMs: Long) {
        val from = fromMs.coerceIn(0L, duration)
        startMs = from
        endMs = toMs.coerceIn(from, duration)
    }

    val selected = (endMs - startMs).coerceAtLeast(0L)
    val widthPx = width.toIntOrNull()?.coerceIn(120, 1280) ?: 480
    val config = GifConfig(startMs, endMs, widthPx, fps, loop, highQuality)
    val args = media?.let { Commands.gif(it.path, output, config) } ?: emptyList()
    val estimated = Estimate.gifBytes(widthPx, fps, selected)

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(L10n.t("视频转 GIF"), L10n.t("调色板优化 · 本地生成"), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Label(L10n.t("输入文件"))
            CurrentMediaCard(onPick = pick)

            if (media?.hasVideo == true) {
                VideoPreview(
                    path = media!!.path,
                    startMs = startMs,
                    endMs = endMs,
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }

            SectionCard(L10n.t("区间选择"), icon = Icons.Default.Gif) {
                InfoRow(L10n.t("源时长"), formatClock(duration))
                Spacer(Modifier.height(8.dp))
                val sliderStart = startMs.toFloat().coerceIn(0f, duration.toFloat())
                val sliderEnd = endMs.toFloat().coerceIn(sliderStart, duration.toFloat())
                RangeSlider(
                    value = sliderStart..sliderEnd,
                    onValueChange = { range ->
                        updateRange(range.start.toLong(), range.endInclusive.toLong())
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                InfoRow(L10n.t("起点"), formatDuration(startMs))
                InfoRow(L10n.t("终点"), formatDuration(endMs))
                InfoRow(L10n.t("输出时长"), formatDuration(selected))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = { updateRange(0L, duration) },
                        label = { Text(L10n.t("全程")) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { updateRange(0L, 5_000L.coerceAtMost(duration)) },
                        label = { Text(L10n.t("前 5 秒")) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { updateRange(0L, 10_000L.coerceAtMost(duration)) },
                        label = { Text(L10n.t("前 10 秒")) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    L10n.t("拖动两端手柄选择起点与终点，预览会循环播放所选区间。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(L10n.t("画质与体积")) {
                LabeledDropdown(
                    label = L10n.t("输出宽度"),
                    options = listOf("240" to "240 px", "320" to "320 px", "480" to L10n.t("480 px（推荐）"), "640" to "640 px", "800" to "800 px"),
                    selected = width,
                    onSelect = { width = it }
                )
                Spacer(Modifier.height(10.dp))
                Text(L10n.t("帧率（越高越流畅、体积越大）"), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(8, 10, 12, 15, 20).forEach { value ->
                        FilterChip(
                            selected = fps == value,
                            onClick = { fps = value },
                            label = { Text("$value") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LabeledDropdown(
                    label = L10n.t("循环次数"),
                    options = listOf("0" to L10n.t("无限循环"), "1" to L10n.t("播放 1 次"), "3" to L10n.t("播放 3 次")),
                    selected = loop.toString(),
                    onSelect = { loop = it.toIntOrNull() ?: 0 }
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L10n.t("高质量调色板（palettegen + paletteuse）"), modifier = Modifier.weight(1f))
                    Switch(checked = highQuality, onCheckedChange = { highQuality = it })
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    L10n.t("两遍处理会先分析整段画面生成最优 256 色调色板，色彩更准、抖动更少；关闭后速度更快。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (media?.hasVideo == true) {
                SizeEstimateCard(
                    original = media!!.sizeBytes,
                    estimated = estimated,
                    rough = true
                )
            }
            CommandPreview(Commands.preview(args))
            ActionButton(
                text = L10n.t("开始生成 GIF"),
                enabled = media?.hasVideo == true && selected > 0 && args.isNotEmpty(),
                onClick = {
                    FFmpegEngine.submit(L10n.t("视频转 GIF"), args, output, selected)
                    output = nextOutput("gif", "gif")
                    onSubmitted()
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
