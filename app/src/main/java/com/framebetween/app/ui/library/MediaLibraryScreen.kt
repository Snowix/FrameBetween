package com.framebetween.app.ui.library
import com.framebetween.app.core.L10n

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framebetween.app.core.AppState
import com.framebetween.app.core.FileSystem
import com.framebetween.app.core.FsEntry
import com.framebetween.app.core.FsSortKey
import com.framebetween.app.core.LayoutMode
import com.framebetween.app.core.LibraryActions
import com.framebetween.app.core.LibraryItem
import com.framebetween.app.core.LibrarySettings
import com.framebetween.app.core.LibrarySource
import com.framebetween.app.core.LibraryTree
import com.framebetween.app.core.MediaLibrary
import com.framebetween.app.core.MediaMeta
import com.framebetween.app.core.MediaThumbnails
import com.framebetween.app.core.SortKey
import com.framebetween.app.core.ViewMode
import com.framebetween.app.ui.common.MediaPlayerDialog
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.util.MediaCover
import com.framebetween.app.util.formatBytes
import com.framebetween.app.util.formatClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sortKey by LibrarySettings.sortKey.collectAsState()
    val descending by LibrarySettings.descending.collectAsState()
    val viewMode by LibrarySettings.viewMode.collectAsState()
    val layout by LibrarySettings.layout.collectAsState()
    val gridCount by LibrarySettings.gridCount.collectAsState()
    val nestedFolders by LibrarySettings.nestedFolders.collectAsState()
    val scanVersion by LibrarySettings.scanVersion.collectAsState()

    // 全局 StateFlow：冷启动的磁盘快照、快阶段结果、完整结果都由 MediaLibrary 发布，
    // 页面不再等整段同步扫描跑完才一次性赋值，也不因切导航重建而丢状态。
    val items by MediaLibrary.items.collectAsState()
    val loading by MediaLibrary.loading.collectAsState()
    val newCounts by MediaLibrary.newCounts.collectAsState()
    var granted by rememberSaveable { mutableStateOf(MediaLibrary.hasPermission(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    // 嵌套文件夹：文件夹模式记录逐级进入的路径，树形模式记录已展开的节点 key
    var nestedPath by rememberSaveable { mutableStateOf(listOf<String>()) }
    var nestedExpanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showSort by remember { mutableStateOf(false) }
    var showViewOptions by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<LibraryItem?>(null) }
    var playerItems by remember { mutableStateOf<List<LibraryItem>?>(null) }
    val nowPlaying by AppState.nowPlaying.collectAsState()
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    // 文件夹多选与文件多选共用同一个 selectionMode，用 folderMode 区分当前选的是哪种对象
    var folderMode by rememberSaveable { mutableStateOf(false) }
    var selectedFolders by rememberSaveable { mutableStateOf(setOf<String>()) }
    var folderDetail by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    // 「全部文件」是标题旁的独立开关：按真实目录逐层浏览，不扫描媒体、不套用媒体过滤规则
    var allFilesMode by rememberSaveable { mutableStateOf(false) }
    var currentDir by rememberSaveable { mutableStateOf("") }
    var fsEntries by remember { mutableStateOf(emptyList<FsEntry>()) }
    var fsVersion by remember { mutableStateOf(0) }
    // 「全部文件」右上角排序菜单的状态（与媒体库排序互不影响）
    var fsSortKey by rememberSaveable { mutableStateOf(FsSortKey.NAME) }
    var fsDescending by rememberSaveable { mutableStateOf(false) }
    var fsDirsFirst by rememberSaveable { mutableStateOf(true) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }
    var operationItems by remember { mutableStateOf(listOf<LibraryItem>()) }
    var pendingMove by remember { mutableStateOf(false) }
    val expandedSet = expanded.toSet()

    val folderGroups = remember(items) { items.groupBy { it.folder } }
    val selectedFolderRefs = selectedFolders.map { key ->
        val group = folderGroups[key].orEmpty()
        if (group.isNotEmpty()) {
            LibraryActions.FolderRef(key, group.firstOrNull()?.folderPath, group)
        } else {
            // 嵌套目录节点的 key 是相对目录路径（如 DCIM/Camera），按短名分组找不到；
            // 用目录路径收齐该目录及其后代，name 仍取 key，保证黑名单标签非空。
            val label = key.trim().trim('/')
            val matched = if (label.isEmpty()) emptyList<LibraryItem>() else items.filter { item ->
                item.folderPath?.contains(label, ignoreCase = true) == true ||
                    item.folderLabel.contains(label, ignoreCase = true) ||
                    item.folder.contains(label, ignoreCase = true)
            }
            LibraryActions.FolderRef(key, null, matched)
        }
    }
    // 文件夹模式下，操作对象是所选文件夹内的全部媒体文件
    val selected: List<LibraryItem> = if (folderMode) {
        selectedFolderRefs.flatMap { it.files }
    } else {
        items.filter { it.id in selectedIds }
    }
    val selectionCount = if (folderMode) selectedFolders.size else selectedIds.size

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }

    val reload: () -> Unit = {
        granted = MediaLibrary.hasPermission(context)
        scope.launch {
            withContext(Dispatchers.IO) { MediaLibrary.loadStaged(context) }
        }
    }
    // 兜底：Application 启动时会预热，这里保证任何入口进入媒体库都能立刻拿到快照。
    // 放到 IO 线程，避免与后台预热抢同步锁时阻塞主线程首帧。
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { MediaLibrary.prime(context) }
    }
    // 三个媒体视图共用同一份扫描结果；视图切换只改变组织方式。
    LaunchedEffect(scanVersion) { reload() }
    // 文件管理模式只读取「当前目录这一层」，进入目录时现场读取，不做任何全盘扫描。
    LaunchedEffect(allFilesMode, currentDir, fsVersion, scanVersion) {
        if (!allFilesMode) return@LaunchedEffect
        val dir = currentDir.ifBlank { FileSystem.sharedRoot() }
        fsEntries = withContext(Dispatchers.IO) { FileSystem.entries(dir) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { reload() }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            scope.launch {
                val result = if (folderMode && selectedFolderRefs.isNotEmpty()) {
                    LibraryActions.copyFolders(context, selectedFolderRefs, uri, pendingMove)
                } else {
                    LibraryActions.copyTo(context, operationItems, uri, pendingMove)
                }
                AppState.notify(
                    if (pendingMove) {
                        L10n.t("已移动 %1\$s 个文件，失败 %2\$s 个", result.ok, result.failed)
                    } else {
                        L10n.t("已复制 %1\$s 个文件，失败 %2\$s 个", result.ok, result.failed)
                    }
                )
                LibraryActions.refresh()
                selectionMode = false
                selectedIds = emptySet()
                folderMode = false
                selectedFolders = emptySet()
            }
        }
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
        folderMode = false
        selectedFolders = emptySet()
    }

    /** 标题旁的开关：进入/退出「全部文件」文件管理模式。 */
    fun toggleAllFiles() {
        val next = !allFilesMode
        allFilesMode = next
        if (next) {
            exitSelection()
            currentDir = FileSystem.sharedRoot()
            fsEntries = emptyList()
            query = ""
            showSearch = false
        } else {
            currentDir = ""
            fsEntries = emptyList()
            query = ""
            showSearch = false
        }
    }

    /** 返回上一级目录；已经到最顶层时退出文件管理模式。 */
    fun goUpDirectory() {
        val parent = FileSystem.parentOf(currentDir.ifBlank { FileSystem.sharedRoot() })
        if (parent != null) {
            currentDir = parent
            query = ""
        } else {
            toggleAllFiles()
        }
    }

    BackHandler(
        enabled = playerItems != null || detail != null || folderDetail != null ||
            showRename || showDelete ||
            selectionMode || allFilesMode || nowPlaying.isNotEmpty() || selectedFolder != null ||
            expanded.isNotEmpty() || showSearch || nestedPath.isNotEmpty() || nestedExpanded.isNotEmpty()
    ) {
        when {
            playerItems != null -> playerItems = null
            detail != null -> detail = null
            folderDetail != null -> folderDetail = null
            showRename -> showRename = false
            showDelete -> showDelete = false
            selectionMode -> exitSelection()
            showSearch -> {
                showSearch = false
                query = ""
            }
            allFilesMode -> goUpDirectory()
            nowPlaying.isNotEmpty() -> AppState.stopAudio()
            nestedPath.isNotEmpty() -> nestedPath = nestedPath.dropLast(1)
            nestedExpanded.isNotEmpty() -> nestedExpanded = nestedExpanded.dropLast(1)
            selectedFolder != null -> selectedFolder = null
            expanded.isNotEmpty() -> expanded = expanded.dropLast(1)
        }
    }

    val filtered = remember(items, query, sortKey, descending) {
        val base = if (query.isBlank()) items else items.filter {
            it.title.contains(query, ignoreCase = true) || it.folderLabel.contains(query, ignoreCase = true)
        }
        val sorted = when (sortKey) {
            SortKey.TITLE -> base.sortedBy { it.title.lowercase() }
            SortKey.DATE -> base.sortedBy { it.dateMs }
            SortKey.SIZE -> base.sortedBy { it.sizeBytes }
        }
        if (descending) sorted.reversed() else sorted
    }

    /** 文件夹长按：进入「文件夹多选」，与文件多选互斥。 */
    val onFolderLongClick: (String) -> Unit = { key ->
        if (!selectionMode) {
            selectionMode = true
            folderMode = true
            selectedIds = emptySet()
        } else if (!folderMode) {
            folderMode = true
            selectedIds = emptySet()
        }
        selectedFolders = selectedFolders + key
    }
    val onFolderItem: (String) -> Unit = { key ->
        when {
            selectionMode && folderMode -> {
                selectedFolders = if (key in selectedFolders) {
                    selectedFolders - key
                } else {
                    selectedFolders + key
                }
                if (selectedFolders.isEmpty()) exitSelection()
            }
            !selectionMode -> {
                MediaLibrary.markFolderSeen(key)
                selectedFolder = key
            }
        }
    }
    val onLongClick: (LibraryItem) -> Unit = { item ->
        if (folderMode) onFolderLongClick(item.folder)
        else {
            if (!selectionMode) selectionMode = true
            selectedIds = selectedIds + item.id
        }
    }
    val onToggleSelect: (LibraryItem) -> Unit = { item ->
        if (folderMode) {
            onFolderItem(item.folder)
        } else {
            selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
            if (selectedIds.isEmpty()) selectionMode = false
        }
    }
    /** 详情、播放器与多选操作共用的「添加到当前文件列表」。 */
    val addToCurrent: (List<LibraryItem>) -> Unit = { targets ->
        scope.launch {
            val infos = LibraryActions.addToCurrent(targets)
            if (infos.isEmpty()) {
                AppState.notify(L10n.t("导入失败：无法读取该文件"))
            } else {
                AppState.addCurrent(infos)
                AppState.notify(L10n.t("已添加 %1\$s 个文件到当前列表", infos.size))
            }
        }
    }
    val onItem: (LibraryItem) -> Unit = { item ->
        when {
            selectionMode -> onToggleSelect(item)
            // 音频不弹窗，交给导航栏上方的圆角播放条
            item.isAudio -> AppState.playAudio(listOf(item))
            item.isVideo -> playerItems = listOf(item)
            // 图片 / 文档 / 压缩包绝不能塞进视频播放器：交给系统「打开方式」，
            // 找不到能打开的应用时退回详情弹窗。
            else -> {
                if (!LibraryActions.open(context, item)) detail = item
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        val barTitle = if (selectionMode) {
            if (folderMode) L10n.t("已选择 %1\$s 个文件夹", selectionCount)
            else L10n.t("已选择 %1\$s 项", selectionCount)
        } else L10n.t("媒体库")

        ScreenTopBar(
            title = barTitle,
            titleContent = {
                if (selectionMode) {
                    Text(barTitle, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                } else {
                    // 去掉中间的斜线；两段标题字体 / 字号 / 颜色完全一致，
                    // 选中态改用类似底部导航的胶囊指示器，而不是仅靠文字变色。
                    TitleSwitch(L10n.t("媒体库"), !allFilesMode) {
                        if (allFilesMode) toggleAllFiles()
                    }
                    Spacer(Modifier.width(8.dp))
                    TitleSwitch(L10n.t("全部文件"), allFilesMode) {
                        if (!allFilesMode) toggleAllFiles()
                    }
                }
            },
            actions = {
                if (selectionMode && folderMode) {
                    // 文件夹多选：详情 / 分享 / 黑名单 / 退出（不提供播放）
                    IconButton(
                        onClick = { folderDetail = selectedFolders.firstOrNull() },
                        enabled = selectionCount == 1
                    ) { Icon(Icons.Default.Info, contentDescription = L10n.t("详情")) }
                    IconButton(
                        onClick = { LibraryActions.share(context, selected) },
                        enabled = selected.isNotEmpty()
                    ) { Icon(Icons.Default.Share, contentDescription = L10n.t("分享")) }
                    IconButton(
                        onClick = {
                            val labels = selectedFolderRefs.map { ref ->
                                ref.path?.takeIf { it.isNotBlank() } ?: ref.name
                            }
                            LibrarySettings.setBlacklist(LibrarySettings.blacklist.value + labels)
                            AppState.notify(L10n.t("已将 %1\$s 个文件夹加入黑名单", labels.size))
                            exitSelection()
                        },
                        enabled = selectionCount > 0
                    ) { Icon(Icons.Default.Block, contentDescription = L10n.t("加入黑名单")) }
                    IconButton(onClick = ::exitSelection) {
                        Icon(Icons.Default.Close, contentDescription = L10n.t("退出多选"))
                    }
                } else if (selectionMode) {
                    IconButton(
                        onClick = {
                            val list = selected.toList()
                            val videos = list.filter { it.isVideo }
                            if (videos.isEmpty()) AppState.playAudio(list.filter { it.isAudio })
                            else playerItems = videos
                        },
                        enabled = selected.isNotEmpty()
                    ) { Icon(Icons.Default.PlayArrow, contentDescription = L10n.t("播放")) }
                    IconButton(
                        onClick = { detail = selected.firstOrNull() },
                        enabled = selectionCount == 1
                    ) { Icon(Icons.Default.Info, contentDescription = L10n.t("详情")) }
                    IconButton(
                        onClick = { LibraryActions.share(context, selected) },
                        enabled = selected.isNotEmpty()
                    ) { Icon(Icons.Default.Share, contentDescription = L10n.t("分享")) }
                    IconButton(onClick = ::exitSelection) {
                        Icon(Icons.Default.Close, contentDescription = L10n.t("退出多选"))
                    }
                } else if (allFilesMode) {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = L10n.t("搜索"))
                    }
                    Box {
                        IconButton(onClick = { showSort = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = L10n.t("排序"))
                        }
                        DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                            listOf(
                                FsSortKey.NAME to L10n.t("按名称"),
                                FsSortKey.DATE to L10n.t("按日期"),
                                FsSortKey.SIZE to L10n.t("按大小"),
                                FsSortKey.TYPE to L10n.t("按类型")
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { fsSortKey = key; showSort = false },
                                    trailingIcon = if (fsSortKey == key) {
                                        { Text("✓") }
                                    } else null
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (fsDescending) L10n.t("降序") else L10n.t("升序")) },
                                onClick = { fsDescending = !fsDescending; showSort = false }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (fsDirsFirst) L10n.t("文件夹优先：开")
                                        else L10n.t("文件夹优先：关")
                                    )
                                },
                                onClick = { fsDirsFirst = !fsDirsFirst; showSort = false }
                            )
                        }
                    }
                } else {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = L10n.t("搜索"))
                    }
                    Box {
                        IconButton(onClick = { showSort = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = L10n.t("排序"))
                        }
                        DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                            listOf(
                                SortKey.TITLE to L10n.t("按标题"),
                                SortKey.DATE to L10n.t("按日期"),
                                SortKey.SIZE to L10n.t("按大小")
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { LibrarySettings.setSortKey(key); showSort = false },
                                    trailingIcon = if (sortKey == key) {
                                        { Text("✓") }
                                    } else null
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (descending) L10n.t("降序") else L10n.t("升序")) },
                                onClick = { LibrarySettings.setDescending(!descending); showSort = false }
                            )
                        }
                    }
                    IconButton(onClick = { showViewOptions = true }) {
                        Icon(Icons.Default.Tune, contentDescription = L10n.t("视图选项"))
                    }
                }
            }
        )

        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(L10n.t("搜索标题或文件夹")) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { query = ""; showSearch = false }) {
                        Icon(Icons.Default.Close, contentDescription = L10n.t("关闭"))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (!granted && !allFilesMode) {
            PermissionCard(
                onGrant = { permissionLauncher.launch(MediaLibrary.requiredPermissions()) },
                onSettings = { MediaLibrary.openPermissionSettings(context) }
            )
        }
        if (allFilesMode && !MediaLibrary.hasAllFilesAccess()) {
            AllFilesAccessHint(onOpen = { MediaLibrary.openAllFilesSettings(context) })
        }

        if (!allFilesMode && viewMode == ViewMode.FOLDER && selectedFolder != null) {
            val path = items.firstOrNull { it.folder == selectedFolder }?.folderLabel ?: selectedFolder.orEmpty()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedFolder = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = L10n.t("返回上层"))
                }
                Column(Modifier.weight(1f)) {
                    Text(selectedFolder!!, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                TextButton(onClick = { selectedFolder = null }) { Text(L10n.t("全部文件夹")) }
            }
        }

        // 嵌套文件夹（文件夹模式）：面包屑显示当前层级，返回键 / 箭头逐级回退
        if (!allFilesMode && nestedFolders && viewMode == ViewMode.FOLDER && nestedPath.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { nestedPath = nestedPath.dropLast(1) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = L10n.t("返回上层"))
                }
                Column(Modifier.weight(1f)) {
                    Text(nestedPath.last(), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        nestedPath.joinToString(" / "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                TextButton(onClick = { nestedPath = emptyList() }) {
                    Text(L10n.t("全部文件夹"))
                }
            }
        }

        if (allFilesMode) {
            AllFilesBrowser(
                modifier = Modifier.weight(1f),
                entries = fsEntries,
                currentDir = currentDir.ifBlank { FileSystem.sharedRoot() },
                layout = layout,
                gridCount = gridCount,
                query = query,
                sortKey = fsSortKey,
                descending = fsDescending,
                directoriesFirst = fsDirsFirst,
                onOpenDir = { currentDir = it; query = "" },
                onUp = { goUpDirectory() },
                onHome = { currentDir = FileSystem.sharedRoot(); query = "" },
                onFile = onItem,
                onFileLongClick = { detail = it }
            )
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    granted = MediaLibrary.hasPermission(context)
                    scope.launch {
                        try {
                            MediaLibrary.invalidate()
                            withContext(Dispatchers.IO) { MediaLibrary.loadStaged(context) }
                        } finally {
                            refreshing = false
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                query.isNotBlank() -> L10n.t("没有匹配的结果")
                                loading -> L10n.t("正在扫描媒体…")
                                else -> L10n.t("暂无媒体文件")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    when (viewMode) {
                        ViewMode.FILE -> FileCollection(
                            filtered, layout, gridCount, listState, gridState,
                            selectionMode, selectedIds, onItem, onLongClick
                        )
                        ViewMode.FOLDER -> {
                            if (nestedFolders) {
                                NestedFolderCollection(
                                    filtered, nestedPath, layout, gridCount, listState, gridState,
                                    selectionMode, selectedIds, newCounts,
                                    onOpenFolder = { node, items ->
                                        MediaLibrary.markItemsSeen(items)
                                        nestedPath = nestedPath + node.name
                                    },
                                    onFolderLongClick = { key -> onFolderLongClick(key) },
                                    onFolderClick = onFolderItem,
                                    onItem = onItem,
                                    onLongClick = onLongClick,
                                    selectedFolders = if (selectionMode && folderMode) selectedFolders else emptySet(),
                                    folderSelectionMode = selectionMode && folderMode
                                )
                            } else if (selectedFolder == null) {
                                FolderCollection(
                                    filtered, layout, gridCount, listState, gridState,
                                    selectionMode && folderMode, selectedFolders,
                                    onFolderItem, onFolderLongClick, newCounts
                                )
                            } else {
                                FileCollection(
                                    filtered.filter { it.folder == selectedFolder },
                                    layout, gridCount, listState, gridState,
                                    selectionMode, selectedIds, onItem, onLongClick
                                )
                            }
                        }
                        ViewMode.TREE -> if (nestedFolders) {
                            NestedTreeCollection(
                                filtered, nestedExpanded.toSet(), layout, gridCount, listState,
                                selectionMode, selectedIds, newCounts,
                                onToggle = { key, items ->
                                    nestedExpanded = if (key in nestedExpanded) {
                                        nestedExpanded - key
                                    } else {
                                        nestedExpanded + key
                                    }
                                    MediaLibrary.markItemsSeen(items)
                                },
                                onFolderLongClick = { key -> onFolderLongClick(key) },
                                onFolderClick = onFolderItem,
                                onItem = onItem,
                                onLongClick = onLongClick,
                                selectedFolders = if (selectionMode && folderMode) selectedFolders else emptySet(),
                                folderSelectionMode = selectionMode && folderMode
                            )
                        } else {
                            TreeCollection(
                                filtered, layout, gridCount, expandedSet, listState,
                                selectionMode, selectedIds,
                                selectionMode && folderMode, selectedFolders,
                                onToggle = { key ->
                                    expanded = if (key in expanded) expanded - key else expanded + key
                                },
                                onItem = onItem,
                                onLongClick = onLongClick,
                                onFolderLongClick = onFolderLongClick,
                                newCounts = newCounts
                            )
                        }
                    }
                }
            }
        }

        if (selectionMode && !allFilesMode) {
            SelectionActionBar(
                count = selectionCount,
                onCopy = {
                    operationItems = selected.toList()
                    pendingMove = false
                    treeLauncher.launch(null)
                },
                onMove = {
                    operationItems = selected.toList()
                    pendingMove = true
                    treeLauncher.launch(null)
                },
                onRename = {
                    renameText = if (folderMode) {
                        selectedFolders.firstOrNull().orEmpty()
                    } else {
                        selected.firstOrNull()?.title.orEmpty()
                    }
                    showRename = true
                },
                onAdd = {
                    addToCurrent(selected.toList())
                    exitSelection()
                },
                onDelete = { showDelete = true }
            )
        }
    }

    if (showViewOptions) {
        ViewOptionsDialog(
            viewMode = viewMode,
            layout = layout,
            gridCount = gridCount,
            nestedFolders = nestedFolders,
            onViewMode = {
                LibrarySettings.setViewMode(it)
                selectedFolder = null
                nestedPath = emptyList()
                nestedExpanded = emptyList()
            },
            onLayout = { LibrarySettings.setLayout(it) },
            onGridCount = { LibrarySettings.setGridCount(it) },
            onNestedFolders = {
                LibrarySettings.setNestedFolders(it)
                selectedFolder = null
                nestedPath = emptyList()
                nestedExpanded = emptyList()
            },
            onDismiss = { showViewOptions = false }
        )
    }

    folderDetail?.let { key ->
        val group = folderGroups[key].orEmpty()
        val path = group.firstOrNull()?.folderPath ?: key
        AlertDialog(
            onDismissRequest = { folderDetail = null },
            title = { Text(key, maxLines = 2) },
            text = {
                Column {
                    Text(L10n.t("完整路径：%1\$s", path))
                    Text(L10n.t("媒体数量：%1\$s", group.size))
                    Text(L10n.t("总大小：%1\$s", formatBytes(group.sumOf { it.sizeBytes })))
                    Text(
                        L10n.t(
                            "视频：%1\$s · 音频：%2\$s",
                            group.count { it.isVideo }, group.count { it.isAudio }
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { folderDetail = null }) { Text(L10n.t("关闭")) }
            }
        )
    }

    detail?.let { item ->
        val meta by MediaMeta.map.collectAsState()
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(item.title, maxLines = 2) },
            text = {
                Column {
                    Text(L10n.t("大小：%1\$s", formatBytes(item.sizeBytes)))
                    Text(L10n.t("文件夹：%1\$s", item.folderLabel))
                    if (item.durationMs > 0) Text(L10n.t("时长：%1\$s", formatClock(item.durationMs)))
                    item.resolutionLabel(meta[item.id]).takeIf { it.isNotBlank() }?.let {
                        Text(L10n.t("分辨率：%1\$s", it))
                    }
                    item.path?.let { Text(L10n.t("路径：%1\$s", it)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = item
                    detail = null
                    addToCurrent(listOf(target))
                    exitSelection()
                }) { Text(L10n.t("添加到列表")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val target = item
                    detail = null
                    // 交给系统「打开方式」菜单；对话框本身可点击外部或返回键关闭
                    if (!LibraryActions.open(context, target)) {
                        AppState.notify(L10n.t("没有可用的应用打开此文件"))
                    }
                }) { Text(L10n.t("打开")) }
            }
        )
    }

    if (showRename) {
        val target = selected.firstOrNull()
        val targetFolder = if (folderMode) selectedFolderRefs.firstOrNull() else null
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(if (folderMode) L10n.t("重命名文件夹") else L10n.t("重命名")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text(if (folderMode) L10n.t("文件夹名") else L10n.t("文件名")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (folderMode) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            L10n.t("仅对设备上的真实目录生效；系统媒体库或 SAF 虚拟分组无法整体改名，请开启所有文件访问权限。"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = if (targetFolder != null) {
                            LibraryActions.renameFolder(context, targetFolder, renameText)
                        } else {
                            target?.let { LibraryActions.rename(context, it, renameText) } ?: false
                        }
                        AppState.notify(
                            if (ok) L10n.t("已重命名") else L10n.t("重命名失败：系统未授权修改该文件或文件夹")
                        )
                        LibraryActions.refresh()
                        showRename = false
                        exitSelection()
                    }
                }) { Text(L10n.t("确定")) }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text(L10n.t("取消")) } }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(if (folderMode) L10n.t("删除文件夹内容") else L10n.t("删除文件")) },
            text = {
                Text(
                    if (folderMode) {
                        L10n.t("将从设备删除所选 %1\$s 个文件夹中的 %2\$s 个文件，此操作不可撤销。", selectionCount, selected.size)
                    } else {
                        L10n.t("将从设备删除选中的 %1\$s 个文件，此操作不可撤销。", selected.size)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val targets = selected.toList()
                    val folders = selectedFolderRefs.toList()
                    scope.launch {
                        val result = if (folderMode && folders.isNotEmpty()) {
                            LibraryActions.deleteFolders(context, folders)
                        } else {
                            LibraryActions.delete(context, targets)
                        }
                        AppState.notify(L10n.t("已删除 %1\$s 个文件，失败 %2\$s 个", result.ok, result.failed))
                        LibraryActions.refresh()
                        showDelete = false
                        exitSelection()
                    }
                }) { Text(L10n.t("删除")) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(L10n.t("取消")) } }
        )
    }

    playerItems?.let { list ->
        MediaPlayerDialog(
            title = if (list.size == 1) list.first().title else L10n.t("播放列表（%1\$s）", list.size),
            uris = list.map { it.uri },
            video = list.first().isVideo,
            onDismiss = { playerItems = null },
            onAddToCurrent = { addToCurrent(list) }
        )
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit, onSettings: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(L10n.t("尚未授予媒体权限"), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                L10n.t("授权后可直接浏览系统音乐与视频并批量导入；FFmpeg 会优先直接读取原始文件路径，只有系统不允许直接访问时才复制到应用缓存。"),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onGrant, modifier = Modifier.weight(1f)) { Text(L10n.t("授予权限")) }
                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text(L10n.t("系统设置")) }
            }
        }
    }
}

