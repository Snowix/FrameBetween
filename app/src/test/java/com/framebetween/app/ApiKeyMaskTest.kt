package com.framebetween.app

import com.framebetween.app.util.ApiKeyMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** API 密钥展示脱敏——首尾各留 4 位，中间等长星号，长度不变。 */
class ApiKeyMaskTest {

    @Test
    fun `empty key stays empty`() {
        assertEquals("", ApiKeyMask.mask(""))
    }

    @Test
    fun `keeps first and last four characters by default`() {
        assertEquals("1234**7890", ApiKeyMask.mask("1234567890"))
        assertEquals("sk-1****cdef", ApiKeyMask.mask("sk-12345cdef"))
    }

    @Test
    fun `short keys are fully hidden instead of revealing everything`() {
        assertEquals("*******", ApiKeyMask.mask("1234567"))
        assertEquals("********", ApiKeyMask.mask("12345678"))
        assertEquals("1234*6789", ApiKeyMask.mask("123456789"))
    }

    @Test
    fun `masked text always keeps the original length`() {
        listOf("", "a", "12345678", "123456789", "sk-1234567890abcdef", "密 钥 with spaces!").forEach {
            assertEquals(it.length, ApiKeyMask.mask(it).length)
        }
    }

    @Test
    fun `visible width is configurable`() {
        assertEquals("12******90", ApiKeyMask.mask("1234567890", 2))
        assertEquals("1********0", ApiKeyMask.mask("1234567890", 1))
    }

    @Test
    fun `zero visible width hides everything`() {
        assertEquals("**********", ApiKeyMask.mask("1234567890", 0))
    }

    @Test
    fun `only head and tail survive masking`() {
        val raw = "sk-1234567890abcdef"
        val masked = ApiKeyMask.mask(raw)
        assertEquals(raw.take(4), masked.take(4))
        assertEquals(raw.takeLast(4), masked.takeLast(4))
        assertTrue(masked.substring(4, masked.length - 4).all { it == '*' })
    }
}
