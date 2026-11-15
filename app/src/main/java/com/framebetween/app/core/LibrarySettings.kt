package com.framebetween.app.core

import android.content.Context
import android.content.SharedPreferences
import com.framebetween.app.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class SortKey { TITLE, DATE, SIZE }
/** 只描述「扫描出来的媒体」如何组织；「全部文件」是独立的文件管理模式，不参与此枚举。 */
enum class ViewMode { FOLDER, TREE, FILE }
enum class LayoutMode { LIST, GRID }

/**
 * 媒体库偏好与文件夹规则：全部落到 SharedPreferences，切换导航或重启进程都不丢失。
 * [scanVersion] 作为重新扫描信号，媒体库页收集它触发重读。
 * 过滤阈值统一用滑块数值：短视频 0–60 秒、小体积 0–5.0 MB，0 表示不限制。
 */
object LibrarySettings {

    private const val PREF = "ffstudio_library"

    /** 0.1 MB 为一个滑块刻度，取值 0–50 对应 0–5.0 MB。 */
    const val MAX_SIZE_TENTHS_MB = 50
    const val MAX_SHORT_SECONDS = 60

    private lateinit var prefs: SharedPreferences

    private val _sortKey = MutableStateFlow(SortKey.DATE)
    val sortKey: StateFlow<SortKey> = _sortKey.asStateFlow()

