package com.framebetween.app.core

import android.os.Environment
import java.io.File

/**
 * 「全部文件」文件管理器的数据源：只读取「当前目录这一层」的真实文件系统，
 * 不扫描媒体、不查 MediaStore、不递归、不套用媒体库的黑白名单与阈值规则。
 * 进入某个目录时才现场 listFiles()，因此切换目录是即时的。
 */
object FileSystem {

    /** 内置共享存储根，通常是 /storage/emulated/0。 */
    fun sharedRoot(): String = runCatching {
        Environment.getExternalStorageDirectory()?.absolutePath?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "/storage/emulated/0"

    /** 所有挂载的存储卷（内置存储、SD 卡等）。 */
    fun volumesRoot(): String = "/storage"

    @Suppress("DEPRECATION")
    fun entries(path: String): List<FsEntry> {
        val dir = File(path)
        if (!dir.isDirectory) return emptyList()
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
        val list = children.mapNotNull { f ->
            runCatching {
                FsEntry(
                    name = f.name,
                    path = f.absolutePath,
                    isDirectory = f.isDirectory,
                    sizeBytes = if (f.isFile) f.length() else 0L,
                    dateMs = f.lastModified()
                )
            }.getOrNull()
        }
        return FsEntrySort.sort(list)
    }

    /**
     * 上一级目录：共享存储根 → 存储卷列表 → 返回 null（由调用方退出文件管理模式）。
     * 不允许越出 /storage，避免进入系统根目录。
     */
    fun parentOf(path: String): String? =
        parentPath(path, sharedRoot(), volumesRoot())

    /** 纯路径逻辑（无 Android 依赖），便于 JVM 单测覆盖目录回退边界。 */
    fun parentPath(path: String, root: String, volumes: String): String? {
        val shared = root.trimEnd('/')
        val top = volumes.trimEnd('/')
        val current = path.trimEnd('/')
        return when {
            current == shared -> top
            current == top -> null
            current == "/" -> null
            else -> {
                val cut = current.lastIndexOf('/')
                if (cut <= 0) null else current.substring(0, cut)
                    .takeIf { it.isNotBlank() && it.length >= top.length }
            }
        }
    }

    /** 把一个真实文件包装成媒体库条目，复用播放、详情与「添加到列表」。 */
    fun asLibraryItem(entry: FsEntry): LibraryItem {
        val file = File(entry.path)
        return LibraryItem(
            id = "fs_${entry.path}",
            title = entry.name,
            uri = android.net.Uri.fromFile(file),
            sizeBytes = entry.sizeBytes,
            dateMs = entry.dateMs,
            durationMs = 0L,
            mime = com.framebetween.app.util.mimeOf(entry.name),
            folder = file.parentFile?.name.orEmpty(),
            source = LibrarySource.SCAN,
            path = entry.path,
            folderPath = file.parent
        )
    }
}
