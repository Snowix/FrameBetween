package com.framebetween.app.core

/**
 * 文件管理模式下的一个目录项。纯数据类，不含 Android 依赖，便于 JVM 单元测试。
 * 与媒体库的 LibraryItem 分开：这里不做媒体扫描、不查 MediaStore、不套用媒体过滤规则。
 */
data class FsEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val dateMs: Long = 0L
)

/** 「全部文件」视图右上角排序菜单的可选维度。 */
enum class FsSortKey { NAME, DATE, SIZE, TYPE }

/**
 * 目录优先、其次按所选维度排序。目录优先始终生效（降序只反转同一分组内的顺序），
 * 名称比较忽略大小写并在最后兜底，保证排序结果稳定、不会因切换视图抖动。
 */
object FsEntrySort {
    fun sort(
        entries: List<FsEntry>,
        key: FsSortKey = FsSortKey.NAME,
        descending: Boolean = false,
        directoriesFirst: Boolean = true
    ): List<FsEntry> {
        val cmp = Comparator<FsEntry> { a, b ->
            if (directoriesFirst && a.isDirectory != b.isDirectory) {
                return@Comparator if (a.isDirectory) -1 else 1
            }
            val primary = when (key) {
                FsSortKey.NAME -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
                FsSortKey.DATE -> a.dateMs.compareTo(b.dateMs)
                FsSortKey.SIZE -> a.sizeBytes.compareTo(b.sizeBytes)
                FsSortKey.TYPE -> extension(a.name).compareTo(extension(b.name), ignoreCase = true)
            }
            val result = if (primary != 0) {
                primary
            } else {
                String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
                    .takeIf { it != 0 } ?: a.name.compareTo(b.name)
            }
            if (descending) -result else result
        }
        return entries.sortedWith(cmp)
    }

    /** 无扩展名（或点号在末尾）统一归为空串，目录也归入这一类。 */
    private fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot < 0 || dot == name.lastIndex) "" else name.substring(dot + 1)
    }
}