    private val _descending = MutableStateFlow(true)
    val descending: StateFlow<Boolean> = _descending.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.FILE)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _layout = MutableStateFlow(LayoutMode.LIST)
    val layout: StateFlow<LayoutMode> = _layout.asStateFlow()

    private val _gridCount = MutableStateFlow(2)
    val gridCount: StateFlow<Int> = _gridCount.asStateFlow()

    /** 文件夹 / 树形视图专用：按真实目录层级逐级展开，而不是平铺所有文件夹。 */
    private val _nestedFolders = MutableStateFlow(false)
    val nestedFolders: StateFlow<Boolean> = _nestedFolders.asStateFlow()

    private val _includeNoMedia = MutableStateFlow(false)
    val includeNoMedia: StateFlow<Boolean> = _includeNoMedia.asStateFlow()

    private val _includeHidden = MutableStateFlow(false)
    val includeHidden: StateFlow<Boolean> = _includeHidden.asStateFlow()

    /** 隐藏时长不超过该秒数的视频；0 表示不过滤。 */
    private val _filterShortSeconds = MutableStateFlow(0)
    val filterShortSeconds: StateFlow<Int> = _filterShortSeconds.asStateFlow()

    /** 隐藏体积小于该阈值的文件；单位 0.1 MB，0 表示不过滤。 */
    private val _minSizeTenthsMb = MutableStateFlow(0)
    val minSizeTenthsMb: StateFlow<Int> = _minSizeTenthsMb.asStateFlow()

    private val _blacklist = MutableStateFlow<Set<String>>(emptySet())
    val blacklist: StateFlow<Set<String>> = _blacklist.asStateFlow()

    private val _whitelist = MutableStateFlow<Set<String>>(emptySet())
    val whitelist: StateFlow<Set<String>> = _whitelist.asStateFlow()

    private val _scanPaths = MutableStateFlow<Set<String>>(emptySet())
    val scanPaths: StateFlow<Set<String>> = _scanPaths.asStateFlow()

    private val _scanVersion = MutableStateFlow(0)
    val scanVersion: StateFlow<Int> = _scanVersion.asStateFlow()

    /** 缩略图 / 元数据磁盘缓存目录，供「清空元数据缓存」直接回收。 */
    fun thumbDir(): File = File(App.instance.cacheDir, "thumbs")

    fun minSizeBytes(): Long = _minSizeTenthsMb.value * 1024L * 1024L / 10L

    /** 可传入滑块草稿值，让数值在拖动过程中即时显示。 */
    fun minSizeLabel(tenths: Int = _minSizeTenthsMb.value): String = if (tenths == 0) {
        L10n.t("不限制")
    } else {
        "%.1f MB".format(java.util.Locale.US, tenths / 10.0)
    }

    /** 可传入滑块草稿值，让秒数在拖动过程中即时显示。 */
    fun shortLabel(seconds: Int = _filterShortSeconds.value): String = if (seconds == 0) {
        L10n.t("不限制")
    } else {
        L10n.t("%1\$s 秒", seconds)
    }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        _sortKey.value = enumOr("sort", SortKey.DATE)
        _descending.value = prefs.getBoolean("descending", true)
        _viewMode.value = enumOr("view", ViewMode.FILE)
        _layout.value = enumOr("layout", LayoutMode.LIST)
        _gridCount.value = prefs.getInt("grid", 2).coerceIn(2, 3)
        _nestedFolders.value = prefs.getBoolean("nested", false)
        _includeNoMedia.value = prefs.getBoolean("nomedia", false)
        _includeHidden.value = prefs.getBoolean("hidden", false)
        // 兼容早期布尔开关：开启时等价于 2 秒阈值
        _filterShortSeconds.value = if (prefs.contains("short_sec")) {
            prefs.getInt("short_sec", 0)
        } else if (prefs.getBoolean("short", false)) 2 else 0
        _minSizeTenthsMb.value = prefs.getInt("min_size", 0)
        _blacklist.value = strings("blacklist")
        _whitelist.value = strings("whitelist")
        _scanPaths.value = strings("scan_paths")
    }

    private inline fun <reified T : Enum<T>> enumOr(key: String, fallback: T): T = runCatching {
        enumValueOf<T>(prefs.getString(key, fallback.name) ?: fallback.name)
    }.getOrDefault(fallback)

    private fun strings(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        if (!::prefs.isInitialized) return
        val editor = prefs.edit()
        editor.block()
        editor.apply()
    }

    /** 已打开过文件夹里的文件 id；持久化到磁盘，避免冷启动后 seenIds 为空导致满屏红点。 */
    fun seenIds(): Set<String> {
        if (!::prefs.isInitialized) return emptySet()
        return prefs.getStringSet("seen_ids", emptySet())?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    /** 限制规模，避免已删除文件 id 无限堆积撑大 SharedPreferences。 */
    fun saveSeenIds(ids: Set<String>) {
        edit { putStringSet("seen_ids", ids.filter { it.isNotBlank() }.take(20000).toSet()) }
    }

    /** 是否已完成首次扫描基线：未建立前绝不显示新增角标。 */
    fun seenBaseline(): Boolean =
        if (!::prefs.isInitialized) false else prefs.getBoolean("seen_baseline", false)

    fun setSeenBaseline(value: Boolean) {
        edit { putBoolean("seen_baseline", value) }
    }

    fun setSortKey(value: SortKey) {
        _sortKey.value = value
        edit { putString("sort", value.name) }
    }

    fun setDescending(value: Boolean) {
        _descending.value = value
        edit { putBoolean("descending", value) }
    }

    fun setViewMode(value: ViewMode) {
        _viewMode.value = value
        edit { putString("view", value.name) }
    }

    fun setLayout(value: LayoutMode) {
        _layout.value = value
        edit { putString("layout", value.name) }
    }

    fun setGridCount(value: Int) {
        val safe = value.coerceIn(2, 3)
        _gridCount.value = safe
        edit { putInt("grid", safe) }
    }

    fun setNestedFolders(value: Boolean) {
        _nestedFolders.value = value
        edit { putBoolean("nested", value) }
    }

    fun setIncludeNoMedia(value: Boolean) {
        _includeNoMedia.value = value
        edit { putBoolean("nomedia", value) }
        rescan()
    }

    fun setIncludeHidden(value: Boolean) {
        _includeHidden.value = value
        edit { putBoolean("hidden", value) }
        rescan()
    }

    fun setFilterShortSeconds(value: Int) {
        val safe = value.coerceIn(0, MAX_SHORT_SECONDS)
        _filterShortSeconds.value = safe
        edit { putInt("short_sec", safe); putBoolean("short", safe > 0) }
        rescan()
    }

    fun setMinSizeTenthsMb(value: Int) {
        val safe = value.coerceIn(0, MAX_SIZE_TENTHS_MB)
        _minSizeTenthsMb.value = safe
        edit { putInt("min_size", safe) }
        rescan()
    }

    /** 黑白名单保存的是用户从系统文件夹选择器选中的文件夹路径（或可读名称）。 */
    fun setBlacklist(value: Set<String>) {
        _blacklist.value = value
        edit { putStringSet("blacklist", value) }
        rescan()
    }

    fun setWhitelist(value: Set<String>) {
        _whitelist.value = value
        edit { putStringSet("whitelist", value) }
        rescan()
    }

    fun setScanPaths(value: Set<String>) {
        _scanPaths.value = value
        edit { putStringSet("scan_paths", value) }
        rescan()
    }

    fun addScanPath(value: String) {
        if (value.isBlank()) return
        setScanPaths(_scanPaths.value + value)
    }

    /** 重新扫描：丢弃内存中的媒体清单，通知媒体库页重新读取。 */
    fun rescan() {
        MediaLibrary.invalidate()
        _scanVersion.value++
    }

    /** 清空元数据缓存：内存缩略图 + 磁盘缩略图文件全部回收，并触发重扫。 */
    fun clearMetadataCache(): Long {
        var freed = 0L
        thumbDir().walkBottomUp().forEach { f ->
            if (f.isFile) {
                freed += f.length()
                runCatching { f.delete() }
            }
        }
        runCatching { thumbDir().delete() }
        MediaThumbnails.clearMemory()
        MediaLibrary.invalidate()
        _scanVersion.value++
        return freed
    }
}
