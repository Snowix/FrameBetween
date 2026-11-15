package com.framebetween.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.framebetween.app.core.AppState
import com.framebetween.app.core.MediaRepository

/**
 * 「原文件名 + MMddHHmmss」输出路径的唯一入口。
 *
 * 修复历史 bug：各工具页原本在组合初始化时直接 `MediaRepository.newOutput(ext, label)`，
 * 那一刻「当前文件」往往还没选（AppState.current 为 null），名字退化成 `flip_MMddHHmmss.mp4`；
 * 之后用户再选文件也不会重算，于是产物永远不带原文件名。
 *
 * 这里把「生成时机」和「当前文件 / 容器变化」绑定：
 * - 初始就用当前文件名生成；
 * - 换文件、换输出容器时重新生成，仍以新的原文件名为前缀；
 * - 用 rememberSaveable 记住已绑定的 key，切换底部导航恢复页面时不会把用户
 *   已生成的输出路径再换一次（保持任务栏与命令预览一致）。
 */
@Composable
fun rememberOutput(ext: String, label: String, sourceName: String? = null): MutableState<String> {
    val current by AppState.current.collectAsState()
    val source = sourceName ?: current?.displayName
    val sourceKey = sourceName ?: current?.path.orEmpty()
    val state = rememberSaveable {
        mutableStateOf(MediaRepository.newOutput(ext, label, source))
    }
    var bound by rememberSaveable { mutableStateOf("$ext|$sourceKey") }
    LaunchedEffect(ext, sourceKey) {
        val next = "$ext|$sourceKey"
        if (next != bound) {
            bound = next
            state.value = MediaRepository.newOutput(ext, label, source)
        }
    }
    return state
}

/** 提交任务后换一个新时间戳，避免下一次执行覆盖上一次产物，前缀仍是原文件名。 */
fun nextOutput(ext: String, label: String, sourceName: String? = null): String =
    MediaRepository.newOutput(ext, label, sourceName ?: AppState.current.value?.displayName)
