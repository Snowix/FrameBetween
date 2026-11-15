package com.framebetween.app.ui.audiocut
import com.framebetween.app.core.L10n

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.AppState
import com.framebetween.app.core.AudioCutConfig
import com.framebetween.app.core.AudioOutputConfig
import com.framebetween.app.core.AudioWaveform
import com.framebetween.app.core.Commands
import com.framebetween.app.core.Estimate
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.ui.common.ActionButton
import com.framebetween.app.ui.common.AudioOutputFields
import com.framebetween.app.ui.common.AudioPreview
import com.framebetween.app.ui.common.CommandPreview
import com.framebetween.app.ui.common.CurrentMediaCard
import com.framebetween.app.ui.common.InfoRow
import com.framebetween.app.ui.common.Label
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.ui.common.SizeEstimateCard
import com.framebetween.app.ui.common.nextOutput
import com.framebetween.app.ui.common.rememberOutput
import com.framebetween.app.ui.common.rememberSinglePicker
import com.framebetween.app.util.formatClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun AudioCutScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val media by AppState.current.collectAsState()
    val pick = rememberSinglePicker()
    val durationMs = media?.durationMs?.coerceAtLeast(1L) ?: 1L
    val totalSec = durationMs / 1000f

    var initializedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var startSec by rememberSaveable { mutableStateOf(0f) }
    var endSec by rememberSaveable { mutableStateOf(0f) }
    var startText by rememberSaveable { mutableStateOf("0.00") }
    var endText by rememberSaveable { mutableStateOf("0.00") }
    var speed by rememberSaveable { mutableStateOf(1f) }
    var format by rememberSaveable { mutableStateOf("mp3") }
    var sampleRate by rememberSaveable { mutableStateOf(44100) }
    var bitrate by rememberSaveable { mutableStateOf(192) }
    var output by rememberOutput(format, "cut")
    var waveBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun syncText() {
        startText = "%.2f".format(Locale.US, startSec)
        endText = "%.2f".format(Locale.US, endSec)
    }

    LaunchedEffect(media?.path) {
        if (initializedPath == media?.path) return@LaunchedEffect
        initializedPath = media?.path
        val total = (media?.durationMs?.coerceAtLeast(1L) ?: 1L) / 1000f
        startSec = 0f
        endSec = total
        startText = "0.00"
        endText = "%.2f".format(Locale.US, total)
        waveBitmap = withContext(Dispatchers.IO) {
            val png = media?.path?.let { runCatching { AudioWaveform.render(it) }.getOrNull() }
            png?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
        }
    }

    // 输出名由 rememberOutput 绑定「当前文件 + 输出格式」，这里不再用副作用重算

    val startMs = (startSec * 1000).toLong().coerceIn(0L, durationMs)
    val endMs = (endSec * 1000).toLong().coerceIn(startMs, durationMs)
    val selectedMs = (endMs - startMs).coerceAtLeast(0L)
    val outputMs = (selectedMs / speed.coerceAtLeast(0.25f)).toLong()
    val audioOut = AudioOutputConfig(format, sampleRate, bitrate)
    val config = AudioCutConfig(startMs, endMs, speed, audioOut)
    val args = media?.let { Commands.audioCut(it.path, output, config) } ?: emptyList()
    val estimated = Estimate.audioBytes(audioOut, outputMs)

    val startFraction = (startSec / totalSec).coerceIn(0f, 1f)
    val middleFraction = ((endSec - startSec) / totalSec).coerceIn(0f, 1f - startFraction)
    val tailFraction = (1f - startFraction - middleFraction)

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(L10n.t("音频剪切"), L10n.t("波形截取 · 变速试听 · 多格式导出"), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Label(L10n.t("输入音频（仅支持一个文件）"))
            CurrentMediaCard(onPick = pick, single = true)

            SectionCard(L10n.t("波形与截取区间"), icon = Icons.Default.GraphicEq) {
                if (media == null) {
                    Text(
                        L10n.t("添加音频后会生成整段波形，可直接拖动滑块或输入时间截取。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(150.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            val bmp = waveBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = L10n.t("音频波形"),
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                )
                                Row(Modifier.fillMaxSize()) {
                                    Box(
                                        Modifier.weight(startFraction.coerceAtLeast(0.001f))
                                            .fillMaxHeight()
                                            .background(Color.Black.copy(alpha = 0.45f))
                                    )
                                    Box(
                                        Modifier.weight(middleFraction.coerceAtLeast(0.001f))
                                            .fillMaxHeight()
                                    )
                                    Box(
                                        Modifier.weight(tailFraction.coerceAtLeast(0.001f))
                                            .fillMaxHeight()
                                            .background(Color.Black.copy(alpha = 0.45f))
                                    )
                                }
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        L10n.t("正在生成波形…"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    RangeSlider(
                        value = startSec.coerceAtMost(endSec)..endSec.coerceAtLeast(startSec),
                        onValueChange = { range ->
                            startSec = range.start.coerceIn(0f, totalSec)
                            endSec = range.endInclusive.coerceIn(startSec, totalSec)
                            syncText()
                        },
                        valueRange = 0f..totalSec,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondsField(
                            L10n.t("起点秒"), startText,
                            {
                                startText = it
                                val v = it.toFloatOrNull()
                                if (v != null) startSec = v.coerceIn(0f, endSec)
                            },
                            Modifier.weight(1f)
                        )
                        SecondsField(
                            L10n.t("终点秒"), endText,
                            {
                                endText = it
                                val v = it.toFloatOrNull()
                                if (v != null) endSec = v.coerceIn(startSec, totalSec)
                            },
                            Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    InfoRow(L10n.t("选中时长"), formatClock(selectedMs))
                    InfoRow(L10n.t("输出时长"), formatClock(outputMs))
                }
            }

            SectionCard(L10n.t("速度与试听"), icon = Icons.Default.Speed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (media != null) AudioPreview(media!!.path, speed, startMs, endMs)
                    Text(
                        L10n.t("播放速度：%1\$s×", "%.2f".format(Locale.US, speed)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    L10n.t("试听会同步应用当前速度；输出文件也会按同样速度处理。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = speed.coerceIn(0.5f, 4f),
                    onValueChange = { speed = it },
                    valueRange = 0.5f..4f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    L10n.t("速度范围 0.5× – 4.0×；超过 2× 时命令会串联两级 atempo。"),
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

            SizeEstimateCard(
                original = media?.sizeBytes ?: 0L,
                estimated = estimated,
                rough = false
            )
            CommandPreview(Commands.preview(args))
            ActionButton(
                text = L10n.t("开始剪切"),
                enabled = media != null && selectedMs > 0 && args.isNotEmpty(),
                icon = Icons.Default.ContentCut,
                onClick = {
                    FFmpegEngine.submit(L10n.t("音频剪切"), args, output, selectedMs)
                    output = nextOutput(format, "cut")
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
        trailingIcon = { Text("s") },
        singleLine = true,
        modifier = modifier
    )
}
