package com.framebetween.app
import com.framebetween.app.core.L10n

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.framebetween.app.core.AppState
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.core.MergeDraft
import com.framebetween.app.ui.AppRoot
import com.framebetween.app.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 部分厂商会忽略 Manifest 的 adjustNothing，这里再显式设置一次，
        // 确保输入法弹出时窗口完全不缩放、底部导航不被顶起。
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        if (savedInstanceState == null) handleExternal(intent)
        setContent {
            AppTheme {
                val snackbar = remember { SnackbarHostState() }
                val notice by AppState.notice.collectAsState()
                LaunchedEffect(notice) {
                    notice?.let {
                        snackbar.showSnackbar(it)
                        AppState.consumeNotice()
                    }
                }
                AppRoot(snackbar)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternal(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleExternal(source: Intent?) {
        val uris = mutableListOf<Uri>()
        when (source?.action) {
            Intent.ACTION_VIEW -> source.data?.let { uris.add(it) }
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= 33) {
                    source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    source.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                uri?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= 33) {
                    source.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    source.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                list?.let { uris.addAll(it) }
            }
        }
        if (uris.isEmpty()) return

        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { runCatching { MediaRepository.import(it) }.getOrNull() }
            }
            val first = imported.firstOrNull()
            if (first == null) {
                AppState.notify(L10n.t("导入失败：无法读取该文件"))
                return@launch
            }
            // 外部「打开方式 / 分享」的所有文件直接追加到当前文件列表，不覆盖已有选择
            AppState.addCurrent(imported)
            if (imported.size > 1) {
                MergeDraft.set(imported.map { it.path })
                AppState.notify(L10n.t("已导入 %1\$s 个文件，可前往「视频合并」", imported.size))
            } else {
                AppState.notify(L10n.t("已导入：%1\$s", first.displayName))
            }
        }
    }
}
