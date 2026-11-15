package com.framebetween.app
import com.framebetween.app.core.L10n

import android.app.Application
import com.framebetween.app.core.AiChatStore
import com.framebetween.app.core.AiSettings
import com.framebetween.app.core.AppLog
import com.framebetween.app.core.CodecCapabilities
import com.framebetween.app.core.LibrarySettings
import com.framebetween.app.core.MediaLibrary
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.core.PlaybackCompat
import com.framebetween.app.core.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 先读偏好：Settings.init 会同步日志开关，保证写第一条日志前门控已就位
        Settings.init(this)
        AppLog.init(filesDir)
        AppLog.installCrashHandler()
        AppLog.i("App", L10n.t("FrameBetween 启动"))
        LibrarySettings.init(this)
        AiSettings.init(this)
        // 连续对话存 SharedPreferences，不在 cacheDir 内，冷启动回收不会误删
        AiChatStore.init(this)
        // 上次进程残留的暂存文件在这里统一回收
        MediaRepository.sweepOnStart()
        // 清理 cacheDir/playback 下可能残留的暂存文件（pb_* 兼容产物、src_* 源副本），
        // 冷启动异步执行一次，把占掉的存储还给用户。
        CoroutineScope(Dispatchers.IO).launch {
            val freed = PlaybackCompat.clearLegacyCache()
            if (freed > 0) AppLog.i("App", "legacy playback cache cleared: $freed bytes")
        }
        // 冷启动预热：只读取上一轮媒体库快照（一次轻量文件 IO，首屏秒开）。
        CoroutineScope(Dispatchers.IO).launch {
            MediaLibrary.prime(this@App)
        }
        // 异步预热 FFmpeg 能力探测，不阻塞冷启动：探测结果缓存后，硬件编解码开关
        // 与「设备编解码器支持」页能立即拿到本机真实的 mediacodec 支持情况。
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { CodecCapabilities.probeFfmpeg() }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
