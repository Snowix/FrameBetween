package com.framebetween.app.core

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import java.io.File
import java.io.FileOutputStream

/**
 * 媒体库缩略图 / 音频封面加载：内存 LruCache + 磁盘文件缓存，全部走 IO 线程。
 * 视频优先用系统已生成的缩略图（省时、不解码整帧），失败再回退取帧；
 * 音频优先读文件内嵌封面，再回退专辑封面。
 */
object MediaThumbnails {

    private val memory = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun clearMemory() {
        synchronized(memory) { memory.evictAll() }
    }

    fun load(context: Context, item: LibraryItem): Bitmap? {
        val key = item.id.hashCode().toString()
        synchronized(memory) { memory.get(key)?.let { return it } }
        val disk = File(LibrarySettings.thumbDir(), "$key.jpg")
        if (disk.isFile && disk.length() > 0) {
            runCatching { BitmapFactory.decodeFile(disk.absolutePath) }.getOrNull()?.let {
                synchronized(memory) { memory.put(key, it) }
                return it
            }
        }
        val bitmap = when {
            item.isVideo -> video(context, item)
            item.isAudio -> audio(context, item)
            item.mime.startsWith("image") -> image(context, item)
            else -> null
        } ?: return null
        synchronized(memory) { memory.put(key, bitmap) }
        runCatching {
            LibrarySettings.thumbDir().mkdirs()
            FileOutputStream(disk).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
        }
        return bitmap
    }

    private fun video(context: Context, item: LibraryItem): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 视频按 3:2 取图；音频用方形专辑 / 内嵌图，显示时统一裁进与视频相同的 3:2 画布。
            runCatching {
                context.contentResolver.loadThumbnail(item.uri, Size(200, 134), null)
            }.getOrNull()?.let { return it }
        }
        return frame(context, item.uri, item.path)
    }

    private fun audio(context: Context, item: LibraryItem): Bitmap? {
        val embedded = withRetriever(context, item) { r ->
            r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        if (embedded != null) return embedded
        if (item.albumId > 0) {
            val artUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), item.albumId
            )
            runCatching {
                context.contentResolver.openInputStream(artUri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun image(context: Context, item: LibraryItem): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(item.uri, Size(200, 200), null)
            }.getOrNull()?.let { return it }
        }
        return runCatching {
            context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun frame(context: Context, uri: Uri, path: String?): Bitmap? = withRetriever(context, uri, path) { r ->
        r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    private fun <T> withRetriever(
        context: Context,
        item: LibraryItem,
        block: (MediaMetadataRetriever) -> T?
    ): T? = withRetriever(context, item.uri, item.path, block)

    private fun <T> withRetriever(
        context: Context,
        uri: Uri,
        path: String?,
        block: (MediaMetadataRetriever) -> T?
    ): T? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (!path.isNullOrBlank() && File(path).canRead()) {
                retriever.setDataSource(path)
            } else {
                retriever.setDataSource(context, uri)
            }
            block(retriever)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

}
