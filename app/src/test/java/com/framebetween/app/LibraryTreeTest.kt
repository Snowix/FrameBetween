package com.framebetween.app

import com.framebetween.app.core.LibraryTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 嵌套文件夹视图的目录层级还原与展开逻辑。 */
class LibraryTreeTest {

    private val paths = listOf(
        listOf("DCIM", "Camera", "2026"),
        listOf("DCIM", "Camera"),
        listOf("DCIM"),
        listOf("Movies", "Action"),
        listOf("Movies"),
        listOf("Download")
    )

    @Test
    fun `shared storage prefix is stripped and mother folder becomes first segment`() {
        assertEquals(
            listOf("DCIM", "Camera"),
            LibraryTree.segmentsOf("/storage/emulated/0/DCIM/Camera", null, "Camera", false)
        )
        assertEquals(
            listOf("Movies", "Action"),
            LibraryTree.segmentsOf("primary:Movies/Action", null, "Action", false)
        )
        assertEquals(
            listOf("Music"),
            LibraryTree.segmentsOf(null, "/sdcard/Music/a.mp3", "Music", false)
        )
        assertEquals(
            listOf("FrameBetween"),
            LibraryTree.segmentsOf("/storage/emulated/0/Android/data/x", null, "FrameBetween", true)
        )
    }

    @Test
    fun `root level shows only topmost mother folders`() {
        val nodes = LibraryTree.nodesOf(paths, emptyList())
        assertEquals(listOf("DCIM", "Download", "Movies"), nodes.map { it.name })
        assertEquals(listOf(0, 1, 2), nodes.first { it.name == "DCIM" }.indices)
        assertEquals(0, LibraryTree.directOf(paths, emptyList()).size)
    }

    @Test
    fun `clicking a folder reveals next level folders and direct media`() {
        val dcim = LibraryTree.nodesOf(paths, listOf("DCIM"))
        assertEquals(listOf("Camera"), dcim.map { it.name })
        assertEquals(listOf(2), LibraryTree.directOf(paths, listOf("DCIM")))
        assertEquals(listOf(1), LibraryTree.directOf(paths, listOf("DCIM", "Camera")))
        assertEquals(listOf(0), LibraryTree.nodesOf(paths, listOf("DCIM", "Camera"))
            .first { it.name == "2026" }.indices)
    }

    @Test
    fun `flatten only expands the folders present in expanded set`() {
        val collapsed = LibraryTree.flattenPaths(paths, emptySet())
        assertEquals(3, collapsed.size)
        assertTrue(collapsed.all { it.folder })

        val expanded = LibraryTree.flattenPaths(paths, setOf("DCIM"))
        val rows = expanded.map { it.name to it.folder }
        assertTrue(rows.contains("DCIM" to true))
        assertTrue(rows.contains("" to false))          // DCIM 直属媒体
        assertTrue(rows.contains("Camera" to true))      // 下一级文件夹
        assertEquals(1, expanded.first { !it.folder }.depth)
        assertFalse(expanded.any { !it.folder && it.depth > 1 })

        val deep = LibraryTree.flattenPaths(paths, setOf("DCIM", "DCIM/Camera"))
        assertTrue(deep.any { it.name == "2026" })
        assertEquals(2, deep.count { !it.folder })
    }

    @Test
    fun `unknown path degrades to bucket name instead of an empty segment`() {
        assertEquals(
            listOf("Camera"),
            LibraryTree.segmentsOf(null, null, "Camera", false)
        )
    }
}
