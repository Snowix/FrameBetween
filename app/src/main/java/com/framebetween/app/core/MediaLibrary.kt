package com.framebetween.app.core

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.framebetween.app.App
import com.framebetween.app.util.isAudioVideoMime
import com.framebetween.app.util.mimeOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class LibrarySource { MEDIA, OUTPUT, SCAN }

/** 媒体库条目：系统媒体库中的音视频、扫描目录与本应用产物统一成一种结构。 */
data class LibraryItem(
    val id: String,
    val title: String,
    val uri: Uri,
    val sizeBytes: Long,
    val dateMs: Long,
    val durationMs: Long,
    val mime: String,
    val folder: String,
    val source: LibrarySource,
    val albumId: Long = 0L,
    val path: String? = null,
    /** 文件夹完整路径，供文件夹视图备注与黑白名单匹配；无法解析时为 null。 */
    val folderPath: String? = null,
    /** 视频分辨率与帧率；MediaStore 提供宽高，帧率由 MediaMeta 惰性补齐。 */
    val width: Int = 0,
    val height: Int = 0,
    val fps: Double = 0.0,
    /** 位于名称以 . 开头的目录 / 文件，需「包含隐藏文件夹」才显示。 */
    val hiddenDir: Boolean = false,
    /** 位于含 .nomedia 标记的目录树，需「包含 .nomedia 文件夹」才显示。 */
    val nomediaDir: Boolean = false
) {
    val isVideo: Boolean get() = mime.startsWith("video")
    val isAudio: Boolean get() = mime.startsWith("audio")

    /** 展示用的完整目录：优先真实路径，其次 bucket 名称。 */
    val folderLabel: String get() = folderPath ?: folder

    /** 「1920×1080@30」；缺帧率时只显示分辨率。 */
    fun resolutionLabel(meta: VideoMeta? = null): String {
        val w = if (width > 0) width else meta?.width ?: 0
        val h = if (height > 0) height else meta?.height ?: 0
        if (w <= 0 || h <= 0) return ""
        val rate = if (fps > 0) fps else meta?.fps ?: 0.0
        val base = "${w}×${h}"
        return if (rate > 0) "$base@${"%.0f".format(java.util.Locale.US, rate)}" else base
    }
}

/**
 * 媒体库数据源 + 媒体权限工具。
 * 除 MediaStore 外，有「所有文件访问权限」时会直接遍历共享存储，
 * 这样新增视频、隐藏目录、.nomedia 目录树与 SAF 扫描路径都能被真正发现。
 */
object MediaLibrary {

    /** 当前已发布的列表：切换导航回来时立刻有内容，不必等一次新的 MediaStore 查询。 */
    @Volatile
    private var snapshot: List<LibraryItem> = emptyList()

    /** 上一次完整扫描结果（跨导航保留，冷启动由磁盘快照恢复），用于首屏打底与变动比对。 */
    @Volatile
    private var lastFull: List<LibraryItem> = emptyList()

    /** 已被用户打开过的文件夹里的文件 id；未看过的文件才计入新增角标。 */
    private val seenIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 文件夹 → 新增媒体数量；打开该文件夹后清零。 */
    private val _newCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val newCounts: StateFlow<Map<String, Int>> = _newCounts.asStateFlow()

    /** 把持久化的已看 id 灌进内存；幂等，保证任何调用路径都先加载过。 */
    private fun loadSeenBaseline() {
        seenIds.addAll(LibrarySettings.seenIds())
    }

    private fun persistSeen() {
        LibrarySettings.saveSeenIds(seenIds.toSet())
    }

    /** 用户点开文件夹后，把该文件夹及其所有后代文件标记为已看，角标消失。 */
    fun markFolderSeen(folder: String) {
        val clean = folder.trim().trimEnd('/')
        val matched = snapshot.filter { item ->
            val f = item.folder
            val p = item.folderPath.orEmpty()
            f == clean || f.startsWith("$clean/") || p == clean || p.startsWith("$clean/") ||
                item.folderLabel == clean || item.folderLabel.startsWith("$clean/")
        }
        if (matched.isNotEmpty()) seenIds.addAll(matched.map { it.id })
        recomputeNewCounts()
    }

