package com.framebetween.app.core

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.framebetween.app.App
import java.io.File

/**
 * 用本构建自带的 showwavespic 滤镜把整段音频渲染成一张波形 PNG。
 * 结果按「路径 + 修改时间」缓存在 cacheDir/waveforms，清除缓存时会一并回收。
 */
object AudioWaveform {
    fun render(path: String): String? {
        val source = File(path)
        if (!source.isFile) return null
        val dir = File(App.instance.cacheDir, "waveforms").apply { mkdirs() }
        val key = "${source.absolutePath.hashCode().toString(16)}_${source.lastModified()}"
        val out = File(dir, "$key.png")
        if (out.isFile && out.length() > 0) return out.absolutePath
        val args = listOf(
            "-y", "-i", path,
            "-filter_complex", "showwavespic=s=1000x180:colors=0x4C63B6",
            "-frames:v", "1", out.absolutePath
        )
        return runCatching {
            val session = FFmpegKit.execute(CommandTokenizer.join(args))
            val code = session.getReturnCode()
            if (code != null && ReturnCode.isSuccess(code) && out.isFile && out.length() > 0) {
                out.absolutePath
            } else {
                runCatching { out.delete() }
                null
            }
        }.getOrNull()
    }
}
