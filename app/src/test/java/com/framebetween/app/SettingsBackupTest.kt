package com.framebetween.app

import com.framebetween.app.core.SettingsBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    @Test
    fun `round trip keeps all supported value types`() {
        val groups = linkedMapOf<String, Map<String, Any?>>(
            "ffstudio_settings" to linkedMapOf(
                "theme" to "DARK",
                "hardware_codec" to true,
                "grid" to 3,
                "seen_ids" to setOf("a&b", "<c>", "中文")
            ),
            "ffstudio_ai" to linkedMapOf("provider" to "deepseek", "api_key" to "k\"1")
        )
        val xml = SettingsBackup.encode(groups)
        assertTrue(xml.contains("<framebetween-settings"))
        val restored = SettingsBackup.decode(xml)
        assertEquals("DARK", restored["ffstudio_settings"]?.get("theme"))
        assertEquals(true, restored["ffstudio_settings"]?.get("hardware_codec"))
        assertEquals(3, restored["ffstudio_settings"]?.get("grid"))
        assertEquals(setOf("a&b", "<c>", "中文"), restored["ffstudio_settings"]?.get("seen_ids"))
        assertEquals("k\"1", restored["ffstudio_ai"]?.get("api_key"))
    }

    @Test
    fun `xml special characters are escaped and restored`() {
        val xml = SettingsBackup.encode(
            mapOf("ffstudio_library" to mapOf("rule" to "a<b>&'\"c"))
        )
        assertTrue(!xml.contains("a<b>"))
        assertEquals("a<b>&'\"c", SettingsBackup.decode(xml)["ffstudio_library"]?.get("rule"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects foreign root element`() {
        SettingsBackup.decode("<other version=\"1\"></other>")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unsupported backup version`() {
        SettingsBackup.decode("<framebetween-settings version=\"99\"></framebetween-settings>")
    }

    @Test
    fun `generated file name has timestamp and xml suffix`() {
        assertTrue(SettingsBackup.fileName().matches(Regex("FrameBetween_settings_\\d{14}\\.xml")))
    }
}
