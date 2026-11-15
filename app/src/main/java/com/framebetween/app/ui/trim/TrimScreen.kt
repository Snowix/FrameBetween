package com.framebetween.app.ui.trim
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
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.framebetween.app.core.Commands
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.FrameDelete
import com.framebetween.app.core.FrameDeleteMode
import com.framebetween.app.core.FrameRanges
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.core.TrimConfig
import com.framebetween.app.core.TrimMode
import com.framebetween.app.ui.common.ActionButton
import com.framebetween.app.ui.common.CommandPreview
import com.framebetween.app.ui.common.CurrentMediaCard
import com.framebetween.app.ui.common.InfoRow
import com.framebetween.app.ui.common.Label
import com.framebetween.app.ui.common.NumberField
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.ui.common.SizeEstimateCard
import com.framebetween.app.ui.common.VideoPreview
import com.framebetween.app.ui.common.nextOutput
import com.framebetween.app.ui.common.rememberMediaPicker
import com.framebetween.app.ui.common.rememberOutput
import com.framebetween.app.util.formatDuration
import com.framebetween.app.util.frameToMs
import com.framebetween.app.util.msToFrame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val media by AppState.current.collectAsState()
    val pick = rememberMediaPicker()

    var startMs by rememberSaveable { mutableStateOf(0L) }
    var endMs by rememberSaveable { mutableStateOf(0L) }
    var mode by rememberSaveable { mutableStateOf(TrimMode.COPY) }
    var startFrame by rememberSaveable { mutableStateOf("0") }
    var endFrame by rememberSaveable { mutableStateOf("") }
    var startTimeText by rememberSaveable { mutableStateOf("0") }
    var endTimeText by rememberSaveable { mutableStateOf("") }
    var output by rememberOutput("mp4", "trim")
    var deleteFramesEnabled by rememberSaveable { mutableStateOf(false) }
    var deleteRangeText by rememberSaveable { mutableStateOf("") }
    var deleteMode by rememberSaveable { mutableStateOf(FrameDeleteMode.RECODE) }

    // 只在「更换文件」时初始化区间；切换导航后恢复状态时不覆盖用户已调好的参数
    var initializedPath by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(media?.path) {
        if (initializedPath == media?.path) return@LaunchedEffect
        initializedPath = media?.path
        val duration = media?.durationMs ?: 0L
        startMs = 0L
        endMs = duration
        startTimeText = "0"
        endTimeText = if (duration > 0) (duration / 1000.0).toString() else ""
        startFrame = "0"
        endFrame = if (duration > 0) msToFrame(duration, media?.video?.fps ?: 0.0).toString() else ""
    }

    val info = media
    val duration = (info?.durationMs ?: 0L).coerceAtLeast(1L)
    val fps = info?.video?.fps ?: 0.0
    val totalFrames = info?.totalFrames ?: 0L
    val selected = (endMs - startMs).coerceAtLeast(0L)

    /** 区间变化时把四个精确输入框一起同步，保证两种输入方式表现一致。 */
    fun syncPreciseFields(fromMs: Long, toMs: Long) {
        startTimeText = (fromMs / 1000.0).toString()
        endTimeText = (toMs / 1000.0).toString()
        if (fps > 0) {
            startFrame = msToFrame(fromMs, fps).toString()
            endFrame = msToFrame(toMs, fps).toString()
        }
    }

    fun updateRange(fromMs: Long, toMs: Long) {
        val from = fromMs.coerceIn(0L, duration)
        val to = toMs.coerceIn(from, duration)
        startMs = from
        endMs = to
    }

    val config = TrimConfig(startMs = startMs, endMs = endMs, mode = mode)
    val deleteRanges = FrameRanges.parse(deleteRangeText)
    /** 码流级模式：把「保留区间」写成 concat demuxer 列表，命令只做 -c copy。 */
    val concatPath = remember(
        info?.path, fps, totalFrames, deleteRangeText, deleteMode, deleteFramesEnabled
    ) {
        if (deleteFramesEnabled && deleteMode == FrameDeleteMode.STREAM_COPY &&
            info != null && fps > 0 && totalFrames > 0 && deleteRanges.isNotEmpty()
        ) {
            val text = FrameDelete.concatList(info.path, deleteRanges, fps, totalFrames)
            if (text.isBlank()) "" else MediaRepository.newTempText("dropframes", text)
        } else ""
    }
    // 「删除帧」启用时是独立的按帧处理，忽略上方时间裁剪区间。
    val args = when {
        deleteFramesEnabled -> info?.let {
            Commands.deleteFrames(
                input = it.path,
                output = output,
                ranges = deleteRanges,
                videoKbps = it.videoKbps,
                fps = fps,
                mode = deleteMode,
                concatListPath = concatPath,
                totalFrames = totalFrames
            )
        } ?: emptyList()
        else -> info?.let { Commands.trim(it.path, output, config, it.videoKbps) } ?: emptyList()
    }
    // 重编码沿用源视频码率、音频同步删除，体积按保留比例线性估算
    val estimated = if (info != null && info.sizeBytes > 0) {
        val ratio = if (deleteFramesEnabled && totalFrames > 0) {
            (1.0 - FrameRanges.removedFrames(deleteRanges).toDouble() / totalFrames)
                .coerceIn(0.0, 1.0)
        } else selected.toDouble() / duration
        (info.sizeBytes * ratio).toLong()
    } else 0L
    val removedMs = if (fps > 0) {
        (FrameRanges.removedFrames(deleteRanges) * 1000.0 / fps).toLong()
    } else 0L
    val taskDuration = if (deleteFramesEnabled) {
        (info?.durationMs ?: 0L) - removedMs
    } else selected

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(L10n.t("时长裁剪"), L10n.t("滑动区间或输入精确帧号"), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Label(L10n.t("输入文件"))
            CurrentMediaCard(onPick = pick)

            if (info != null && info.hasVideo) {
                VideoPreview(
                    path = info.path,
                    startMs = startMs,
                    endMs = endMs,
                    modifier = Modifier.fillMaxWidth().height(210.dp)
                )
            }

            SectionCard(L10n.t("区间选择"), icon = Icons.Default.ContentCut) {
                val sliderStart = startMs.toFloat().coerceIn(0f, duration.toFloat())
                val sliderEnd = endMs.toFloat().coerceIn(sliderStart, duration.toFloat())
                RangeSlider(
                    value = sliderStart..sliderEnd,
                    onValueChange = { range ->
                        updateRange(range.start.toLong(), range.endInclusive.toLong())
                        // 滑块移动时，下方「精确输入」的秒数与帧号同步跟着变化
                        syncPreciseFields(startMs, endMs)
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                InfoRow(L10n.t("起点"), formatDuration(startMs))
                InfoRow(L10n.t("终点"), formatDuration(endMs))
                InfoRow(L10n.t("输出时长"), formatDuration(selected))
                if (fps > 0) {
                    InfoRow(L10n.t("对应帧范围"), "${msToFrame(startMs, fps)} → ${msToFrame(endMs, fps)}")
                    InfoRow(L10n.t("源帧率 / 总帧数"), "${(fps * 100).toLong() / 100.0} fps / $totalFrames")
                }
            }

            SectionCard(L10n.t("精确输入")) {
                Label(L10n.t("按帧定位（自动按帧率换算）"))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        label = L10n.t("起始帧"),
                        value = startFrame,
                        onValue = { value ->
                            startFrame = value
                            val from = value.toLongOrNull()
                            val to = endFrame.toLongOrNull()
                            if (fps > 0 && from != null && to != null && to > from) {
                                updateRange(frameToMs(from, fps), frameToMs(to, fps))
                                startTimeText = (startMs / 1000.0).toString()
                                endTimeText = (endMs / 1000.0).toString()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = L10n.t("结束帧"),
                        value = endFrame,
                        onValue = { value ->
                            endFrame = value
                            val from = startFrame.toLongOrNull()
                            val to = value.toLongOrNull()
                            if (fps > 0 && from != null && to != null && to > from) {
                                updateRange(frameToMs(from, fps), frameToMs(to, fps))
                                startTimeText = (startMs / 1000.0).toString()
                                endTimeText = (endMs / 1000.0).toString()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    text = L10n.t("按帧应用"),
                    enabled = fps > 0,
                    onClick = {
                        val from = startFrame.toLongOrNull()
                        val to = endFrame.toLongOrNull()
                        if (from == null || to == null || to <= from) {
                            AppState.notify(L10n.t("请填写有效的帧范围"))
                            return@ActionButton
                        }
                        updateRange(frameToMs(from, fps), frameToMs(to, fps))
                        syncPreciseFields(startMs, endMs)
                        AppState.notify(L10n.t("已按帧定位：%1\$s → %2\$s", formatDuration(startMs), formatDuration(endMs)))
                    }
                )
                Spacer(Modifier.height(12.dp))
                Label(L10n.t("按秒精确输入（支持小数）"))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondsField(L10n.t("起点秒"), startTimeText, { value ->
                        startTimeText = value
                        val from = value.toDoubleOrNull()
                        val to = endTimeText.toDoubleOrNull()
                        if (from != null && to != null && to > from) {
                            updateRange((from * 1000).toLong(), (to * 1000).toLong())
                            if (fps > 0) {
                                startFrame = msToFrame(startMs, fps).toString()
                                endFrame = msToFrame(endMs, fps).toString()
                            }
                        }
                    }, Modifier.weight(1f))
                    SecondsField(L10n.t("终点秒"), endTimeText, { value ->
                        endTimeText = value
                        val from = startTimeText.toDoubleOrNull()
                        val to = value.toDoubleOrNull()
                        if (from != null && to != null && to > from) {
                            updateRange((from * 1000).toLong(), (to * 1000).toLong())
                            if (fps > 0) {
                                startFrame = msToFrame(startMs, fps).toString()
                                endFrame = msToFrame(endMs, fps).toString()
                            }
                        }
                    }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    L10n.t("输入时上方区间滑块会实时跟随；拖动滑块时这里的秒数与帧号也会同步更新。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    text = L10n.t("按秒应用"),
                    onClick = {
                        val from = startTimeText.toDoubleOrNull()
                        val to = endTimeText.toDoubleOrNull()
                        if (from == null || to == null || to <= from) {
                            AppState.notify(L10n.t("请填写有效的时间范围"))
                            return@ActionButton
                        }
                        updateRange((from * 1000).toLong(), (to * 1000).toLong())
                        syncPreciseFields(startMs, endMs)
                    }
                )
                if (fps <= 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        L10n.t("当前文件未提供帧率信息（可能是纯音频），按帧输入不可用。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            SectionCard(L10n.t("删除帧"), icon = Icons.Default.DeleteSweep) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        L10n.t("按帧号删除区间"),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = deleteFramesEnabled,
                        onCheckedChange = { deleteFramesEnabled = it },
                        enabled = info?.hasVideo == true && fps > 0
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = deleteRangeText,
                    onValueChange = { deleteRangeText = it },
                    label = { Text(L10n.t("删除区间（帧号）")) },
                    placeholder = { Text("10-20, 50-60") },
                    enabled = deleteFramesEnabled,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    L10n.t("帧号从 0 开始且含首尾；多段用逗号或换行分隔，重叠区间自动合并。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (deleteFramesEnabled) {
                    Spacer(Modifier.height(10.dp))
                    Label(L10n.t("是否重编码"))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FrameDeleteMode.entries.forEach { item ->
                            FilterChip(
                                selected = deleteMode == item,
                                onClick = { deleteMode = item },
                                label = { Text(item.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        deleteMode.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        L10n.t("已解析：%1\$s", FrameRanges.label(deleteRanges).ifBlank { L10n.t("无有效区间") }),
                        style = MaterialTheme.typography.labelMedium
                    )
                    TextButton(
                        onClick = { deleteRangeText = "$startFrame-$endFrame" },
                        enabled = startFrame.isNotBlank() && endFrame.isNotBlank()
                    ) { Text(L10n.t("填入上方的起始帧 / 结束帧")) }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        L10n.t("两种模式都会同步删除对应时间的音频；精确模式逐帧准确但需重编码，极速模式用码流级拼接不重编码、切点会吸附到关键帧。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        L10n.t("启用后将忽略上方的时间裁剪区间。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (deleteMode == FrameDeleteMode.STREAM_COPY && concatPath.isBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            L10n.t("码流级方案需要总帧数与帧率信息，当前文件不可用，请改用精确模式。"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (info?.hasVideo != true || fps <= 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        L10n.t("删除帧需要视频流与帧率信息，当前文件不可用。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            SectionCard(L10n.t("处理方式（速度 ↔ 精度）")) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrimMode.entries.forEach { item ->
                        FilterChip(
                            selected = mode == item,
                            onClick = { mode = item },
                            label = {
                                Text(
                                    when (item) {
                                        TrimMode.COPY -> L10n.t("极速")
                                        TrimMode.FAST_ENCODE -> L10n.t("均衡")
                                        TrimMode.PRECISE -> L10n.t("精确")
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("${mode.label}：${mode.hint}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    L10n.t("重编码模式沿用源视频码率 %1\$s kbps，音频直接复制，输出不会比原片同长度更大。", info?.videoKbps ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (info != null) {
                SizeEstimateCard(
                    original = info.sizeBytes,
                    estimated = estimated,
                    rough = mode != TrimMode.COPY || deleteFramesEnabled
                )
            }
            CommandPreview(Commands.preview(args))
            ActionButton(
                text = if (deleteFramesEnabled) L10n.t("开始删除帧") else L10n.t("开始裁剪"),
                enabled = info != null && args.isNotEmpty() &&
                    (if (deleteFramesEnabled) deleteRanges.isNotEmpty() else selected > 0),
                onClick = {
                    val source = info ?: return@ActionButton
                    FFmpegEngine.submit(
                        if (deleteFramesEnabled) L10n.t("删除帧") else L10n.t("时长裁剪"),
                        args,
                        output,
                        taskDuration.coerceAtLeast(0L)
                    )
                    output = nextOutput("mp4", if (deleteFramesEnabled) "dropframes" else "trim")
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
