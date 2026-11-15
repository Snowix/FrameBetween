package com.framebetween.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.framebetween.app.core.AppLog
import com.framebetween.app.core.AppState
import com.framebetween.app.core.L10n
import com.framebetween.app.core.LogEntry
import com.framebetween.app.core.LogLevel
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.core.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun levelOf(name: String): LogLevel =
    runCatching { LogLevel.valueOf(name) }.getOrDefault(LogLevel.VERBOSE)

private fun logFileName(): String =
    "FrameBetween-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())

private fun shareFile(context: Context, level: LogLevel): File {
    val dir = File(context.cacheDir, "logs").apply { mkdirs() }
    return File(dir, logFileName() + ".txt").apply {
        writeText(AppLog.text(level), Charsets.UTF_8)
    }
}

private fun shareLog(context: Context, level: LogLevel) {
    runCatching {
        val uri = MediaRepository.shareUri(shareFile(context, level).absolutePath)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, L10n.t("分享日志"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { AppState.notify(L10n.t("分享失败，请重试")) }
}

private fun exportLog(context: Context, tree: Uri?, level: LogLevel): Boolean {
    if (tree == null) return false
    return runCatching {
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return false
        val doc = dir.createFile("text/plain", logFileName()) ?: return false
        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            out.write(AppLog.text(level).toByteArray(Charsets.UTF_8))
        } ?: return false
        true
    }.getOrDefault(false)
}

/**
 * 【详细日志】面板：实时日志按级别筛选查看；导出日志可选类别、目标文件夹，
 * 并执行分享 / 导出 / 清空。日志始终只在本机生成与保存。
 */
@Composable
fun LogPanel() {
    val context = LocalContext.current
    val logEnabled by Settings.logEnabled.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var liveLevelName by rememberSaveable { mutableStateOf(LogLevel.VERBOSE.name) }
    var exportLevelName by rememberSaveable { mutableStateOf(LogLevel.VERBOSE.name) }
    var treeUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(emptyList<LogEntry>()) }

    val liveLevel = levelOf(liveLevelName)
    val exportLevel = levelOf(exportLevelName)
    val treeUri = treeUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }

    val levels = listOf(
        LogLevel.VERBOSE to L10n.t("详细"),
        LogLevel.DEBUG to L10n.t("调试"),
        LogLevel.INFO to L10n.t("信息"),
        LogLevel.WARN to L10n.t("警告"),
        LogLevel.ERROR to L10n.t("错误")
    )

    LaunchedEffect(tab, liveLevel, logEnabled) {
        if (tab != 0 || !logEnabled) return@LaunchedEffect
        while (isActive) {
            entries = AppLog.entries(liveLevel)
            delay(1200)
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            treeUriString = uri.toString()
        }
    }

    // 日志默认不开启：开关关闭时既不写内存也不落盘，下面的查看 / 导出 / 清空都不可用。
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(L10n.t("开启日志"), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    L10n.t("默认关闭。开启后才会记录运行日志到本机，可实时查看与导出。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = logEnabled, onCheckedChange = { Settings.setLogEnabled(it) })
        }
    }

    Spacer(Modifier.height(12.dp))

    if (!logEnabled) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Text(
                L10n.t("日志未开启。打开上方开关后，应用才开始记录任务、播放与失败信息；日志仅保存在本机，不会上传。"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp)
            )
        }
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = tab == 0,
            onClick = { tab = 0 },
            label = { Text(L10n.t("实时日志")) },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = tab == 1,
            onClick = { tab = 1 },
            label = { Text(L10n.t("导出日志")) },
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(Modifier.height(10.dp))
    Text(L10n.t("日志类别"), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        levels.chunked(3).forEach { group ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.forEach { (item, label) ->
                    FilterChip(
                        selected = (if (tab == 0) liveLevel else exportLevel) == item,
                        onClick = {
                            if (tab == 0) liveLevelName = item.name else exportLevelName = item.name
                        },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    if (tab == 0) {
        Card(
            Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                if (entries.isEmpty()) {
                    Text(
                        L10n.t("暂无日志，使用功能后会自动记录。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    entries.forEach { entry ->
                        val color = when (entry.level) {
                            LogLevel.ERROR -> MaterialTheme.colorScheme.error
                            LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            entry.format(),
                            color = color,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            L10n.t("每 1.2 秒自动刷新；日志仅保存在本机，不会上传。"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        ListItem(
            headlineContent = { Text(L10n.t("保存文件夹")) },
            supportingContent = {
                Text(
                    treeUriString?.let {
                        L10n.t("已选择：%1\$s", MediaRepository.treeDisplayPath(it))
                    } ?: L10n.t("点击选择日志保存位置"),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            leadingContent = {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            modifier = Modifier.fillMaxWidth()
                .clickable { folderLauncher.launch(null) }
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { shareLog(context, exportLevel) },
                modifier = Modifier.weight(1f)
            ) { Text(L10n.t("分享")) }
            Button(
                onClick = {
                    if (treeUri == null) {
                        AppState.notify(L10n.t("请先选择保存文件夹"))
                    } else {
                        AppState.notify(
                            if (exportLog(context, treeUri, exportLevel)) {
                                L10n.t("日志已导出")
                            } else {
                                L10n.t("导出失败，请重试")
                            }
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(L10n.t("导出")) }
            OutlinedButton(
                onClick = { showClearConfirm = true },
                modifier = Modifier.weight(1f)
            ) { Text(L10n.t("清空")) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            L10n.t("导出内容按上方所选类别筛选；分享与清空同样只作用于本机日志。"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(L10n.t("清空日志")) },
            text = { Text(L10n.t("将删除本机保存的全部日志，此操作不可撤销。")) },
            confirmButton = {
                TextButton(onClick = {
                    AppLog.clear()
                    entries = emptyList()
                    showClearConfirm = false
                    AppState.notify(L10n.t("日志已清空"))
                }) { Text(L10n.t("清空")) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(L10n.t("取消"))
                }
            }
        )
    }
}
