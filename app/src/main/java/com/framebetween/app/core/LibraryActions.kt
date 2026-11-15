package com.framebetween.app.core

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.framebetween.app.util.mimeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * 媒体库多选操作：复制 / 移动到 SAF 目录、重命名、删除、分享、加入当前文件列表。
 * 全部在 IO 线程执行；涉及系统媒体库或 SAF 的写操作都需要用户已授权，
 * 失败时逐项统计而不是整批回滚。
 */
object LibraryActions {

    data class OpResult(val ok: Int, val failed: Int)

    /** 媒体库中被选中的文件夹：名称、真实目录路径（可能为 null）与其下的媒体项。 */
    data class FolderRef(
        val name: String,
        val path: String?,
        val files: List<LibraryItem>
    )

    private fun mime(item: LibraryItem): String =
        item.mime.takeUnless { it.isBlank() || it == "application/octet-stream" }
            ?: mimeOf(item.title).takeUnless { it == "application/octet-stream" }
            ?: "application/octet-stream"

    private fun localFile(item: LibraryItem): File? =
        item.path?.let { File(it) }?.takeIf { it.isFile }

    private fun openSource(context: Context, item: LibraryItem): InputStream? {
        if (item.uri.scheme == "file") {
            val path = item.uri.path
            if (path != null && File(path).isFile) return runCatching { FileInputStream(path) }.getOrNull()
        }
        return runCatching { context.contentResolver.openInputStream(item.uri) }.getOrNull()
            ?: item.path?.takeIf { File(it).isFile }
                ?.let { runCatching { FileInputStream(it) }.getOrNull() }
    }

    /** 复制到用户通过系统文件夹选择器选中的目录；move = true 时成功后删除源文件。 */
    suspend fun copyTo(
        context: Context,
        items: List<LibraryItem>,
        tree: Uri,
        move: Boolean = false
    ): OpResult = withContext(Dispatchers.IO) {
        val root = runCatching { DocumentFile.fromTreeUri(context, tree) }.getOrNull()
            ?: return@withContext OpResult(0, items.size)
        var ok = 0
        var failed = 0
        items.forEach { item ->
            val child = runCatching { root.createFile(mime(item), item.title) }.getOrNull()
            val input = openSource(context, item)
            val copied = if (child != null && input != null) {
                runCatching {
                    input.use { src ->
                        context.contentResolver.openOutputStream(child.uri)?.use { dst ->
                            src.copyTo(dst, 64 * 1024)
                        } ?: throw IllegalStateException("no output stream")
                    }
                    true
                }.getOrElse {
                    runCatching { child.delete() }
                    false
                }
            } else false
            if (copied) {
                ok++
                if (move) deleteOne(context, item)
            } else failed++
        }
        OpResult(ok, failed)
    }

    /** 把一批文件复制进指定 SAF 目录；move = true 时成功后删除源文件。 */
    private fun copyInto(
        context: Context,
        items: List<LibraryItem>,
        dir: DocumentFile,
        move: Boolean
    ): OpResult {
        var ok = 0
        var failed = 0
        items.forEach { item ->
            val child = runCatching { dir.createFile(mime(item), item.title) }.getOrNull()
            val input = openSource(context, item)
            val copied = if (child != null && input != null) {
                runCatching {
                    input.use { src ->
                        context.contentResolver.openOutputStream(child.uri)?.use { dst ->
                            src.copyTo(dst, 64 * 1024)
                        } ?: throw IllegalStateException("no output stream")
                    }
                    true
                }.getOrElse {
                    runCatching { child.delete() }
                    false
                }
            } else false
            if (copied) {
                ok++
                if (move) deleteOne(context, item)
            } else failed++
        }
        return OpResult(ok, failed)
    }

