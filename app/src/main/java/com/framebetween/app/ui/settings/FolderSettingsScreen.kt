package com.framebetween.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.framebetween.app.core.AppState
import com.framebetween.app.core.L10n
import com.framebetween.app.core.LibrarySettings
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.util.formatBytes

private enum class RuleKind { BLACK, WHITE }

@Composable
fun FolderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val includeNoMedia by LibrarySettings.includeNoMedia.collectAsState()
    val includeHidden by LibrarySettings.includeHidden.collectAsState()
    val shortSeconds by LibrarySettings.filterShortSeconds.collectAsState()
    val minSizeTenths by LibrarySettings.minSizeTenthsMb.collectAsState()
    val blacklist by LibrarySettings.blacklist.collectAsState()
    val whitelist by LibrarySettings.whitelist.collectAsState()
    val scanPaths by LibrarySettings.scanPaths.collectAsState()

    var editing by remember { mutableStateOf<RuleKind?>(null) }
    var pickKind by remember { mutableStateOf<RuleKind?>(null) }
    var cacheBytes by remember { mutableStateOf(0L) }
    // 拖动时只更新草稿，松手才落盘并重新扫描，避免滑块每动一格就重扫一次媒体库
    var shortDraft by remember(shortSeconds) { mutableStateOf(shortSeconds.toFloat()) }
    var sizeDraft by remember(minSizeTenths) { mutableStateOf(minSizeTenths.toFloat()) }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            LibrarySettings.addScanPath(uri.toString())
            AppState.notify(L10n.t("已添加扫描路径，媒体库将重新扫描"))
        }
    }

    /** 黑白名单直接选文件夹，不再要求用户手输路径。 */
    val ruleFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val kind = pickKind
        pickKind = null
        if (uri != null && kind != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            val label = MediaRepository.treeDisplayPath(uri.toString())
                .ifBlank { DocumentFile.fromTreeUri(context, uri)?.name.orEmpty() }
                .ifBlank { uri.lastPathSegment.orEmpty() }
            if (label.isNotBlank()) {
                if (kind == RuleKind.BLACK) {
                    LibrarySettings.setBlacklist(LibrarySettings.blacklist.value + label)
                } else {
                    LibrarySettings.setWhitelist(LibrarySettings.whitelist.value + label)
                }
                AppState.notify(L10n.t("已添加文件夹：%1\$s", label))
            }
        }
    }

    fun refreshCacheSize() {
        var total = 0L
        LibrarySettings.thumbDir().walkTopDown().forEach { if (it.isFile) total += it.length() }
        cacheBytes = total
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = L10n.t("文件夹设置"),
            subtitle = L10n.t("内容发现、目录规则与媒体库维护"),
            onBack = onBack
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(L10n.t("内容发现与过滤"), icon = Icons.Default.Search) {
                SwitchRow(
                    L10n.t("包含 .nomedia 文件夹"),
                    L10n.t("递归扫描含 .nomedia 标记的目录树（需所有文件访问权限或已添加扫描路径）"),
                    includeNoMedia,
                    LibrarySettings::setIncludeNoMedia
                )
                SwitchRow(
                    L10n.t("包含隐藏文件夹"),
                    L10n.t("递归显示名称以 . 开头的目录与文件"),
                    includeHidden,
                    LibrarySettings::setIncludeHidden
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    L10n.t("过滤极短视频：%1\$s", LibrarySettings.shortLabel(shortDraft.toInt())),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = shortDraft,
                    onValueChange = { shortDraft = it },
                    onValueChangeFinished = {
                        LibrarySettings.setFilterShortSeconds(shortDraft.toInt())
                    },
                    valueRange = 0f..LibrarySettings.MAX_SHORT_SECONDS.toFloat(),
                    steps = LibrarySettings.MAX_SHORT_SECONDS - 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    L10n.t("0 秒表示不过滤；隐藏时长不超过该值的视频，最长 60 秒。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    L10n.t("过滤小体积文件：%1\$s", LibrarySettings.minSizeLabel(sizeDraft.toInt())),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = sizeDraft,
                    onValueChange = { sizeDraft = it },
                    onValueChangeFinished = {
                        LibrarySettings.setMinSizeTenthsMb(sizeDraft.toInt())
                    },
                    valueRange = 0f..LibrarySettings.MAX_SIZE_TENTHS_MB.toFloat(),
                    steps = LibrarySettings.MAX_SIZE_TENTHS_MB - 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    L10n.t("0 MB 表示不过滤；隐藏体积小于该值的媒体文件，最大 5 MB。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    L10n.t("说明：受 Android 分区存储限制，扫描 .nomedia 与隐藏目录只覆盖常见媒体目录的浅层；如需扫描任意位置，请在下方添加扫描路径。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(L10n.t("目录规则与授权"), icon = Icons.Default.Folder) {
                RuleRow(
                    L10n.t("文件夹黑名单"),
                    L10n.t("%1\$s 个文件夹 · 命中即不显示", blacklist.size),
                    onClick = { editing = RuleKind.BLACK }
                )
                RuleRow(
                    L10n.t("文件夹白名单"),
                    if (whitelist.isEmpty()) L10n.t("为空时显示所有文件夹")
                    else L10n.t("%1\$s 个文件夹 · 仅显示命中的目录", whitelist.size),
                    onClick = { editing = RuleKind.WHITE }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { treeLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(L10n.t("添加扫描路径"))
                }
                Text(
                    L10n.t("通过系统文件夹选择器授权目录，媒体库会额外扫描该目录中的音视频文件。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                scanPaths.forEach { raw ->
                    val label = runCatching {
                        DocumentFile.fromTreeUri(context, Uri.parse(raw))?.name
                    }.getOrNull() ?: Uri.parse(raw).lastPathSegment.orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { LibrarySettings.setScanPaths(scanPaths - raw) }) {
                            Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                        }
                    }
                }
            }

            SectionCard(L10n.t("媒体库维护"), icon = Icons.Default.Refresh) {
                Text(
                    L10n.t("缩略图 / 元数据缓存占用：%1\$s", formatBytes(cacheBytes)),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            LibrarySettings.rescan()
                            refreshCacheSize()
                            AppState.notify(L10n.t("已重新扫描媒体库"))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(L10n.t("重新扫描")) }
                    OutlinedButton(
                        onClick = {
                            val freed = LibrarySettings.clearMetadataCache()
                            refreshCacheSize()
                            AppState.notify(L10n.t("已清空元数据缓存，释放 %1\$s", formatBytes(freed)))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(L10n.t("清空缓存")) }
                }
                Text(
                    L10n.t("重新扫描会重新读取文件清单并校验已删除文件；清空缓存只删除缩略图与元数据，不会删除任何媒体文件或输出产物。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    editing?.let { kind ->
        val current = if (kind == RuleKind.BLACK) blacklist else whitelist
        val applyList: (Set<String>) -> Unit = if (kind == RuleKind.BLACK) {
            LibrarySettings::setBlacklist
        } else {
            LibrarySettings::setWhitelist
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = {
                Text(
                    if (kind == RuleKind.BLACK) L10n.t("文件夹黑名单") else L10n.t("文件夹白名单")
                )
            },
            text = {
                Column {
                    Text(
                        L10n.t("点击下方按钮用系统文件夹选择器添加，匹配时不区分大小写。"),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    current.sorted().forEach { value ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { applyList(current - value) }) {
                                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            pickKind = kind
                            ruleFolderLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(L10n.t("选择文件夹"))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text(L10n.t("完成")) } }
        )
    }

    LaunchedEffect(Unit) { refreshCacheSize() }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RuleRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onClick) { Text(L10n.t("编辑")) }
    }
}
