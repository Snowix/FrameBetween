package com.framebetween.app.core

/**
 * 「嵌套文件夹」视图的目录树：把扁平的扫描结果按真实路径还原成层级。
 *
 * 核心算法只处理 List<List<String>>（每个条目从母文件夹开始的目录段），
 * 不碰 LibraryItem / Uri，因此 JVM 单元测试可以直接验证层级拆分与展开行为。
 * UI 拿到的是条目下标，再映射回真实的媒体列表。
 *
 * 开启后页面只显示最顶层母文件夹（DCIM / Movies / Download …），点一级进一级；
 * 树形模式则在同一列表里逐级展开，展开后同时列出该层直属的音视频。
 */
object LibraryTree {

    /** 当前层的一个子文件夹；[indices] 指向它下面所有后代媒体（含更深层级）。 */
    data class Node(val key: String, val name: String, val indices: List<Int>)

    /** 树形摊平后的一行：[folder] 为真是目录节点，否则是一组直属媒体。 */
    data class Row(
        val key: String,
        val name: String,
        val depth: Int,
        val folder: Boolean,
        val indices: List<Int>
    )

    private val STORAGE_PREFIXES = listOf(
        "storage/emulated/0/", "storage/emulated/1/", "storage/emulated/",
        "sdcard/", "mnt/sdcard/", "storage/"
    )

    fun keyOf(path: List<String>): String = path.joinToString("/")

    /**
     * 把条目还原成从「最顶层母文件夹」开始的目录段。
     * - 应用产物单独归到 FrameBetween（真实路径在 Android/data 私有目录下，层级对用户没意义）；
     * - 共享存储前缀与 SAF 的 `primary:` 前缀剥掉，剩下的第一段就是母文件夹；
     * - 完全没有路径可读时退回 MediaStore 的 bucket 名称。
     */
    fun segmentsOf(
        folderPath: String?,
        filePath: String?,
        folder: String,
        output: Boolean
    ): List<String> {
        if (output) return listOf(folder.ifBlank { "FrameBetween" })
        val raw = (folderPath ?: filePath?.substringBeforeLast('/') ?: folder)
            .replace('\\', '/')
        var text = raw.trim().trim('/')
        val colon = text.indexOf(':')
        if (colon in 0..12) text = text.substring(colon + 1)
        for (prefix in STORAGE_PREFIXES) {
            if (text.startsWith(prefix, ignoreCase = true)) {
                text = text.removePrefix(prefix)
                break
            }
        }
        val parts = text.split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." }
        return parts.ifEmpty { listOf(folder.ifBlank { "FrameBetween" }) }
    }

    fun segments(item: LibraryItem): List<String> = segmentsOf(
        item.folderPath, item.path, item.folder, item.source == LibrarySource.OUTPUT
    )

    private fun matches(segs: List<String>, path: List<String>): Boolean =
        segs.size >= path.size && path.indices.all { segs[it] == path[it] }

    /** 当前层的直接子文件夹节点，按名称排序。 */
    fun nodesOf(paths: List<List<String>>, path: List<String>): List<Node> {
        val grouped = LinkedHashMap<String, MutableList<Int>>()
        paths.forEachIndexed { index, segs ->
            if (segs.size <= path.size || !matches(segs, path)) return@forEachIndexed
            grouped.getOrPut(segs[path.size]) { mutableListOf() }.add(index)
        }
        return grouped.map { (name, indices) ->
            Node(keyOf(path + name), name, indices)
        }.sortedBy { it.name.lowercase() }
    }

    /** 直接位于当前层的媒体文件下标（没有更深一级目录）。 */
    fun directOf(paths: List<List<String>>, path: List<String>): List<Int> =
        paths.mapIndexedNotNull { index, segs ->
            index.takeIf { segs.size == path.size && matches(segs, path) }
        }

    /**
     * 树形视图：按展开状态把整棵树摊平成一行行。展开的目录先出现直属媒体，
     * 再递归出现下一级子目录，因此「点击可展开下一级文件夹和音视频」。
     */
    fun flattenPaths(paths: List<List<String>>, expanded: Set<String>): List<Row> {
        val rows = mutableListOf<Row>()
        fun visit(path: List<String>, depth: Int) {
            nodesOf(paths, path).forEach { node ->
                val childPath = path + node.name
                rows += Row(node.key, node.name, depth, folder = true, indices = node.indices)
                if (node.key in expanded) {
                    val direct = directOf(paths, childPath)
                    if (direct.isNotEmpty()) {
                        rows += Row(
                            "files_${node.key}", "", depth + 1, folder = false, indices = direct
                        )
                    }
                    visit(childPath, depth + 1)
                }
            }
        }
        visit(emptyList(), 0)
        return rows
    }

    /** 目录节点的新增角标：按叶子文件夹汇总，避免同一文件夹被重复计数。 */
    fun newCount(nodeFiles: List<LibraryItem>, newCounts: Map<String, Int>): Int =
        nodeFiles.map { it.folder }.distinct().sumOf { newCounts[it] ?: 0 }
}