    /**
     * 文件夹级复制 / 移动：在目标目录下创建同名子目录，保留目录结构，
     * 而不是把选中的文件夹内容平铺到目标根目录。
     */
    suspend fun copyFolders(
        context: Context,
        folders: List<FolderRef>,
        tree: Uri,
        move: Boolean = false
    ): OpResult = withContext(Dispatchers.IO) {
        val root = runCatching { DocumentFile.fromTreeUri(context, tree) }.getOrNull()
            ?: return@withContext OpResult(0, folders.sumOf { it.files.size })
        var ok = 0
        var failed = 0
        folders.forEach { folder ->
            val name = folder.name.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "FrameBetween" }
            val existing = runCatching { root.findFile(name) }.getOrNull()
            val dir = existing?.takeIf { it.isDirectory }
                ?: runCatching { root.createDirectory(name) }.getOrNull()
            if (dir == null) {
                failed += folder.files.size
            } else {
                val result = copyInto(context, folder.files, dir, move)
                ok += result.ok
                failed += result.failed
            }
        }
        OpResult(ok, failed)
    }

    /** 文件夹级删除：先删目录内媒体文件，再尝试删掉已清空的目录。 */
    suspend fun deleteFolders(context: Context, folders: List<FolderRef>): OpResult =
        withContext(Dispatchers.IO) {
            var ok = 0
            var failed = 0
            val changed = mutableListOf<String>()
            folders.forEach { folder ->
                folder.files.forEach { item ->
                    if (deleteOne(context, item)) {
                        ok++
                        item.path?.let { changed += it }
                    } else failed++
                }
                folder.path?.let { path ->
                    runCatching {
                        File(path).takeIf { dir ->
                            dir.isDirectory && dir.listFiles()?.isEmpty() == true
                        }?.delete()
                    }
                    changed += path
                }
            }
            // File.delete() 不会同步 MediaStore，必须请求系统刷新，否则其它应用与
            // 重新扫描仍会看到已被删除的文件索引。
            MediaLibrary.notifyPathsChanged(context, changed)
            OpResult(ok, failed)
        }

    /**
     * 文件夹重命名只对能拿到真实路径且可写的目录生效（通常已开启「所有文件访问权限」）。
     * 系统媒体库 / SAF 的虚拟分组无法整体改名，返回 false 由 UI 明确提示，不做静默批量改名。
     */
    suspend fun renameFolder(context: Context, folder: FolderRef, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            val name = newName.trim()
            if (name.isEmpty() || name == folder.name) return@withContext false
            val dir = folder.path?.let { File(it) }?.takeIf { it.isDirectory }
                ?: return@withContext false
            val dest = File(dir.parentFile, name)
            if (dest.exists()) return@withContext false
            runCatching { dir.renameTo(dest) }.getOrDefault(false)
        }

    suspend fun rename(context: Context, item: LibraryItem, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            val name = newName.trim()
            if (name.isEmpty() || name == item.title) return@withContext false
            val local = localFile(item)
            if (local != null) {
                val dest = File(local.parentFile, name)
                return@withContext !dest.exists() && local.renameTo(dest)
            }
            if (item.uri.scheme == "content") {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                }
                val updated = runCatching {
                    context.contentResolver.update(item.uri, values, null, null) > 0
                }.getOrDefault(false)
                if (updated) return@withContext true
                return@withContext runCatching {
                    DocumentFile.fromSingleUri(context, item.uri)?.renameTo(name) ?: false
                }.getOrDefault(false)
            }
            if (item.uri.scheme == "file") {
                val f = item.uri.path?.let { File(it) } ?: return@withContext false
                val dest = File(f.parentFile, name)
                return@withContext !dest.exists() && f.renameTo(dest)
            }
            false
        }

    private fun deleteOne(context: Context, item: LibraryItem): Boolean {
        val local = localFile(item)
        if (local != null) return runCatching { local.delete() }.getOrDefault(false)
        if (item.uri.scheme == "file") {
            val path = item.uri.path ?: return false
            return runCatching { File(path).delete() }.getOrDefault(false)
        }
        if (item.uri.scheme == "content") {
            val deleted = runCatching {
                context.contentResolver.delete(item.uri, null, null) > 0
            }.getOrDefault(false)
            if (deleted) return true
            return runCatching {
                DocumentFile.fromSingleUri(context, item.uri)?.delete() ?: false
            }.getOrDefault(false)
        }
        return false
    }

    suspend fun delete(context: Context, items: List<LibraryItem>): OpResult =
        withContext(Dispatchers.IO) {
            var ok = 0
            var failed = 0
            val changed = mutableListOf<String>()
            items.forEach { item ->
                if (deleteOne(context, item)) {
                    ok++
                    item.path?.let { changed += it }
                } else failed++
            }
            MediaLibrary.notifyPathsChanged(context, changed)
            OpResult(ok, failed)
        }

    /** 把选中的媒体项导入「当前文件」列表，优先直读原始路径。 */
    suspend fun addToCurrent(items: List<LibraryItem>): List<MediaInfo> =
        withContext(Dispatchers.IO) {
            items.mapNotNull { item ->
                runCatching { MediaRepository.import(item.uri) }.getOrNull()
            }
        }

    /**
     * 【全部文件】详情弹窗的「打开」：交给系统「打开方式」菜单。
     * 真实路径通过 FileProvider 生成 content:// 并授予临时读权限（file_paths.xml 已覆盖
     * 外部存储）；没有真实路径时直接用条目自带的 content:// Uri。
     */
    fun open(context: Context, item: LibraryItem): Boolean {
        val uri = item.path?.takeIf { File(it).isFile }?.let {
            runCatching { MediaRepository.shareUri(it) }.getOrNull()
        } ?: item.uri
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime(item))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(
                Intent.createChooser(intent, L10n.t("打开文件")).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            )
            true
        }.getOrDefault(false)
    }

    fun share(context: Context, items: List<LibraryItem>): Boolean {
        if (items.isEmpty()) return false
        val uris = items.map { it.uri }
        val base: Intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uris.first())
                type = mime(items.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                type = "*/*"
            }
        }
        val intent = base.apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("media", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
        }
        return runCatching {
            context.startActivity(
                Intent.createChooser(intent, L10n.t("分享文件"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    fun refresh() {
        MediaMeta.clear()
        LibrarySettings.rescan()
    }
}
