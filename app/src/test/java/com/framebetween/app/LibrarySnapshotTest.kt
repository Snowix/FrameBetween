package com.framebetween.app

import com.framebetween.app.core.LibrarySnapshot
import com.framebetween.app.core.SnapshotRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySnapshotTest {

    private fun row(id: String, path: String? = null) = SnapshotRow(
        id = id,
        title = "视频 名字 $id.mp4",
        uri = "content://media/external/video/$id",
        sizeBytes = 12_345_678L,
        dateMs = 1_700_000_000_000L,
        durationMs = 61_234L,
        mime = "video/mp4",
        folder = "DCIM",
        source = "MEDIA",
        albumId = 42L,
        path = path,
        folderPath = "/storage/emulated/0/DCIM",
        width = 1920,
        height = 1080,
        fps = 29.97,
        hiddenDir = false,
        nomediaDir = true
    )

    @Test
    fun `round trip keeps every field`() {
        val rows = listOf(row("1", "/sdcard/DCIM/a.mp4"), row("2", null))
        assertEquals(rows, LibrarySnapshot.decode(LibrarySnapshot.encode(rows)))
    }

    @Test
    fun `weird characters in paths survive`() {
        val tricky = row("3", "/sdcard/我的 视频\t带,逗号\n换行/剪辑.mp4")
        assertEquals(tricky, LibrarySnapshot.decode(LibrarySnapshot.encode(listOf(tricky))).single())
    }

    @Test
    fun `empty input round trips`() {
        assertTrue(LibrarySnapshot.decode(LibrarySnapshot.encode(emptyList())).isEmpty())
    }

    @Test
    fun `corrupt or foreign files degrade to empty`() {
        assertTrue(LibrarySnapshot.decode(ByteArray(0)).isEmpty())
        assertTrue(LibrarySnapshot.decode("not a snapshot".toByteArray()).isEmpty())
        val valid = LibrarySnapshot.encode(listOf(row("4")))
        assertTrue(LibrarySnapshot.decode(valid.copyOf(valid.size - 6)).isEmpty())
        val wrongMagic = valid.copyOf()
        wrongMagic[0] = 0
        assertTrue(LibrarySnapshot.decode(wrongMagic).isEmpty())
    }

    @Test
    fun `row count is capped`() {
        val many = (1..LibrarySnapshot.MAX_ROWS + 20).map { row(it.toString()) }
        val decoded = LibrarySnapshot.decode(LibrarySnapshot.encode(many))
        assertEquals(LibrarySnapshot.MAX_ROWS, decoded.size)
    }
}
