package com.framebetween.app.ui.library

import com.framebetween.app.core.L10n

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.FsEntry
import com.framebetween.app.core.FsEntrySort
import com.framebetween.app.core.FsSortKey
import com.framebetween.app.core.FileSystem
import com.framebetween.app.core.LayoutMode
import com.framebetween.app.core.LibraryItem
import com.framebetween.app.util.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「全部文件」文件管理器：按目录层级直接列出手机存储里的真实目录与文件。
 * 不做媒体扫描、不查 MediaStore、不递归、不套用媒体库过滤规则。
 */
@Composable
fun AllFilesBrowser(
    modifier: Modifier = Modifier,
    entries: List<FsEntry>,
    currentDir: String,
    layout: LayoutMode,
    gridCount: Int,
    query: String,
    sortKey: FsSortKey = FsSortKey.NAME,
    descending: Boolean = false,
    directoriesFirst: Boolean = true,
    onOpenDir: (String) -> Unit,
    onUp: () -> Unit,
    onHome: () -> Unit,
    onFile: (LibraryItem) -> Unit,
    onFileLongClick: (LibraryItem) -> Unit
) {
    val shown = remember(entries, query, sortKey, descending, directoriesFirst) {
        val base = if (query.isBlank()) entries else entries.filter {
            it.name.contains(query, ignoreCase = true)
        }
        FsEntrySort.sort(base, sortKey, descending, directoriesFirst)
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = L10n.t("返回上级"))
            }
            Text(
                currentDir,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onHome) { Text(L10n.t("本机存储")) }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) L10n.t("目录为空或无法读取") else L10n.t("没有匹配的结果"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (layout == LayoutMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridCount),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(shown, key = { it.path }) { entry ->
                    FsTile(
                        entry, LayoutMode.GRID, Modifier,
                        onClick = { openEntry(entry, onOpenDir, onFile) },
                        onLongClick = { if (!entry.isDirectory) onFileLongClick(FileSystem.asLibraryItem(entry)) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shown, key = { it.path }) { entry ->
                    FsTile(
                        entry, LayoutMode.LIST, Modifier.fillMaxWidth(),
                        onClick = { openEntry(entry, onOpenDir, onFile) },
                        onLongClick = { if (!entry.isDirectory) onFileLongClick(FileSystem.asLibraryItem(entry)) }
                    )
                }
            }
        }
    }
}

private fun openEntry(
    entry: FsEntry,
    onOpenDir: (String) -> Unit,
    onFile: (LibraryItem) -> Unit
) {
    if (entry.isDirectory) onOpenDir(entry.path) else onFile(FileSystem.asLibraryItem(entry))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FsTile(
    entry: FsEntry,
    layout: LayoutMode,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val mime = com.framebetween.app.util.mimeOf(entry.name)
    val icon: ImageVector = when {
        entry.isDirectory -> Icons.Default.Folder
        mime.startsWith("video") -> Icons.Default.Movie
        mime.startsWith("audio") -> Icons.Default.MusicNote
        mime.startsWith("image") -> Icons.Default.Image
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    val shape = RoundedCornerShape(8.dp)
    val subtitle = if (entry.isDirectory) {
        L10n.t("文件夹")
    } else {
        val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.dateMs))
        "${formatBytes(entry.sizeBytes)} · $date"
    }
    Card(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        if (layout == LayoutMode.GRID) {
            Column(Modifier.padding(8.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1.5f).clip(shape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.name,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(84.dp).aspectRatio(1.5f).clip(shape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        subtitle,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