    private fun recomputeNewCounts() {
        loadSeenBaseline()
        if (!LibrarySettings.seenBaseline()) {
            _newCounts.value = emptyMap()
            return
        }
        // 快照尚未发布时不要按空集合清理，避免冷启动最早阶段抹掉持久化的已看基线
        if (snapshot.isNotEmpty()) {
            val aliveIds = snapshot.mapTo(HashSet<String>()) { it.id }
            seenIds.retainAll(aliveIds)
        }
        _newCounts.value = LibraryDiff.newCounts(snapshot.map(::entry), seenIds)
        persistSeen()
    }

    private fun entry(item: LibraryItem) = LibraryDiff.Entry(
        id = item.id,
        key = item.path?.let { "p:${File(it).absolutePath}" } ?: item.id,
        folder = item.folder,
        sizeBytes = item.sizeBytes,
        dateMs = item.dateMs,
        durationMs = item.durationMs,
        fromMediaStore = item.source == LibrarySource.MEDIA,
        alive = alive(item)
    )

    /**
     * 全局可观察的媒体清单。冷启动时先由 [prime] 发布磁盘快照，扫描过程中再分阶段
     * 发布「系统媒体 + 应用产物」与「补齐扫描路径后的完整结果」。
     * 页面只收集这份 Flow，不再等一整次同步扫描跑完才赋值，因此首屏不会空白。
     */
    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    val items: StateFlow<List<LibraryItem>> = _items.asStateFlow()

    /** 后台补齐扫描路径时为空态文案提供区分；纯文本提示，不引入任何动画。 */
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    @Volatile
    private var primed = false
    private val snapshotLock = Any()

    /** 扫描代次：快速连续触发重扫时，只允许最后一次的结果发布，避免旧结果盖掉新结果。 */
    private val loadGeneration = AtomicLong(0)

    private fun snapshotFile(context: Context): File =
        File(context.applicationContext.cacheDir, "library_snapshot.bin")

    /** path + lastModified -> duration，避免每次重新扫描都对每个文件跑 MediaMetadataRetriever。 */
    private val durationCache = ConcurrentHashMap<String, Long>()

    /**
     * 目录 → 是否位于 .nomedia 目录树。MediaStore 返回的条目不带 nomediaDir 标记，
     * 只靠扫描器打的标记会漏判；这里按真实路径向上找 .nomedia 文件并缓存结果。
     */
    private val nomediaDirs = ConcurrentHashMap<String, Boolean>()

    fun clearDurationCache() {
        durationCache.clear()
    }

