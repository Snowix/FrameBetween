package com.framebetween.app

import com.framebetween.app.core.LibraryRules
import com.framebetween.app.util.isAudioVideoMime
import com.framebetween.app.util.mimeOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 隐藏目录 / .nomedia 目录树两个开关的收录规则。 */
class LibraryRulesTest {

    @Test
    fun `normal file is always visible`() {
        assertTrue(LibraryRules.allowed(false, false, false, false))
        assertTrue(LibraryRules.allowed(false, false, true, true))
    }

    @Test
    fun `hidden folder only visible when includeHidden is on`() {
        assertFalse(LibraryRules.allowed(true, false, false, false))
        assertFalse(LibraryRules.allowed(true, false, false, true))
        assertTrue(LibraryRules.allowed(true, false, true, false))
        assertTrue(LibraryRules.allowed(true, false, true, true))
    }

    @Test
    fun `nomedia tree only visible when includeNoMedia is on`() {
        assertFalse(LibraryRules.allowed(false, true, false, false))
        assertFalse(LibraryRules.allowed(false, true, true, false))
        assertTrue(LibraryRules.allowed(false, true, false, true))
        assertTrue(LibraryRules.allowed(false, true, true, true))
    }

    @Test
    fun `hidden file inside nomedia tree needs either switch`() {
        assertFalse(LibraryRules.allowed(true, true, false, false))
        assertTrue(LibraryRules.allowed(true, true, true, false))
        assertTrue(LibraryRules.allowed(true, true, false, true))
        assertTrue(LibraryRules.allowed(true, true, true, true))
    }

    @Test
    fun `a nomedia marker in the file folder hides MediaStore rows too`() {
        val root = Files.createTempDirectory("framebetween-nomedia").toFile()
        try {
            val marked = File(root, "marked").apply { mkdirs() }
            val child = File(marked, "child").apply { mkdirs() }
            val movie = File(child, "a.mp4").apply { writeText("x") }
            File(marked, ".nomedia").writeText("")
            assertTrue(LibraryRules.hasNoMediaMarker(movie.absolutePath))
            assertTrue(LibraryRules.hasNoMediaMarker(child.absolutePath))
            // 关掉开关后，即使条目本身没带 nomediaDir 标记也必须被过滤
            assertFalse(
                LibraryRules.allowed(false, LibraryRules.hasNoMediaMarker(movie.path), false, false)
            )
            assertTrue(
                LibraryRules.allowed(false, LibraryRules.hasNoMediaMarker(movie.path), false, true)
            )
            val clean = File(root, "clean").apply { mkdirs() }
            val cleanMovie = File(clean, "b.mp4").apply { writeText("x") }
            assertFalse(LibraryRules.hasNoMediaMarker(cleanMovie.absolutePath))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `library accepts audio video containers but rejects images`() {
        listOf("mkv", "ts", "flac", "m2ts", "avi", "mp3", "mp4", "wma", "mka")
            .forEach { assertTrue("$it", isAudioVideoMime(mimeOf("a.$it"))) }
        listOf("jpg", "png", "webp", "heic", "gif", "bmp", "tif")
            .forEach { assertFalse("$it", isAudioVideoMime(mimeOf("a.$it"))) }
        assertFalse(isAudioVideoMime("application/octet-stream"))
        assertFalse(isAudioVideoMime("image/jpeg"))
    }

    @Test
    fun `rules are symmetric for all sixteen switch combinations`() {
        val switches = listOf(false to false, false to true, true to false, true to true)
        val classes = listOf(false to false, true to false, false to true, true to true)
        classes.forEach { (hidden, nomedia) ->
            switches.forEach { (includeHidden, includeNoMedia) ->
                val got = LibraryRules.allowed(hidden, nomedia, includeHidden, includeNoMedia)
                val expected = when {
                    !hidden && !nomedia -> true
                    hidden && nomedia -> includeHidden || includeNoMedia
                    hidden -> includeHidden
                    else -> includeNoMedia
                }
                assertTrue(
                    "hidden=$hidden nomedia=$nomedia ih=$includeHidden inm=$includeNoMedia",
                    got == expected
                )
            }
        }
    }
}
