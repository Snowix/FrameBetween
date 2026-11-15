package com.framebetween.app.ui.common
import com.framebetween.app.core.L10n

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.AppState
import com.framebetween.app.core.MediaInfo
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.util.formatBytes
import com.framebetween.app.util.formatClock
import com.framebetween.app.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 选择（可多选）音视频文件并追加到「当前文件」列表。 */
@Composable
fun rememberMediaPicker(onPicked: (MediaInfo) -> Unit = {}): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                uris.mapNotNull { runCatching { MediaRepository.import(it) }.getOrNull() }
            }
            if (list.isEmpty()) {
                AppState.notify(L10n.t("导入失败：无法读取该文件"))
            } else {
                AppState.addCurrent(list)
                onPicked(list.first())
                AppState.notify(L10n.t("已添加 %1\$s 个文件到当前列表", list.size))
            }
        }
    }
    return { launcher.launch(arrayOf("video/*", "audio/*")) }
}

/** 单输入工具页专用：替换当前列表为刚选中的单个文件。 */
@Composable
fun rememberSinglePicker(onPicked: (MediaInfo) -> Unit = {}): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching { MediaRepository.import(uri) }.getOrNull()
            }
            if (info == null) {
                AppState.notify(L10n.t("导入失败：无法读取该文件"))
            } else {
                AppState.setCurrent(info)
                onPicked(info)
            }
        }
    }
    return { launcher.launch(arrayOf("video/*", "audio/*")) }
}

@Composable
fun rememberMultiPicker(
    mimeTypes: Array<String> = arrayOf("video/*", "audio/*"),
    onPicked: (List<MediaInfo>) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                uris.mapNotNull { runCatching { MediaRepository.import(it) }.getOrNull() }
            }
            if (list.isEmpty()) AppState.notify(L10n.t("导入失败")) else onPicked(list)
        }
    }
    return { launcher.launch(mimeTypes) }
}

private fun kbpsText(kbps: Long): String =
    if (kbps >= 1000) "%.2f Mbps".format(kbps / 1000.0) else "$kbps kbps"

/**
 * 当前文件卡片：默认多选，逐项显示并可单独移除；
 * single = true 时供「音频剪切」这类只允许一个输入的工具页使用。
 */
@Composable
fun CurrentMediaCard(
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    single: Boolean = false
) {
    val files by AppState.currentFiles.collectAsState()
    val media by AppState.current.collectAsState()
    var showDetails by remember { mutableStateOf(false) }
    val shown = if (single) listOfNotNull(media) else files

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (shown.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(L10n.t("尚未选择文件"), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    L10n.t("可通过文件选择、其他应用「打开方式」或分享导入"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onPick) { Text(L10n.t("添加文件")) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        L10n.t("当前文件（%1\$s）", shown.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { AppState.clearCurrent() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = L10n.t("清空列表"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                shown.forEach { info ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                info.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                "${formatBytes(info.sizeBytes)} · ${formatClock(info.durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = { AppState.removeCurrent(info.path) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = L10n.t("移除"),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                val primary = shown.first()
                Spacer(Modifier.height(6.dp))
                val detail = buildString {
                    append(formatBytes(primary.sizeBytes))
                    append(" · ")
                    append(formatClock(primary.durationMs))
                    primary.video?.let { append(" · ${it.width}×${it.height}") }
                    append(" · ")
                    append(kbpsText(primary.overallBitrateKbps))
                }
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                val streamDetail = buildString {
                    primary.video?.let {
                        if (it.fps > 0) append("${(it.fps * 100).toLong() / 100.0} fps")
                        if (it.bitrateKbps > 0) append(L10n.t(" · 视频 %1\$s", kbpsText(it.bitrateKbps)))
                        append(" · ${it.codec}")
                    }
                    primary.audio?.let {
                        if (isNotEmpty()) append("  |  ")
                        if (it.sampleRate > 0) append("${it.sampleRate / 1000.0} kHz")
                        append(L10n.t(" · %1\$s 声道", it.channels))
                        if (it.bitrateKbps > 0) append(L10n.t(" · 音频 %1\$s", kbpsText(it.bitrateKbps)))
                        append(" · ${it.codec}")
                    }
                    if (isEmpty()) append(primary.formatName)
                }
                Text(
                    streamDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPick, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (single) L10n.t("更换文件") else L10n.t("添加文件"))
                    }
                    OutlinedButton(onClick = { showDetails = true }, modifier = Modifier.weight(1f)) {
                        Text(L10n.t("查看详情"))
                    }
                }
            }
        }
    }

    if (showDetails && media != null) {
        MediaDetailsDialog(info = media!!, onDismiss = { showDetails = false })
    }
}

@Composable
private fun MediaDetailsDialog(info: MediaInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.t("媒体详情")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InfoRow(L10n.t("文件名"), info.displayName)
                InfoRow(L10n.t("容器格式"), info.formatName)
                InfoRow(L10n.t("文件大小"), formatBytes(info.sizeBytes))
                InfoRow(L10n.t("时长"), formatDuration(info.durationMs))
                InfoRow(L10n.t("总码率"), kbpsText(info.overallBitrateKbps))
                InfoRow(L10n.t("本地路径"), info.path)
                info.video?.let { v ->
                    Spacer(Modifier.height(8.dp))
                    Text(L10n.t("视频流"), style = MaterialTheme.typography.titleSmall)
                    InfoRow(L10n.t("编码器"), v.codec)
                    if (v.profile.isNotBlank()) InfoRow("Profile", v.profile)
                    if (v.pixFmt.isNotBlank()) InfoRow(L10n.t("像素格式"), v.pixFmt)
                    InfoRow(L10n.t("分辨率"), "${v.width}×${v.height}")
                    if (v.aspectRatio.isNotBlank()) InfoRow(L10n.t("显示宽高比"), v.aspectRatio)
                    InfoRow(L10n.t("帧率"), if (v.fps > 0) "${(v.fps * 100).toLong() / 100.0} fps" else L10n.t("未知"))
                    InfoRow(L10n.t("视频码率"), if (v.bitrateKbps > 0) kbpsText(v.bitrateKbps) else L10n.t("未标注"))
                    InfoRow(L10n.t("帧数"), if (v.nbFrames > 0) v.nbFrames.toString() else info.totalFrames.toString())
                }
                info.audio?.let { a ->
                    Spacer(Modifier.height(8.dp))
                    Text(L10n.t("音频流"), style = MaterialTheme.typography.titleSmall)
                    InfoRow(L10n.t("编码器"), a.codec)
                    InfoRow(L10n.t("采样率"), if (a.sampleRate > 0) "${a.sampleRate} Hz" else L10n.t("未知"))
                    InfoRow(L10n.t("声道数"), a.channels.toString())
                    if (a.channelLayout.isNotBlank()) InfoRow(L10n.t("声道布局"), a.channelLayout)
                    InfoRow(L10n.t("音频码率"), if (a.bitrateKbps > 0) kbpsText(a.bitrateKbps) else L10n.t("未标注"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L10n.t("关闭")) } }
    )
}