    /** MediaStore.Files 没有 EXTERNAL_CONTENT_URI 常量，用兼容 minSdk 26 的 external volume。 */
    private fun allFilesUri(): Uri = MediaStore.Files.getContentUri("external")

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /** Android 11+ 的「所有文件访问权限」需用户在系统设置页手动开启。 */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                App.instance, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    /** 已获得「所有文件访问权限」时无需再逐项请求 READ_MEDIA_*。 */
    fun hasPermission(context: Context): Boolean = hasAllFilesAccess() ||
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun openAllFilesSettings(context: Context) {
        val launched = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } else false
        }.getOrDefault(false)
        if (!launched) openPermissionSettings(context)
    }

    fun openPermissionSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 任何即将发布的列表都再套一次当前过滤规则，杜绝缓存 / 沿用条目绕过开关。 */
    private fun conforming(items: List<LibraryItem>): List<LibraryItem> =
        runCatching { applyRules(items) }.getOrDefault(items)

    /**
     * 重新扫描时只清时长缓存；保留已发布列表与上次完整结果，避免刷新时目录闪没。
     * 开关变化（含 .nomedia / 隐藏目录 / 黑白名单 / 过滤阈值）时立即用当前规则
     * 重筛已发布内容与 lastFull，否则 carryOver() 会把关掉之前扫到的 .nomedia 目录又带回来。
     */
    fun invalidate() {
        clearDurationCache()
        val current = conforming(snapshot)
        snapshot = current
        lastFull = conforming(lastFull)
        _items.value = current
        recomputeNewCounts()
    }

    /** 嵌套文件夹视图打开某个目录时，把该目录下所有媒体标记为已看，清掉新增角标。 */
    fun markItemsSeen(items: Collection<LibraryItem>) {
        if (items.isEmpty()) return
        seenIds.addAll(items.map { it.id })
        recomputeNewCounts()
    }

    /**
     * 冷启动秒开：读上一轮扫描落盘的快照，立刻发布给媒体库页。
     * 只读一个小文件并剔除已删除的真实文件，通常在几十毫秒内完成；随后由
     * [loadStaged] 在后台用真实扫描结果覆盖。幂等，重复调用无副作用。
     */
    @Synchronized
    fun prime(context: Context) {
        loadSeenBaseline()
        if (primed) return
        primed = true
        // 真实扫描已经开始/完成时不再用旧快照覆盖新结果（预热比首屏扫描慢的情况）。
        if (loadGeneration.get() > 0) return
        val bytes = runCatching { snapshotFile(context).readBytes() }.getOrDefault(ByteArray(0))
        if (bytes.isEmpty()) return
        val restored = LibrarySnapshot.decode(bytes)
            .mapNotNull(::rowToItem)
            .filter(::alive)
            .let(::conforming)
        if (restored.isNotEmpty()) {
            snapshot = restored
            lastFull = restored
            seenIds.addAll(restored.map { it.id })
            // 快照代表历史已有文件，一律不算新增；同时把基线持久化，冷启动不再满屏红点
            LibrarySettings.setSeenBaseline(true)
            persistSeen()
            _items.value = restored
        }
    }

    /** 删除或新增后请求系统刷新 MediaStore，避免其它应用仍看到陈旧索引。 */
    fun notifyPathsChanged(context: Context, paths: List<String>) {
        val valid = paths.filter { it.isNotBlank() }.distinct().take(200)
        if (valid.isEmpty()) return
        runCatching {
            MediaScannerConnection.scanFile(context, valid.toTypedArray(), null, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun readStore(
        context: Context,
        collection: Uri,
        durationColumn: String?,
        fallbackFolder: String,
        albumColumn: String? = null,
        /** Files 集合包含文档 / 压缩包，只收真实媒体 mime。 */
        mimeFilter: Boolean = false
    ): List<LibraryItem> {
        // MediaStore.Files 集合在部分厂商上不提供 BUCKET_DISPLAY_NAME，改用 DATA 解析目录名。
        val useBucket = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            collection != allFilesUri()
        val folderColumn = if (useBucket) {
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        } else {
            MediaStore.MediaColumns.DATA
        }
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            folderColumn
        )
        // 视频集合才带 width / height，避免部分厂商的音频集合查询未知列导致整批失败
        if (collection == MediaStore.Video.Media.EXTERNAL_CONTENT_URI) {
            projection += "width"
            projection += "height"
        }
        // DATA 在 Android 10+ 仍返回文件路径（只读用途），用于文件夹完整路径备注与存活校验
        if (useBucket) projection += MediaStore.MediaColumns.DATA
        if (durationColumn != null) projection += durationColumn
        if (albumColumn != null) projection += albumColumn

        val cursor = runCatching {
            context.contentResolver.query(collection, projection.toTypedArray(), null, null, null)
        }.getOrNull() ?: return emptyList()

        val out = mutableListOf<LibraryItem>()
        cursor.use { c ->
            val idIdx = c.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val dateIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeIdx = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val folderIdx = c.getColumnIndex(folderColumn)
            val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            val durationIdx = durationColumn?.let { c.getColumnIndex(it) } ?: -1
            val albumIdx = albumColumn?.let { c.getColumnIndex(it) } ?: -1
            val widthIdx = c.getColumnIndex("width")
            val heightIdx = c.getColumnIndex("height")
            val fpsIdx = c.getColumnIndex("frame_rate")
            while (c.moveToNext()) {
                val id = if (idIdx >= 0) c.getLong(idIdx) else continue
                val raw = if (folderIdx >= 0) c.getString(folderIdx).orEmpty() else ""
                val data = if (dataIdx >= 0) c.getString(dataIdx).orEmpty() else ""
                val parent = data.takeIf { it.isNotBlank() }?.let {
                    runCatching { File(it).parent }.getOrNull()
                }
                val folder = if (useBucket) {
                    raw.ifBlank { fallbackFolder }
                } else {
                    runCatching { File(raw).parentFile?.name }.getOrNull().orEmpty().ifBlank { fallbackFolder }
                }
                val name = if (nameIdx >= 0) c.getString(nameIdx).orEmpty() else "media_$id"
                val rawMime = if (mimeIdx >= 0) c.getString(mimeIdx).orEmpty() else ""
                // 部分厂商的 MediaStore 行 mime 为空：按集合类型或扩展名补一个，
                // 否则会被 applyRules() 的「只收音视频」守卫误删。
                val mime = when {
                    rawMime.isNotBlank() -> rawMime
                    collection == MediaStore.Video.Media.EXTERNAL_CONTENT_URI -> "video/mp4"
                    collection == MediaStore.Audio.Media.EXTERNAL_CONTENT_URI -> "audio/mpeg"
                    else -> mimeOf(name)
                }
                if (mimeFilter && !isMediaMime(mime)) continue
                out += LibraryItem(
                    id = "ms_$id",
                    title = name,
                    uri = ContentUris.withAppendedId(collection, id),
                    sizeBytes = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L,
                    dateMs = (if (dateIdx >= 0) c.getLong(dateIdx) else 0L) * 1000L,
                    durationMs = if (durationIdx >= 0) c.getLong(durationIdx) else 0L,
                    mime = mime,
                    folder = folder,
                    source = LibrarySource.MEDIA,
                    albumId = if (albumIdx >= 0) c.getLong(albumIdx) else 0L,
                    path = data.takeIf { it.isNotBlank() },
                    folderPath = parent,
                    width = if (widthIdx >= 0) c.getInt(widthIdx) else 0,
                    height = if (heightIdx >= 0) c.getInt(heightIdx) else 0,
                    fps = if (fpsIdx >= 0) c.getDouble(fpsIdx) else 0.0
                )
            }
        }
        return out
    }

    /**
     * MediaStore 不索引「应用产物 / .nomedia / SAF 扫描路径」下的文件，这些条目必须自己
     * 用 MediaMetadataRetriever 解析时长，否则媒体库不显示时长角标、也躲过极短视频过滤。
     */
    private fun probeDuration(context: Context, uri: Uri, path: String?): Long {
        val file = path?.let { File(it) }
        val key = "${path ?: uri}|${file?.lastModified() ?: 0L}"
        durationCache[key]?.let { return it }
        val result = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                if (file != null && file.canRead()) {
                    retriever.setDataSource(file.absolutePath)
                } else {
                    retriever.setDataSource(context, uri)
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrDefault(0L)
        if (durationCache.size > 6000) durationCache.clear()
        durationCache[key] = result
        return result
    }

    /** 应用默认输出目录不需要媒体权限，任何时候都能展示。 */
    private fun readOutputs(): List<LibraryItem> {
        val dir = MediaRepository.externalDir()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()?.filter { it.isFile && isMediaName(it.name) }?.map { f ->
            LibraryItem(
                id = "out_${f.absolutePath}",
                title = f.name,
                uri = MediaRepository.shareUri(f.absolutePath),
                sizeBytes = f.length(),
                dateMs = f.lastModified(),
                durationMs = 0L,
                mime = mimeOf(f.name),
                folder = "FrameBetween",
                source = LibrarySource.OUTPUT,
                path = f.absolutePath,
                folderPath = dir.absolutePath
            )
        }.orEmpty()
    }

    /**
     * 扫描路径递归遍历整棵 SAF 目录树。
     * 隐藏目录与 .nomedia 目录树按开关收录，父目录标记会向下传递。
     */
    private fun readScanPaths(context: Context): List<LibraryItem> {
        val includeHidden = LibrarySettings.includeHidden.value
        val includeNoMedia = LibrarySettings.includeNoMedia.value
        val out = mutableListOf<LibraryItem>()
        LibrarySettings.scanPaths.value.forEach pathLoop@{ raw ->
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return@pathLoop
            val tree = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull() ?: return@pathLoop
            val rootLabel = MediaRepository.treeDisplayPath(uri.toString())
                .ifBlank { tree.name ?: "FrameBetween" }
            var visitedDirs = 0

            fun walk(
                dir: DocumentFile,
                label: String,
                depth: Int,
                hiddenAncestor: Boolean,
                nomediaAncestor: Boolean
            ) {
                if (depth > 12 || visitedDirs > 1000 || out.size > 8000) return
                visitedDirs++
                val children = runCatching { dir.listFiles() }.getOrNull() ?: return
                val noMediaHere = children.any { it.isFile && it.name.equals(".nomedia", true) }
                val hiddenPath = hiddenAncestor || dir.name?.startsWith(".") == true
                val nomediaPath = nomediaAncestor || noMediaHere
                children.forEach { doc ->
                    runCatching {
                        when {
                            doc.isDirectory -> walk(
                                doc, "$label/${doc.name.orEmpty()}",
                                depth + 1, hiddenPath, nomediaPath
                            )
                            doc.isFile -> {
                                val name = doc.name ?: return@runCatching
                                if (!isMediaName(name)) return@runCatching
                                val hiddenFile = hiddenPath || name.startsWith(".")
                                if (!LibraryRules.allowed(hiddenFile, nomediaPath, includeHidden, includeNoMedia)) {
                                    return@runCatching
                                }
                                out += LibraryItem(
                                    id = "path_${doc.uri}",
                                    title = name,
                                    uri = doc.uri,
                                    sizeBytes = doc.length(),
                                    dateMs = doc.lastModified(),
                                    durationMs = 0L,
                                    mime = mimeOf(name),
                                    folder = dir.name ?: label.substringAfterLast('/'),
                                    source = LibrarySource.SCAN,
                                    folderPath = label,
                                    hiddenDir = hiddenFile,
                                    nomediaDir = nomediaPath
                                )
                            }
                        }
                    }
                }
            }
            runCatching { walk(tree, rootLabel, 0, false, false) }
        }
        return out
    }

    /**
     * 有「所有文件访问权限」时直接遍历共享存储：
     * 1. 补齐 MediaStore 尚未索引的新增媒体，让「重新扫描」真正生效；
     * 2. 发现 MediaStore 根本不索引的隐藏目录 / .nomedia 目录树，让两个开关真正生效。
     * 限制深度与文件数，全程容错，任何异常都不会影响其它数据源。
     */
    @Suppress("DEPRECATION")
    private fun readFileSystem(): List<LibraryItem> {
        if (!hasAllFilesAccess()) return emptyList()
        val includeHidden = LibrarySettings.includeHidden.value
        val includeNoMedia = LibrarySettings.includeNoMedia.value
        val root = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
            ?: return emptyList()
        val known = listOf(
            "DCIM", "Movies", "Music", "Pictures", "Download", "Videos",
            "Recordings", "Documents", "Record", "Camera"
        )
        // 常见媒体目录 + 共享存储根下的一级用户自建目录，兼顾覆盖率与耗时
        val roots = buildList {
            addAll(known.map { File(root, it) }.filter { it.isDirectory })
            runCatching { root.listFiles() }.getOrNull()?.filter {
                it.isDirectory && it.name !in known && it.name != "Android"
            }?.let { addAll(it) }
        }
        val out = mutableListOf<LibraryItem>()
        var visitedDirs = 0

        fun walk(dir: File, depth: Int, hiddenAncestor: Boolean, nomediaAncestor: Boolean) {
            if (depth > 10 || visitedDirs > 1500 || out.size > 8000) return
            visitedDirs++
            val children = runCatching { dir.listFiles() }.getOrNull() ?: return
            val noMediaHere = children.any { it.isFile && it.name.equals(".nomedia", true) }
            val hiddenPath = hiddenAncestor || dir.name.startsWith(".")
            val nomediaPath = nomediaAncestor || noMediaHere
            children.forEach { child ->
                runCatching {
                    when {
                        child.isDirectory -> {
                            if (child.name == "Android" && depth == 0) return@runCatching
                            walk(child, depth + 1, hiddenPath, nomediaPath)
                        }
                        child.isFile && isMediaName(child.name) -> {
                            val hiddenFile = hiddenPath || child.name.startsWith(".")
                            if (!LibraryRules.allowed(hiddenFile, nomediaPath, includeHidden, includeNoMedia)) {
                                return@runCatching
                            }
                            out += LibraryItem(
                                id = "fs_${child.absolutePath}",
                                title = child.name,
                                uri = Uri.fromFile(child),
                                sizeBytes = child.length(),
                                dateMs = child.lastModified(),
                                durationMs = 0L,
                                mime = mimeOf(child.name),
                                folder = dir.name,
                                source = LibrarySource.SCAN,
                                path = child.absolutePath,
                                folderPath = dir.absolutePath,
                                hiddenDir = hiddenFile,
                                nomediaDir = nomediaPath
                            )
                        }
                    }
                }
            }
        }
        roots.forEach { runCatching { walk(it, 0, false, false) } }
        return out
    }

    /** 媒体库只收录音视频；图片由「全部文件」浏览，不再混进三个媒体视图。 */
    private fun isMediaMime(mime: String): Boolean = isAudioVideoMime(mime)

    private fun isMediaName(name: String): Boolean = isMediaMime(mimeOf(name))

    /**
     * 按真实路径补判 .nomedia：MediaStore.Video / Audio / Files 里的行没有 nomediaDir，
     * 取消勾选「包含 .nomedia 文件夹」后这些条目会绕过过滤一直留在媒体库里。
     * 只查到共享存储根，结果按目录缓存；loadStaged() 每次扫描开始时清空缓存。
     */
    private fun nomediaByPath(item: LibraryItem): Boolean {
        val path = item.path?.takeIf { it.isNotBlank() }
            ?: item.folderPath?.takeIf { it.isNotBlank() }
            ?: return false
        return nomediaDirs.getOrPut(path) {
            runCatching { LibraryRules.hasNoMediaMarker(path) }.getOrDefault(false)
        }
    }

    private fun ruleMatches(value: String, item: LibraryItem): Boolean {
        val label = value.trim()
        if (label.isEmpty()) return false
        val targets = listOfNotNull(item.folderPath, item.folder, item.path, item.folderLabel)
        return targets.any { it.contains(label, ignoreCase = true) } ||
            item.folderLabel.substringAfterLast('/').equals(label, ignoreCase = true)
    }

    /** 隐藏 / .nomedia / 极短视频 / 小体积文件 / 黑白名单过滤。 */
    private fun applyRules(items: List<LibraryItem>): List<LibraryItem> {
        val includeHidden = LibrarySettings.includeHidden.value
        val includeNoMedia = LibrarySettings.includeNoMedia.value
        val shortSeconds = LibrarySettings.filterShortSeconds.value
        val minBytes = LibrarySettings.minSizeBytes()
        val black = LibrarySettings.blacklist.value
        val white = LibrarySettings.whitelist.value
        return items.filter { item ->
            // 旧快照 / MediaStore 兜底里可能混进图片或非媒体，统一在发布前再卡一次
            if (!isMediaMime(item.mime)) return@filter false
            val hiddenByName = item.folder.startsWith(".") || item.title.startsWith(".") ||
                item.folderPath?.contains("/.") == true
            if (!LibraryRules.allowed(
                    item.hiddenDir || hiddenByName,
                    item.nomediaDir || nomediaByPath(item),
                    includeHidden, includeNoMedia
                )
            ) return@filter false
            if (shortSeconds > 0 && item.isVideo && item.durationMs > 0 &&
                item.durationMs <= shortSeconds * 1000L
            ) return@filter false
            if (minBytes > 0 && item.sizeBytes > 0 && item.sizeBytes < minBytes) return@filter false
            if (black.any { ruleMatches(it, item) }) return@filter false
            if (white.isNotEmpty() && white.none { ruleMatches(it, item) }) return@filter false
            true
        }
    }

    /** 快阶段：MediaStore 两次查询 + 应用产物，通常几十毫秒即可完成。 */
    private fun readBasic(context: Context): List<LibraryItem> {
        val raw = mutableListOf<LibraryItem>()
        if (hasPermission(context)) {
            raw += runCatching {
                readStore(
                    context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.DURATION, "Videos"
                )
            }.getOrDefault(emptyList())
            raw += runCatching {
                readStore(
                    context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Audio.Media.DURATION, "Music",
                    MediaStore.Audio.Media.ALBUM_ID
                )
            }.getOrDefault(emptyList())
            // MKV / FLAC / TS / AVI 等容器不会进 Video / Audio 集合，
            // 用 Files 集合按 mime 兜底，否则「扫描到的媒体文件不全」。
            // 不再查 Images 集合，mime 兜底也只收 video/* 与 audio/*，
            // 避免相册照片被当成媒体库条目、点开还被塞进视频播放器。
            raw += runCatching {
                readStore(context, allFilesUri(), null, "Files", mimeFilter = true)
            }.getOrDefault(emptyList())
        }
        raw += runCatching { readOutputs() }.getOrDefault(emptyList())
        return raw
    }

    /** 慢阶段：SAF 递归 + 文件系统递归 + 逐个补时长，单独放到后面执行。 */
    private fun readExtended(context: Context): List<LibraryItem> {
        val raw = mutableListOf<LibraryItem>()
        raw += runCatching { readScanPaths(context) }.getOrDefault(emptyList())
        raw += runCatching { readFileSystem() }.getOrDefault(emptyList())
        return raw
    }

    /** 每个数据源单独容错后的统一整理：去重 → 存活校验 → 补时长 → 应用过滤规则。 */
    private fun mergeAndFilter(
        raw: List<LibraryItem>,
        context: Context,
        fillDuration: Boolean
    ): List<LibraryItem> {
        // 同一文件可能同时出现在 MediaStore 与文件系统扫描中：按绝对路径去重，优先保留
        // MediaStore 条目（宽高、时长、专辑信息更全），文件系统条目只用于补齐缺漏。
        val merged = LinkedHashMap<String, LibraryItem>(raw.size)
        raw.forEach { item ->
            val key = item.path?.let { "p:${File(it).absolutePath}" } ?: item.id
            val prev = merged[key]
            if (prev == null || (prev.source != LibrarySource.MEDIA && item.source == LibrarySource.MEDIA)) {
                merged[key] = item
            }
        }

        // 直接 File.delete() 不会立刻移除 MediaStore 陈旧行，必须按真实路径剔除幽灵条目。
        val aliveItems = merged.values.filter(::alive)

        // 非 MediaStore 条目补齐时长；快阶段跳过这一最耗时的步骤，交给慢阶段完成。
        val filled = if (fillDuration) {
            aliveItems.map { item ->
                if (item.durationMs > 0 || (!item.isVideo && !item.isAudio)) item
                else item.copy(durationMs = probeDuration(context, item.uri, item.path))
            }
        } else aliveItems

        return runCatching { applyRules(filled) }.getOrDefault(filled)
    }

    private fun alive(item: LibraryItem): Boolean {
        val p = item.path ?: return true
        return runCatching { File(p).isFile }.getOrDefault(true)
    }

    /**
     * 快阶段只查得到 MediaStore + 应用产物，直接用它发布会先抹掉 .nomedia / 隐藏目录。
     * 这里用上次完整结果打底，让切回媒体库时目录与上次完全一致。
     */
    private fun mergeWithPrevious(
        previous: List<LibraryItem>,
        fresh: List<LibraryItem>
    ): List<LibraryItem> {
        if (previous.isEmpty()) return fresh
        // 上次完整结果也必须按当前规则重筛，否则关掉 .nomedia 开关后旧目录会被带回首屏
        val valid = conforming(previous)
        val carried = LibraryDiff.carryOver(valid.map(::entry), fresh.map(::entry))
        if (carried.isEmpty()) return fresh
        return fresh + valid.filter { it.id in carried }
    }

    private fun publish(items: List<LibraryItem>, allowEmpty: Boolean) {
        // 快阶段若暂时查不到任何媒体，保留已有快照，不用空白覆盖掉刚恢复的内容。
        if (items.isEmpty() && !allowEmpty && _items.value.isNotEmpty()) return
        val safe = conforming(items)
        snapshot = safe
        _items.value = safe
    }

    private fun itemToRow(item: LibraryItem): SnapshotRow = SnapshotRow(
        id = item.id,
        title = item.title,
        uri = item.uri.toString(),
        sizeBytes = item.sizeBytes,
        dateMs = item.dateMs,
        durationMs = item.durationMs,
        mime = item.mime,
        folder = item.folder,
        source = item.source.name,
        albumId = item.albumId,
        path = item.path,
        folderPath = item.folderPath,
        width = item.width,
        height = item.height,
        fps = item.fps,
        hiddenDir = item.hiddenDir,
        nomediaDir = item.nomediaDir
    )

    private fun rowToItem(row: SnapshotRow): LibraryItem? = runCatching {
        LibraryItem(
            id = row.id,
            title = row.title,
            uri = Uri.parse(row.uri),
            sizeBytes = row.sizeBytes,
            dateMs = row.dateMs,
            durationMs = row.durationMs,
            mime = row.mime,
            folder = row.folder,
            source = LibrarySource.valueOf(row.source),
            albumId = row.albumId,
            path = row.path,
            folderPath = row.folderPath,
            width = row.width,
            height = row.height,
            fps = row.fps,
            hiddenDir = row.hiddenDir,
            nomediaDir = row.nomediaDir
        )
    }.getOrNull()

    /** 原子写：先写临时文件再改名，避免扫描中途被清缓存时留下半截文件。 */
    private fun writeSnapshot(context: Context, items: List<LibraryItem>) {
        runCatching {
            val bytes = LibrarySnapshot.encode(items.map(::itemToRow))
            synchronized(snapshotLock) {
                val target = snapshotFile(context)
                val tmp = File(target.parentFile, target.name + ".tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(target)) tmp.delete()
            }
        }
    }

    /**
     * 分阶段扫描并按需发布：
     * 1. 快阶段只查 MediaStore（视频 / 音频 / Files 的音视频 mime 兜底）与应用产物；
     *    用上次完整结果补齐慢阶段目录，切回页面时目录立即与上次一致；
     * 2. 慢阶段补 SAF 扫描路径、文件系统递归与时长，只有结果真有增删或元数据
     *    变化才发布，避免无谓重组；同时刷新新增角标并落盘快照。
     */
    fun loadStaged(context: Context): List<LibraryItem> {
        loadSeenBaseline()
        val generation = loadGeneration.incrementAndGet()
        _loading.value = true
        try {
            // 每次扫描重新确认 .nomedia 标记（扫描在 IO 线程，不阻塞设置页点击）
            nomediaDirs.clear()
            val previous = conforming(lastFull)
            val basic = readBasic(context)
            val stage1 = mergeAndFilter(basic, context, fillDuration = false)
            val interim = conforming(mergeWithPrevious(previous, stage1))
            if (generation == loadGeneration.get() &&
                LibraryDiff.changed(_items.value.map(::entry), interim.map(::entry))
            ) {
                publish(interim, allowEmpty = false)
            }
            val full = mergeAndFilter(basic + readExtended(context), context, fillDuration = true)
            if (generation == loadGeneration.get()) {
                if (LibraryDiff.changed(previous.map(::entry), full.map(::entry))) {
                    publish(full, allowEmpty = true)
                }
                lastFull = full
                if (!LibrarySettings.seenBaseline()) {
                    // 首次扫描（无论有无历史快照）建立已看基线，全部已有文件都不算新增
                    seenIds.addAll(full.map { it.id })
                    LibrarySettings.setSeenBaseline(true)
                    persistSeen()
                }
                recomputeNewCounts()
                writeSnapshot(context, full)
            }
            return full
        } finally {
            if (generation == loadGeneration.get()) _loading.value = false
        }
    }

    /** 只扫描「媒体」数据（文件夹 / 树形 / 文件三个视图共用）。保留给同步调用点。 */
    fun load(context: Context): List<LibraryItem> = loadStaged(context)
}
