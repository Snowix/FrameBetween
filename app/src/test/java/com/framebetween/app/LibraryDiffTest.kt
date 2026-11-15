package com.framebetween.app

import com.framebetween.app.core.LibraryDiff
import com.framebetween.app.core.LibraryDiff.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 媒体库首屏增量显示 / 变动判定 / 新增角标的纯逻辑验证。 */
class LibraryDiffTest {

    private fun e(
        id: String,
        folder: String = "DCIM",
        media: Boolean = true,
        date: Long = 1L,
        alive: Boolean = true
    ) = Entry(id, "p:$id", folder, 100L, date, 0L, media, alive)

    @Test
    fun `fast stage carries previous scan-only folders into the first frame`() {
        val previous = listOf(
            e("hidden", folder = "Private", media = false),
            e("a")
        )
        val fresh = listOf(e("a"))
        assertEquals(setOf("hidden"), LibraryDiff.carryOver(previous, fresh))
    }

    @Test
    fun `carry over drops deleted files and missing MediaStore rows`() {
        val previous = listOf(
            e("store-gone"),
            e("gone", folder = "Private", media = false, alive = false)
        )
        assertEquals(emptySet<String>(), LibraryDiff.carryOver(previous, emptyList()))
    }

    @Test
    fun `changed is false for identical scans and true for real differences`() {
        val before = listOf(e("a"), e("b"))
        assertFalse(LibraryDiff.changed(before, listOf(e("a"), e("b"))))
        assertTrue(LibraryDiff.changed(before, listOf(e("a"))))
        assertTrue(LibraryDiff.changed(before, listOf(e("a"), e("c"))))
        assertTrue(LibraryDiff.changed(before, listOf(e("a"), e("b", date = 2L))))
    }

    @Test
    fun `new counts group by folder and disappear once seen`() {
        val current = listOf(
            e("a", folder = "DCIM"),
            e("b", folder = "DCIM"),
            e("c", folder = "Movies")
        )
        assertEquals(mapOf("DCIM" to 2, "Movies" to 1), LibraryDiff.newCounts(current, emptySet()))
        assertEquals(mapOf("Movies" to 1), LibraryDiff.newCounts(current, setOf("a", "b")))
        assertTrue(LibraryDiff.newCounts(current, current.map { it.id }.toSet()).isEmpty())
    }
}
