package com.framebetween.app.core

/**
 * 媒体库增量更新的纯逻辑：首屏沿用上次完整结果、只在真有变动时发布、
 * 以及按文件夹统计「尚未查看」的新增媒体数量。
 * 不依赖 Android / Uri，便于 JVM 单元测试直接验证扫描结果的合并行为。
 */
object LibraryDiff {

    data class Entry(
        val id: String,
        /** 去重键：优先真实绝对路径，其次条目 id。 */
        val key: String,
        val folder: String,
        val sizeBytes: Long,
        val dateMs: Long,
        val durationMs: Long,
        val fromMediaStore: Boolean,
        val alive: Boolean = true
    )

    /**
     * 快阶段（MediaStore + 应用产物）覆盖不到 .nomedia / SAF 目录，直接用它发布会
     * 先把这些文件夹抹掉，等慢阶段扫完才重新出现。这里返回上次完整结果里需要补回的
     * 条目 id：只保留仍存活且非 MediaStore 的条目；MediaStore 本次没查到就视为已删除，
     * 避免幽灵文件一直留在列表里。
     */
    fun carryOver(previous: List<Entry>, fresh: List<Entry>): Set<String> {
        if (previous.isEmpty()) return emptySet()
        val freshKeys = fresh.mapTo(HashSet(previous.size + fresh.size)) { it.key }
        return previous.asSequence()
            .filter { it.alive && !it.fromMediaStore && it.key !in freshKeys }
            .mapTo(LinkedHashSet()) { it.id }
    }

    /** 内容完全一致时不发布，避免切换导航触发无意义重组与闪烁。 */
    fun changed(previous: List<Entry>, current: List<Entry>): Boolean {
        if (previous.size != current.size) return true
        val before = previous.associateBy { it.id }
        return current.any { cur ->
            val old = before[cur.id] ?: return@any true
            old.sizeBytes != cur.sizeBytes || old.dateMs != cur.dateMs ||
                old.durationMs != cur.durationMs || old.folder != cur.folder ||
                old.key != cur.key
        }
    }

    /** 按文件夹统计尚未被用户打开过的新增条目，供图标右上角角标显示。 */
    fun newCounts(current: List<Entry>, seenIds: Set<String>): Map<String, Int> =
        current.asSequence()
            .filter { it.id !in seenIds }
            .groupBy { it.folder }
            .mapValues { (_, list) -> list.size }
}
