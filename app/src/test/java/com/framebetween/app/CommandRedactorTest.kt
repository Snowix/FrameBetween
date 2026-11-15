package com.framebetween.app

import com.framebetween.app.core.CommandRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AI 解析发送前的真实路径脱敏。 */
class CommandRedactorTest {

    @Test
    fun `redacts quoted input and output paths`() {
        val command = "-i \"/sdcard/DCIM/Camera/a b.mp4\" -t 10 -c copy " +
            "\"/storage/emulated/0/Movies/out.mp4\""
        assertEquals(
            "-i 输入路径 -t 10 -c copy 输出路径",
            CommandRedactor.redact(
                command,
                "/sdcard/DCIM/Camera/a b.mp4",
                "/storage/emulated/0/Movies/out.mp4"
            )
        )
    }

    @Test
    fun `detects input and output paths without hints`() {
        val command = "-i /sdcard/a.mp4 -c:v libx265 -b:v 2000k /sdcard/Movies/b.mp4"
        assertEquals(
            "-i 输入路径 -c:v libx265 -b:v 2000k 输出路径",
            CommandRedactor.redact(command)
        )
    }

    @Test
    fun `keeps in and out placeholders untouched`() {
        assertEquals(
            "-i {in} -c copy {out}",
            CommandRedactor.redact("-i {in} -c copy {out}")
        )
    }

    @Test
    fun `redacts inline filter path but keeps the filter name`() {
        val result = CommandRedactor.redact(
            "-i {in} -vf \"subtitles=filename=/sdcard/Subs/a.srt\" {out}"
        )
        assertTrue(result.contains("subtitles=filename=其它路径"))
        assertFalse(result.contains("/sdcard/Subs"))
    }

    @Test
    fun `numeric expressions like PTS slash 2 are not treated as paths`() {
        val command = "-i {in} -vf setpts=PTS/2 -af atempo=2 {out}"
        assertEquals(command, CommandRedactor.redact(command))
    }

    @Test
    fun `redactText scrubs paths inside failure logs`() {
        val log = "Error opening input /sdcard/DCIM/Camera/a.mp4\n" +
            "Unable to find a suitable output format for '/storage/out.mp4'\n" +
            "  libavformat/... failed"
        val result = CommandRedactor.redactText(
            log, "/sdcard/DCIM/Camera/a.mp4", "/storage/out.mp4"
        )
        assertTrue(result.contains("输入路径"))
        assertTrue(result.contains("输出路径"))
        assertFalse(result.contains("/sdcard"))
        assertFalse(result.contains("/storage/out.mp4"))
    }

    @Test
    fun `empty command stays empty`() {
        assertEquals("", CommandRedactor.redact(""))
    }
}
