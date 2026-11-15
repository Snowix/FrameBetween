package com.framebetween.app

import com.framebetween.app.core.FsEntry
import com.framebetween.app.core.FsEntrySort
import com.framebetween.app.core.FsSortKey
import com.framebetween.app.core.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 「全部文件」按目录逐层浏览，不扫描媒体。 */
class FsEntryTest {

    @Test
    fun `directories come first and names sort case insensitively`() {
        val entries = listOf(
            FsEntry("zeta.mp4", "/z/zeta.mp4", false),
            FsEntry("beta", "/z/beta", true),
            FsEntry("Alpha", "/z/Alpha", true),
            FsEntry("alpha.mp3", "/z/alpha.mp3", false),
            FsEntry("Beta.txt", "/z/Beta.txt", false)
        )
        assertEquals(
            listOf("Alpha", "beta", "alpha.mp3", "Beta.txt", "zeta.mp4"),
            FsEntrySort.sort(entries).map { it.name }
        )
    }

    @Test
    fun `file sort supports date size type descending and folder priority`() {
        val entries = listOf(
            FsEntry("b.mp4", "/b.mp4", false, 100L, 300L),
            FsEntry("a.zip", "/a.zip", false, 900L, 100L),
            FsEntry("c.txt", "/c.txt", false, 50L, 200L),
            FsEntry("folder", "/folder", true, 0L, 999L)
        )
        assertEquals(
            listOf("folder", "a.zip", "c.txt", "b.mp4"),
            FsEntrySort.sort(entries, FsSortKey.DATE).map { it.name }
        )
        assertEquals(
            listOf("folder", "c.txt", "b.mp4", "a.zip"),
            FsEntrySort.sort(entries, FsSortKey.SIZE).map { it.name }
        )
        assertEquals(
            listOf("folder", "b.mp4", "c.txt", "a.zip"),
            FsEntrySort.sort(entries, FsSortKey.TYPE).map { it.name }
        )
        assertEquals(
            listOf("folder", "c.txt", "b.mp4", "a.zip"),
            FsEntrySort.sort(entries, FsSortKey.NAME, descending = true).map { it.name }
        )
        assertEquals(
            listOf("a.zip", "b.mp4", "c.txt", "folder"),
            FsEntrySort.sort(entries, FsSortKey.NAME, directoriesFirst = false).map { it.name }
        )
    }

    @Test
    fun `entry keeps raw file facts without media filtering`() {
        val entry = FsEntry("archive.zip", "/sdcard/archive.zip", false, 42L, 1700L)
        assertFalse(entry.isDirectory)
        assertEquals(42L, entry.sizeBytes)
        assertEquals("archive.zip", entry.name)
    }

    @Test
    fun `parent path walks up but never leaves storage volumes`() {
        val root = "/storage/emulated/0"
        val volumes = "/storage"
        assertEquals(
            "$root/DCIM",
            FileSystem.parentPath("$root/DCIM/Camera", root, volumes)
        )
        assertEquals(root, FileSystem.parentPath("$root/DCIM", root, volumes))
        assertNull(FileSystem.parentPath(volumes, root, volumes))
        assertNull(FileSystem.parentPath("/", root, volumes))
        assertEquals(volumes, FileSystem.parentPath(root, root, volumes))
    }
}
