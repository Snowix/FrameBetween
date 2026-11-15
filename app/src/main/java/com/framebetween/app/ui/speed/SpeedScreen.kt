package com.framebetween.app.ui.speed
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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.AppState
import com.framebetween.app.core.Commands
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.SpeedPreset
import com.framebetween.app.ui.common.ActionButton
import com.framebetween.app.ui.common.CommandPreview
import com.framebetween.app.ui.common.CurrentMediaCard
import com.framebetween.app.ui.common.InfoRow
import com.framebetween.app.ui.common.Label
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.ui.common.VideoPreview
import com.framebetween.app.ui.common.nextOutput
import com.framebetween.app.ui.common.rememberMediaPicker
import com.framebetween.app.ui.common.rememberOutput
import com.framebetween.app.util.formatClock
import java.util.Locale

@Composable
fun SpeedScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val media by AppState.current.collectAsState()
    val pick = rememberMediaPicker()
    var speed by rememberSaveable { mutableStateOf(1f) }
    var output by rememberOutput("mp4", "speed")

    val args = media?.let {
        Commands.speed(it.path, output, speed, it.videoKbps, it.hasAudio)
    } ?: emptyList()
    val estimatedMs = if (speed > 0) {
        ((media?.durationMs ?: 0L) / speed.toDouble()).toLong()
    } else 0L

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(L10n.t("视频变速"), L10n.t("0.25× – 4× · 音画同步"), onBack = onBack)
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
                    modifier = Modifier.fillMaxWidth().height(200.dp)
                )
            }

            SectionCard(L10n.t("速度"), icon = Icons.Default.Speed) {
                InfoRow(L10n.t("当前倍率"), "%.2fx".format(Locale.US, speed))
                InfoRow(L10n.t("源时长"), formatClock(media?.durationMs ?: 0L))
                InfoRow(L10n.t("预计输出时长"), formatClock(estimatedMs))
                Spacer(Modifier.height(10.dp))
                Slider(
                    value = speed,
                    onValueChange = { speed = it.coerceIn(0.25f, 4f) },
                    valueRange = 0.25f..4f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    L10n.t("滑条可在 0.25 到 4 倍之间自由调节，下面提供常用档位快速选择。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SpeedPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = kotlin.math.abs(speed - preset.value) < 0.001f,
                            onClick = { speed = preset.value },
                            label = {
                                Text(
                                    when (preset) {
                                        SpeedPreset.X025 -> "0.25x"
                                        SpeedPreset.X05 -> "0.5x"
                                        SpeedPreset.X15 -> "1.5x"
                                        SpeedPreset.X2 -> "2x"
                                        SpeedPreset.X4 -> "4x"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    L10n.t("视频用 setpts 缩放时间轴，音频用 atempo 同步变速；超过 2 倍时会自动串联两级 atempo，音画始终对齐。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    L10n.t("1.00x 为原速；慢放会成倍增加输出时长与体积，快放则相反。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            CommandPreview(Commands.preview(args))
            ActionButton(
                text = L10n.t("开始变速"),
                enabled = media != null && args.isNotEmpty(),
                onClick = {
                    FFmpegEngine.submit(L10n.t("视频变速"), args, output, estimatedMs)
                    output = nextOutput("mp4", "speed")
                    onSubmitted()
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
