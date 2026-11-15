package com.framebetween.app

import com.framebetween.app.core.AiConfig
import com.framebetween.app.util.outputFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OutputNameTest {

    private val fixedTime = 1_730_000_000_000L

    private fun stamp(): String =
        SimpleDateFormat("MMddHHmmss", Locale.US).format(Date(fixedTime))

    @Test
    fun `original name keeps chinese characters and strips extension`() {
        val name = outputFileName("我的旅行视频.mp4", "mp4", fixedTime)
        assertEquals("我的旅行视频_${stamp()}.mp4", name)
    }

    @Test
    fun `timestamp uses MMddHHmmss and extension is lowercase`() {
        val name = outputFileName("clip.MOV", "MKV", fixedTime)
        assertEquals("clip_${stamp()}.mkv", name)
    }

    @Test
    fun `illegal filesystem characters and spaces are replaced`() {
        assertEquals(
            "a_b_c_d_e__${stamp()}.mp4",
            outputFileName("a/b:c d*e?.mp4", "mp4", fixedTime)
        )
    }

    @Test
    fun `blank or missing name falls back and default extension is mp4`() {
        assertEquals("FrameBetween_${stamp()}.mp4", outputFileName(null, "", fixedTime))
        assertEquals("FrameBetween_${stamp()}.gif", outputFileName("", "gif", fixedTime))
    }

    @Test
    fun `long names are truncated to sixty characters`() {
        val name = outputFileName("x".repeat(120) + ".mp4", "mp4", fixedTime)
        val base = name.substringBeforeLast("_${stamp()}.mp4")
        assertEquals(60, base.length)
    }

    @Test
    fun `timestamp comes from the supplied timeMs and keeps the original name`() {
        val t1 = 1_730_000_000_000L
        val t2 = 1_700_000_000_000L
        val fmt = SimpleDateFormat("MMddHHmmss", Locale.US)
        val name1 = outputFileName("旅行.MP4", "MKV", t1)
        val name2 = outputFileName("旅行.MP4", "MKV", t2)
        assertEquals("旅行_${fmt.format(Date(t1))}.mkv", name1)
        assertEquals("旅行_${fmt.format(Date(t2))}.mkv", name2)
        // 两个不同的 timeMs 必须得到不同的时间戳，证明取自入参而非当前时钟
        assertTrue(name1 != name2)
    }

    @Test
    fun `local model config is valid without an api key`() {
        val local = AiConfig("http://192.168.1.100:11434/v1", "", "qwen2.5:7b", "Ollama")
        assertTrue(local.isValid)
        assertFalse(local.needsKey)
    }

    @Test
    fun `cloud config requires endpoint and model`() {
        assertTrue(
            AiConfig("https://api.openai.com/v1", "sk-test", "gpt-4o-mini", "OpenAI").isValid
        )
        assertFalse(AiConfig("", "sk-test", "gpt-4o-mini", "OpenAI").isValid)
        assertFalse(AiConfig("https://api.openai.com/v1", "", "", "OpenAI").isValid)
    }
}