/** 文件管理模式下的权限提示：不影响列出可读目录，只说明受限目录需要额外授权。 */
@Composable
private fun AllFilesAccessHint(onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                L10n.t("开启「所有文件访问权限」后可读取 Android/data 等受限目录"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall
            )
            TextButton(onClick = onOpen) { Text(L10n.t("去开启")) }
        }
    }
}

@Composable
private fun SelectionActionBar(
    count: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            ActionCell(Icons.Default.ContentCopy, L10n.t("复制"), count > 0, onCopy)
            ActionCell(Icons.AutoMirrored.Filled.ArrowForward, L10n.t("移动"), count > 0, onMove)
            ActionCell(Icons.Default.Edit, L10n.t("重命名"), count == 1, onRename)
            ActionCell(Icons.AutoMirrored.Filled.PlaylistAdd, L10n.t("添加到列表"), count > 0, onAdd)
            ActionCell(Icons.Default.Delete, L10n.t("删除"), count > 0, onDelete)
        }
    }
}

@Composable
private fun RowScope.ActionCell(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
    }
    Column(
        Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun FileCollection(
    files: List<LibraryItem>,
    layout: LayoutMode,
    gridCount: Int,
    listState: LazyListState,
    gridState: LazyGridState,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onItem: (LibraryItem) -> Unit,
    onLongClick: (LibraryItem) -> Unit
) {
    LibraryScrollbarHost(
        listState = if (layout == LayoutMode.LIST) listState else null,
        gridState = if (layout == LayoutMode.GRID) gridState else null
    ) {
        if (layout == LayoutMode.GRID) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridCount),
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(files, key = { it.id }) { item ->
                    FileTile(
                        item, LayoutMode.GRID, Modifier,
                        selectionMode, item.id in selectedIds,
                        { onItem(item) }, { onLongClick(item) }
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.id }) { item ->
                    FileTile(
                        item, LayoutMode.LIST, Modifier.fillMaxWidth(),
                        selectionMode, item.id in selectedIds,
                        { onItem(item) }, { onLongClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderCollection(
    files: List<LibraryItem>,
    layout: LayoutMode,
    gridCount: Int,
    listState: LazyListState,
    gridState: LazyGridState,
    selectionMode: Boolean,
    selectedFolders: Set<String>,
    onFolder: (String) -> Unit,
    onFolderLongClick: (String) -> Unit,
    newCounts: Map<String, Int>
) {
    val groups = remember(files) { files.groupBy { it.folder }.toList().sortedBy { it.first } }
    LibraryScrollbarHost(
        listState = if (layout == LayoutMode.LIST) listState else null,
        gridState = if (layout == LayoutMode.GRID) gridState else null
    ) {
        if (layout == LayoutMode.GRID) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridCount),
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(groups, key = { it.first }) { (name, list) ->
                    FolderTile(
                        name, list, LayoutMode.GRID, Modifier,
                        selectionMode, name in selectedFolders,
                        { onFolder(name) }, { onFolderLongClick(name) },
                        newCounts[name] ?: 0
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups, key = { it.first }) { (name, list) ->
                    FolderTile(
                        name, list, LayoutMode.LIST, Modifier.fillMaxWidth(),
                        selectionMode, name in selectedFolders,
                        { onFolder(name) }, { onFolderLongClick(name) },
                        newCounts[name] ?: 0
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeCollection(
    files: List<LibraryItem>,
    layout: LayoutMode,
    gridCount: Int,
    expanded: Set<String>,
    listState: LazyListState,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    folderSelectionMode: Boolean,
    selectedFolders: Set<String>,
    onToggle: (String) -> Unit,
    onItem: (LibraryItem) -> Unit,
    onLongClick: (LibraryItem) -> Unit,
    onFolderLongClick: (String) -> Unit,
    newCounts: Map<String, Int>
) {
    val tree = remember(files) {
        files.groupBy { if (it.source == LibrarySource.OUTPUT) "output" else "media" }
            .mapValues { (_, list) -> list.groupBy { it.folder } }
            .toList()
            .sortedBy { it.first }
    }
    LibraryScrollbarHost(listState = listState) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tree.forEach { (sourceKey, folders) ->
            item(key = "root_$sourceKey") {
                val rootName = if (sourceKey == "output") L10n.t("应用输出") else L10n.t("系统媒体库")
                TreeHeader(rootName, folders.values.sumOf { it.size }, 0, expanded.contains("root_$sourceKey")) {
                    onToggle("root_$sourceKey")
                }
            }
            if (expanded.contains("root_$sourceKey")) {
                folders.toList().sortedBy { it.first }.forEach { (folder, list) ->
                    val key = "folder_${sourceKey}_$folder"
                    item(key = key) {
                        TreeHeader(
                            folder, list.size, 1, expanded.contains(key),
                            selected = folderSelectionMode && folder in selectedFolders,
                            newCount = newCounts[folder] ?: 0,
                            onLongClick = { onFolderLongClick(folder) }
                        ) {
                            if (!expanded.contains(key)) MediaLibrary.markFolderSeen(folder)
                            onToggle(key)
                        }
                    }
                    if (expanded.contains(key)) {
                        item(key = "files_$key") {
                            StaticFiles(
                                list, layout, gridCount,
                                selectionMode, selectedIds, onItem, onLongClick
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

/** 嵌套文件夹（文件夹模式）里的两种条目：子目录或当前层直属媒体。 */
private sealed class NestedEntry {
    abstract val key: String
    data class Dir(val node: LibraryTree.Node, val items: List<LibraryItem>) : NestedEntry() {
        override val key: String get() = "dir_${node.key}"
    }
    data class Media(val item: LibraryItem) : NestedEntry() {
        override val key: String get() = "media_${item.id}"
    }
}

/**
 * 嵌套文件夹 · 文件夹模式：只列当前层的子文件夹 + 本层音视频，
 * 点文件夹进下一级，面包屑与返回键逐级回退。
 */
@Composable
private fun NestedFolderCollection(
    files: List<LibraryItem>,
    path: List<String>,
    layout: LayoutMode,
    gridCount: Int,
    listState: LazyListState,
    gridState: LazyGridState,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    newCounts: Map<String, Int>,
    onOpenFolder: (LibraryTree.Node, List<LibraryItem>) -> Unit,
    onFolderLongClick: (String) -> Unit,
    onFolderClick: (String) -> Unit,
    onItem: (LibraryItem) -> Unit,
    onLongClick: (LibraryItem) -> Unit,
    selectedFolders: Set<String> = emptySet(),
    folderSelectionMode: Boolean = false
) {
    val paths = remember(files) { files.map(LibraryTree::segments) }
    val nodes = remember(files, path) { LibraryTree.nodesOf(paths, path) }
    val direct = remember(files, path) { LibraryTree.directOf(paths, path).map { files[it] } }
    val entries = remember(nodes, direct) {
        nodes.map { node -> NestedEntry.Dir(node, node.indices.map { files[it] }) } +
            direct.map { NestedEntry.Media(it) }
    }
    LibraryScrollbarHost(
        listState = if (layout == LayoutMode.LIST) listState else null,
        gridState = if (layout == LayoutMode.GRID) gridState else null
    ) {
        if (layout == LayoutMode.GRID) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridCount),
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(entries, key = { it.key }) { entry ->
                    when (entry) {
                        is NestedEntry.Dir -> {
                            val folderKey = entry.node.key
                            FolderTile(
                                entry.node.name, entry.items, LayoutMode.GRID, Modifier,
                                folderSelectionMode, folderKey in selectedFolders,
                                {
                                    if (folderSelectionMode) onFolderClick(folderKey)
                                    else onOpenFolder(entry.node, entry.items)
                                },
                                { onFolderLongClick(folderKey) },
                                LibraryTree.newCount(entry.items, newCounts)
                            )
                        }
                        is NestedEntry.Media -> FileTile(
                            entry.item, LayoutMode.GRID, Modifier,
                            selectionMode, entry.item.id in selectedIds,
                            { onItem(entry.item) }, { onLongClick(entry.item) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.key }) { entry ->
                    when (entry) {
                        is NestedEntry.Dir -> {
                            val folderKey = entry.node.key
                            FolderTile(
                                entry.node.name, entry.items, LayoutMode.LIST, Modifier.fillMaxWidth(),
                                folderSelectionMode, folderKey in selectedFolders,
                                {
                                    if (folderSelectionMode) onFolderClick(folderKey)
                                    else onOpenFolder(entry.node, entry.items)
                                },
                                { onFolderLongClick(folderKey) },
                                LibraryTree.newCount(entry.items, newCounts)
                            )
                        }
                        is NestedEntry.Media -> FileTile(
                            entry.item, LayoutMode.LIST, Modifier.fillMaxWidth(),
                            selectionMode, entry.item.id in selectedIds,
                            { onItem(entry.item) }, { onLongClick(entry.item) }
                        )
                    }
                }
            }
        }
    }
}

/** 嵌套文件夹 · 树形模式：一次只展开当前节点，展开后列出直属媒体与下一级目录。 */
@Composable
private fun NestedTreeCollection(
    files: List<LibraryItem>,
    expanded: Set<String>,
    layout: LayoutMode,
    gridCount: Int,
    listState: LazyListState,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    newCounts: Map<String, Int>,
    onToggle: (String, List<LibraryItem>) -> Unit,
    onFolderLongClick: (String) -> Unit,
    onFolderClick: (String) -> Unit,
    onItem: (LibraryItem) -> Unit,
    onLongClick: (LibraryItem) -> Unit,
    selectedFolders: Set<String> = emptySet(),
    folderSelectionMode: Boolean = false
) {
    val paths = remember(files) { files.map(LibraryTree::segments) }
    val rows = remember(files, expanded) { LibraryTree.flattenPaths(paths, expanded) }
    LibraryScrollbarHost(listState = listState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(rows, key = { it.key }) { row ->
                val rowItems = row.indices.map { files[it] }
                if (row.folder) {
                    val folderKey = row.key
                    TreeHeader(
                        row.name, rowItems.size, row.depth, row.key in expanded,
                        selected = folderKey in selectedFolders,
                        newCount = LibraryTree.newCount(rowItems, newCounts),
                        onLongClick = { onFolderLongClick(folderKey) }
                    ) {
                        if (folderSelectionMode) onFolderClick(folderKey)
                        else onToggle(row.key, rowItems)
                    }
                } else {
                    StaticFiles(
                        rowItems, layout, gridCount,
                        selectionMode, selectedIds, onItem, onLongClick
                    )
                }
            }
        }
    }
}

@Composable
private fun StaticFiles(
    files: List<LibraryItem>,
    layout: LayoutMode,
    gridCount: Int,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onItem: (LibraryItem) -> Unit,
    onLongClick: (LibraryItem) -> Unit
) {
    Column(Modifier.padding(start = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (layout == LayoutMode.GRID) {
            files.chunked(gridCount).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        FileTile(
                            item, LayoutMode.GRID, Modifier.weight(1f),
                            selectionMode, item.id in selectedIds,
                            { onItem(item) }, { onLongClick(item) }
                        )
                    }
                    repeat(gridCount - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            files.forEach { item ->
                FileTile(
                    item, LayoutMode.LIST, Modifier.fillMaxWidth(),
                    selectionMode, item.id in selectedIds,
                    { onItem(item) }, { onLongClick(item) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeHeader(
    name: String,
    count: Int,
    indent: Int,
    open: Boolean,
    selected: Boolean = false,
    newCount: Int = 0,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .padding(start = (indent * 16).dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (open) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        if (newCount > 0) {
            FolderIconWithBadge(
                if (selected) Icons.Default.CheckCircle else Icons.Default.Folder,
                newCount, 18.dp
            )
        } else {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.Folder,
                null,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$count", style = MaterialTheme.typography.bodySmall)
    }
}

/** 异步加载视频缩略图 / 音频封面；失败或无封面时回退类型图标。 */
@Composable
private fun Thumbnail(
    item: LibraryItem,
    modifier: Modifier,
    fallback: ImageVector,
    iconSize: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val version by LibrarySettings.scanVersion.collectAsState()
    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.id, version) {
        bitmap = withContext(Dispatchers.IO) { MediaThumbnails.load(context, item) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(fallback, null, Modifier.size(iconSize), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 时长角标：右下角深色底 + 浅色文字。 */
@Composable
private fun DurationBadge(
    durationMs: Long,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (durationMs <= 0) return
    Box(
        modifier
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
            .padding(horizontal = if (compact) 3.dp else 5.dp, vertical = 2.dp)
    ) {
        Text(
            formatClock(durationMs),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontSize = if (compact) 8.sp else 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTile(
    item: LibraryItem,
    layout: LayoutMode,
    modifier: Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val metaMap by MediaMeta.map.collectAsState()
    LaunchedEffect(item.id) { MediaMeta.load(context, item) }
    val icon: ImageVector = when {
        item.isVideo -> Icons.Default.Movie
        item.isAudio -> Icons.Default.MusicNote
        item.mime.startsWith("image") -> Icons.Default.Image
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    val shape = RoundedCornerShape(8.dp)
    val resolution = item.resolutionLabel(metaMap[item.id])
    // 视频与音频共用 3:2 宽画布，列表 / 网格下画布占用完全一致。
    val wideCanvas = MediaCover.isWideCanvas(item.isVideo, item.isAudio)
    val coverRatio = MediaCover.aspectRatio(item.isVideo, item.isAudio)
    Card(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        if (layout == LayoutMode.GRID) {
            Column(Modifier.padding(8.dp)) {
                // 视频与音频同为 3:2，图片 / 其它文件仍为 1:1
                Box(Modifier.fillMaxWidth().aspectRatio(coverRatio).clip(shape)) {
                    Thumbnail(item, Modifier.fillMaxSize(), icon, 28.dp)
                    DurationBadge(item.durationMs, Modifier.align(Alignment.BottomEnd).padding(4.dp), true)
                    if (selected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = L10n.t("已选中"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    item.title,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                // 网格下视频不再显示分辨率与帧率，只保留体积，缩短卡片高度
                Text(
                    formatBytes(item.sizeBytes),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // 视频 / 音频统一 84×56dp（3:2）；图片 / 其它文件 72×72dp（1:1）
                Box(
                    (if (wideCanvas) {
                        Modifier.width(84.dp).aspectRatio(coverRatio)
                    } else {
                        Modifier.size(72.dp)
                    }).clip(shape)
                ) {
                    Thumbnail(item, Modifier.fillMaxSize(), icon, 24.dp)
                    DurationBadge(item.durationMs, Modifier.align(Alignment.BottomEnd).padding(2.dp), true)
                    if (selected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = L10n.t("已选中"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        listOf(formatBytes(item.sizeBytes), resolution)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.source == LibrarySource.OUTPUT && !selectionMode) {
                    Text(L10n.t("产物"), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** 文件夹图标 + 右上角新增媒体数量角标；数量为 0 时布局与普通图标一致。 */
@Composable
private fun FolderIconWithBadge(icon: ImageVector, badgeCount: Int, iconSize: Dp) {
    Box(Modifier.size(iconSize + 8.dp), contentAlignment = Alignment.Center) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize)
        )
        if (badgeCount > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = MaterialTheme.colorScheme.onError,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 文件夹卡片：
 * - 网格：名称放在图标下面，不显示路径 / 数量 / 大小，整体更矮；
 * - 列表：图标单独放左侧，名称 → 路径 → 数量与大小依次左对齐，
 *   「名称 / 路径」与「路径 / 数量」使用同一个 2dp 间距。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(
    name: String,
    files: List<LibraryItem>,
    layout: LayoutMode,
    modifier: Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    newCount: Int = 0
) {
    val path = files.firstOrNull()?.folderLabel ?: name
    val total = files.sumOf { it.sizeBytes }
    val mb = "%.1f MB".format(Locale.US, total / 1024.0 / 1024.0)
    Card(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        if (layout == LayoutMode.GRID) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FolderIconWithBadge(
                    icon = if (selected) Icons.Default.CheckCircle else Icons.Default.Folder,
                    badgeCount = newCount,
                    iconSize = 34.dp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    name,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标单独放在最左边，不再与文字挤在同一列
                FolderIconWithBadge(Icons.Default.Folder, newCount, 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        path,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    // 数量与大小贴在路径下方左对齐，配色区分
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${files.size} Files",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            mb,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                if (selected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = L10n.t("已选中"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewOptionsDialog(
    viewMode: ViewMode,
    layout: LayoutMode,
    gridCount: Int,
    nestedFolders: Boolean,
    onViewMode: (ViewMode) -> Unit,
    onLayout: (LayoutMode) -> Unit,
    onGridCount: (Int) -> Unit,
    onNestedFolders: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.t("视图选项")) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(L10n.t("视图模式"), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ViewMode.FOLDER to L10n.t("文件夹"),
                        ViewMode.TREE to L10n.t("树形"),
                        ViewMode.FILE to L10n.t("文件")
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = viewMode == mode,
                            onClick = { onViewMode(mode) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    L10n.t("这三个视图只展示扫描到的音视频；标题旁的「全部文件」可进入本机文件管理。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(L10n.t("嵌套文件夹"), style = MaterialTheme.typography.labelMedium)
                        Text(
                            L10n.t("开启后按真实目录层级显示，只列最顶层母文件夹，点击逐级打开或展开下一级。"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = nestedFolders && viewMode != ViewMode.FILE,
                        onCheckedChange = onNestedFolders,
                        enabled = viewMode != ViewMode.FILE
                    )
                }
                Text(
                    L10n.t("仅文件夹 / 树形模式可用；文件视图始终平铺。"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(L10n.t("布局"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        LayoutMode.LIST to L10n.t("列表"),
                        LayoutMode.GRID to L10n.t("网格")
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = layout == mode,
                            onClick = { onLayout(mode) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(L10n.t("网格列数"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 3).forEach { count ->
                        FilterChip(
                            selected = gridCount == count,
                            onClick = { onGridCount(count) },
                            enabled = layout == LayoutMode.GRID,
                            label = { Text(L10n.t("%1\$s 列", count)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    L10n.t("树形视图的分组节点始终以列表展示，展开后的文件会按上面的布局与列数排列。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L10n.t("完成")) } }
    )
}

/** 标题旁的模式切换：选中态用类似底部导航的胶囊指示器，未选中只有文字，两段样式完全一致。 */
@Composable
private fun TitleSwitch(text: String, selected: Boolean, onClick: () -> Unit) {
    val fill = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
