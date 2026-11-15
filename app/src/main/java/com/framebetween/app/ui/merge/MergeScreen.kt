package com.framebetween.app.ui.merge
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.framebetween.app.core.Commands
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.core.MergeConfig
import com.framebetween.app.core.MergeDraft
import com.framebetween.app.ui.common.ActionButton
import com.framebetween.app.ui.common.CommandPreview
import com.framebetween.app.ui.common.InfoRow
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.ui.common.StringDropdown
import com.framebetween.app.ui.common.rememberMultiPicker
import com.framebetween.app.ui.common.nextOutput
import com.framebetween.app.ui.common.rememberOutput
import com.framebetween.app.util.formatBytes
import com.framebetween.app.util.formatClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MergeScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val draft by MergeDraft.paths.collectAsState()
    val files by AppState.mergeFiles.collectAsState()
    var reencode by rememberSaveable { mutableStateOf(false) }
    var container by rememberSaveable { mutableStateOf("mp4") }
    var output by rememberOutput(container, "merge", files.firstOrNull()?.displayName)
    var concatFile by rememberSaveable { mutableStateOf("") }

    val multiPick = rememberMultiPicker { added -> AppState.setMergeFiles(files + added) }

    LaunchedEffect(draft) {
        if (draft.isNotEmpty()) {
            val loaded = withContext(Dispatchers.IO) {
                draft.mapNotNull { path ->
                    MediaRepository.info(path)
                        ?: runCatching { MediaRepository.probe(path) }.getOrNull()
                }
            }
            AppState.setMergeFiles(files + loaded)
            MergeDraft.clear()
        }
    }

    LaunchedEffect(files) {
        concatFile = if (files.size >= 2) {
            withContext(Dispatchers.IO) {
                MediaRepository.newTempText("concat", Commands.concatList(files.map { it.path }))
            }
        } else ""
    }

    // 容器 / 首个输入变化时由 rememberOutput 统一重算输出名，无需额外的副作用

    val args = if (files.size >= 2 && concatFile.isNotEmpty()) {
        Commands.merge(concatFile, output, MergeConfig(reencode), files.firstOrNull()?.videoKbps ?: 0L)
    } else emptyList()

    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in files.indices) return
        val next = files.toMutableList()
        val item = next.removeAt(index)
        next.add(target, item)
        AppState.setMergeFiles(next)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(L10n.t("视频合并"), L10n.t("按顺序拼接 · 支持多文件分享导入"), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(L10n.t("文件列表（%1\$s）", files.size), icon = Icons.AutoMirrored.Filled.MergeType) {
                Text(
                    L10n.t("在其他应用多选视频后点击「分享 → 帧间」可直接填入列表。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = multiPick, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Text(L10n.t(" 添加多个文件"))
                }
                Spacer(Modifier.height(10.dp))
                files.forEachIndexed { index, info ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(info.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    "${formatClock(info.durationMs)} · ${formatBytes(info.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { move(index, -1) }, enabled = index > 0) {
                                Icon(Icons.Default.ArrowUpward, L10n.t("上移"))
                            }
                            IconButton(onClick = { move(index, 1) }, enabled = index < files.lastIndex) {
                                Icon(Icons.Default.ArrowDownward, L10n.t("下移"))
                            }
                            IconButton(onClick = {
                                AppState.setMergeFiles(files.toMutableList().also { it.removeAt(index) })
                            }) {
                                Icon(Icons.Default.Delete, L10n.t("移除"))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (files.size < 2) {
                    Text(
                        L10n.t("至少选择 2 个文件才能合并。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            SectionCard(L10n.t("合并方式")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L10n.t("统一重编码（兼容不同参数）"), modifier = Modifier.weight(1f))
                    Switch(checked = reencode, onCheckedChange = { reencode = it })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (reencode) {
                        L10n.t("重编码为 H.264 / AAC，兼容不同分辨率与码率，速度较慢但最稳妥。")
                    } else {
                        L10n.t("直接复制流，速度最快；要求各片段编码格式、分辨率与参数一致，否则可能出错。")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                StringDropdown(
                    label = L10n.t("输出封装"),
                    options = listOf("mp4", "mkv", "mov"),
                    selected = container,
                    onSelect = { container = it },
                    optionLabel = { it.uppercase() }
                )
                Spacer(Modifier.height(10.dp))
                InfoRow(L10n.t("总时长"), formatClock(files.sumOf { it.durationMs }))
            }

            CommandPreview(Commands.preview(args))
            ActionButton(
                text = L10n.t("开始合并"),
                enabled = files.size >= 2 && args.isNotEmpty(),
                onClick = {
                    val duration = files.sumOf { it.durationMs }
                    FFmpegEngine.submit(L10n.t("视频合并"), args, output, duration)
                    output = nextOutput(container, "merge", files.firstOrNull()?.displayName)
                    onSubmitted()
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
