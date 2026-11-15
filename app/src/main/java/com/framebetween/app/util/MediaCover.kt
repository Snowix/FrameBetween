package com.framebetween.app.util

/**
 * 媒体库封面显示比例（纯 Kotlin，便于 JVM 单测）。
 * 视频与音频统一使用较矮的 3:2 画布，两者在列表 / 网格下占用完全一致；
 * 图片与其它文件仍用 1:1。
 */
object MediaCover {
    const val WIDE_RATIO = 1.5f
    const val SQUARE_RATIO = 1f

    /** 该条目是否使用与视频相同的宽画布。 */
    fun isWideCanvas(isVideo: Boolean, isAudio: Boolean): Boolean = isVideo || isAudio

    fun aspectRatio(isVideo: Boolean, isAudio: Boolean = false): Float =
        if (isWideCanvas(isVideo, isAudio)) WIDE_RATIO else SQUARE_RATIO
}
