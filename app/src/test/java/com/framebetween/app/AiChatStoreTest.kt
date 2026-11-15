package com.framebetween.app

import com.framebetween.app.core.AiChatStore
import com.framebetween.app.core.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 连续对话存储：持久化之前的内存行为可在 JVM 单测里直接验证。 */
class AiChatStoreTest {

    @Before
    fun reset() {
        AiChatStore.clear()
    }

    @Test
    fun `display differs from content but both are kept`() {
        AiChatStore.append(ChatMessage("user", "TEMPLATE + ffprobe", "把视频压缩小一点"))
        val item = AiChatStore.messages.value.single()
        assertEquals("TEMPLATE + ffprobe", item.content)
        assertEquals("把视频压缩小一点", item.display)
    }

    @Test
    fun `history uses content so the model sees the real prompt`() {
        AiChatStore.append(
            ChatMessage("user", "full prompt", "short question"),
            ChatMessage("assistant", "answer text")
        )
        val history = AiChatStore.history()
        assertEquals(listOf("user", "assistant"), history.map { it.role })
        assertEquals("full prompt", history[0].content)
        assertEquals("answer text", history[1].content)
    }

    @Test
    fun `clear removes every message`() {
        AiChatStore.append(ChatMessage("user", "a"), ChatMessage("assistant", "b"))
        assertEquals(2, AiChatStore.messages.value.size)
        AiChatStore.clear()
        assertTrue(AiChatStore.messages.value.isEmpty())
        assertTrue(AiChatStore.history().isEmpty())
    }

    @Test
    fun `store keeps at most 120 messages`() {
        repeat(130) { index ->
            AiChatStore.append(ChatMessage("user", "m$index"))
        }
        assertEquals(120, AiChatStore.messages.value.size)
        assertEquals("m10", AiChatStore.messages.value.first().content)
        assertEquals("m129", AiChatStore.messages.value.last().content)
    }

    @Test
    fun `default display equals content`() {
        val item = ChatMessage("assistant", "raw reply")
        assertEquals(item.content, item.display)
    }
}
