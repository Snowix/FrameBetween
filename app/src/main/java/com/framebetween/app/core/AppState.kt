package com.framebetween.app.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局轻量状态：当前文件列表（可多选）、各工具页草稿、一次性提示。
 *
 * 「当前文件」现在是一个列表：首个元素作为所有单输入工具页的默认输入，
 * 其余文件保留在列表中，供批量转换 / 音频拼接等场景取用。
 * 切换底部导航不会销毁这里的任何状态。
 */
object AppState {
    private val _currentFiles = MutableStateFlow<List<MediaInfo>>(emptyList())
    val currentFiles = _currentFiles.asStateFlow()

    /** 兼容旧调用：始终等于列表的第一个元素。 */
    private val _current = MutableStateFlow<MediaInfo?>(null)
    val current = _current.asStateFlow()

    private val _batchInputs = MutableStateFlow<List<MediaInfo>>(emptyList())
    val batchInputs = _batchInputs.asStateFlow()

    private val _mergeFiles = MutableStateFlow<List<MediaInfo>>(emptyList())
    val mergeFiles = _mergeFiles.asStateFlow()

    /** 音频拼接页的轨道列表，切导航不丢失。 */
    private val _audioJoinFiles = MutableStateFlow<List<MediaInfo>>(emptyList())
    val audioJoinFiles = _audioJoinFiles.asStateFlow()

    /** 媒体库音乐播放条：切 tab 不中断，停止后清空即关闭播放条。 */
    private val _nowPlaying = MutableStateFlow<List<LibraryItem>>(emptyList())
    val nowPlaying = _nowPlaying.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    private fun sync() {
        _current.value = _currentFiles.value.firstOrNull()
    }

    /** 覆盖式设置：用于外部单选、清除、单输入工具页。 */
    fun setCurrent(info: MediaInfo?) {
        val next = listOfNotNull(info)
        val previous = _currentFiles.value
        _currentFiles.value = next
        sync()
        previous.filterNot { it.path in next.map { n -> n.path } }.forEach {
            MediaRepository.releaseInput(it.path)
        }
    }

    /** 追加一个或多个文件，按路径去重；已有文件不会被替换。 */
    fun addCurrent(files: List<MediaInfo>) {
        if (files.isEmpty()) return
        val merged = (_currentFiles.value + files).distinctBy { it.path }
        _currentFiles.value = merged
        sync()
    }

    fun addCurrent(info: MediaInfo) = addCurrent(listOf(info))

    fun removeCurrent(path: String) {
        _currentFiles.value = _currentFiles.value.filterNot { it.path == path }
        sync()
        MediaRepository.releaseInput(path)
    }

    fun clearCurrent() = setCurrent(null)

    fun setBatchInputs(list: List<MediaInfo>) {
        _batchInputs.value = list
    }

    fun setMergeFiles(list: List<MediaInfo>) {
        _mergeFiles.value = list
    }

    fun setAudioJoinFiles(list: List<MediaInfo>) {
        _audioJoinFiles.value = list
    }

    /** 只接收音频条目；重复点同一首会重新创建播放器从头播放。 */
    fun playAudio(items: List<LibraryItem>) {
        _nowPlaying.value = items.filter { it.isAudio }
    }

    fun stopAudio() {
        _nowPlaying.value = emptyList()
    }

    fun notify(message: String) {
        _notice.value = message
    }

    fun consumeNotice() {
        _notice.value = null
    }
}

object MergeDraft {
    private val _paths = MutableStateFlow<List<String>>(emptyList())
    val paths = _paths.asStateFlow()

    fun set(list: List<String>) {
        _paths.value = list
    }

    fun clear() {
        _paths.value = emptyList()
    }
}
