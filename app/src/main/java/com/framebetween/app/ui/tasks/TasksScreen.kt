package com.framebetween.app.ui.tasks
import com.framebetween.app.core.L10n

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.AppState
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.core.TaskItem
import com.framebetween.app.core.TaskStatus
import com.framebetween.app.ui.common.CommandPreview
import com.framebetween.app.ui.common.InfoRow
import com.framebetween.app.ui.common.LogView
import com.framebetween.app.ui.common.ScreenTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TasksScreen() {
    val tasks by FFmpegEngine.tasks.collectAsState()
    val logs by FFmpegEngine.logs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expandedId by remember { mutableStateOf<Long?>(null) }
    val running = tasks.count { it.status == TaskStatus.RUNNING }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = L10n.t("任务队列"),
            subtitle = if (running > 0) L10n.t("%1\$s 个任务运行中", running) else L10n.t("全部任务已结束"),
            actions = {
                IconButton(onClick = { FFmpegEngine.clearFinished() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = L10n.t("清空已完成"))
                }
            }
        )
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(L10n.t("暂无任务"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        lines = logs[task.id].orEmpty(),
                        expanded = expandedId == task.id,
                        onToggle = { expandedId = if (expandedId == task.id) null else task.id },
                        onCancel = { FFmpegEngine.cancel(task.id) },
                        onDelete = { FFmpegEngine.remove(task.id) },
                        onOpen = { openFile(context, task.output) },
                        onShare = { shareFile(context, task.output) },
                        onSave = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    MediaRepository.exportToGallery(
                                        task.output,
                                        MediaRepository.outputMime(task.output)
                                    )
                                }
                                AppState.notify(
                                    if (result != null) L10n.t("已保存到系统媒体库")
                                    else L10n.t("保存失败，可改用「打开 / 分享」导出")
                                )
                            }
                        }
                    )
                }
            }
        }
    }

}

@Composable
private fun TaskCard(
    task: TaskItem,
    lines: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit
) {
    val exists = MediaRepository.outputExists(task.output)
    val alreadyPublished = MediaRepository.isContentRef(task.output)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    when (task.status) {
                        TaskStatus.RUNNING -> L10n.t("运行中")
                        TaskStatus.SUCCESS -> L10n.t("已完成")
                        TaskStatus.FAILED -> L10n.t("失败")
                        TaskStatus.CANCELLED -> L10n.t("已取消")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when (task.status) {
                        TaskStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            if (task.status == TaskStatus.RUNNING) {
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
            }
            InfoRow(
                L10n.t("进度"),
                if (task.status == TaskStatus.RUNNING) {
                    "${(task.progress * 100).toInt()}% · ${task.speed.ifBlank { L10n.t("准备中") }}" +
                        (if (task.estimated) " · " + L10n.t("估算") else "")
                } else task.message
            )
            InfoRow(L10n.t("耗时"), "${task.elapsedMs / 1000}s")
            if (task.output.isNotEmpty()) {
                InfoRow(L10n.t("输出"), MediaRepository.outputName(task.output))
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (task.status == TaskStatus.RUNNING) {
                    TextButton(onClick = onCancel) { Text(L10n.t("取消")) }
                }
                if (task.status == TaskStatus.SUCCESS && exists) {
                    TextButton(onClick = onOpen) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                        Text(L10n.t(" 打开"))
                    }
                    TextButton(onClick = onShare) {
                        Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                        Text(L10n.t(" 分享"))
                    }
                    if (!alreadyPublished) {
                        TextButton(onClick = onSave) {
                            Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                            Text(L10n.t(" 保存"))
                        }
                    }
                }
                TextButton(onClick = onToggle) { Text(if (expanded) L10n.t("收起") else L10n.t("日志")) }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                CommandPreview(task.command)
                Spacer(Modifier.height(8.dp))
                LogView(lines)
            }
        }
    }
}

private fun openFile(context: Context, ref: String) {
    if (!MediaRepository.outputExists(ref)) {
        AppState.notify(L10n.t("输出文件不存在"))
        return
    }
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(MediaRepository.outputUri(ref), MediaRepository.outputMime(ref))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure { AppState.notify(L10n.t("没有可打开该文件的应用")) }
}

private fun shareFile(context: Context, ref: String) {
    if (!MediaRepository.outputExists(ref)) return
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MediaRepository.outputMime(ref)
            putExtra(Intent.EXTRA_STREAM, MediaRepository.outputUri(ref))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, L10n.t("分享文件")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { AppState.notify(L10n.t("分享失败")) }
}
