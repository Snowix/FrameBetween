package com.framebetween.app.core
import com.framebetween.app.core.L10n

import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFprobeKit
import com.framebetween.app.App
import com.framebetween.app.util.mimeOf
import com.framebetween.app.util.outputFileName as buildOutputFileName
import com.framebetween.app.util.safeFileName
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 文件接入层。
 *
 * 存储策略：
 * - 导入时优先把 content:// 解析成真实文件路径，FFmpeg / FFprobe 直接读原文件，不再复制到 cacheDir；
 *   只有无法解析（网盘、部分下载管理器）或探测失败时才回退复制一份输入副本；
 * - FFmpeg 输出直接写到应用外部私有目录（用户可见、无需权限），文件名为「原视频名_MMddHHmmss.ext」；
 * - 任务成功后由 [publish] 按设置转存到系统媒体库或自定义目录；默认输出目录时原位保留；
 * - 删除任务、清空已完成、设置页清除缓存时回收文件；冷启动清理上次残留的输入副本与缩略图以外的临时文件。
 */
object MediaRepository {

    private val cache = ConcurrentHashMap<String, MediaInfo>()

    private fun inputsDir(): File = File(App.instance.cacheDir, "inputs").apply { mkdirs() }
    private fun tmpDir(): File = File(App.instance.cacheDir, "tmp").apply { mkdirs() }

    /** 外部私有目录：/storage/emulated/0/Android/data/包名/files/Movies/FrameBetween，用户可见且卸载即清理。 */
    fun externalDir(): File {
        val base = App.instance.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(App.instance.filesDir, "external")
        return File(base, "FrameBetween").apply { mkdirs() }
    }

    /** FFmpeg 可直接写入、且用户可见的目录（应用外部私有目录）。 */
    fun commandOutputDir(): File = externalDir()

    /** display 路径 → FFmpeg 真正可写的暂存路径，只在进程内保存，用于命令行页回填。 */
    private val outputAliases = ConcurrentHashMap<String, String>()

    /** 把 SAF 目录树还原成用户看得懂的完整路径；无法解析时退回目录名。 */
    @Suppress("DEPRECATION")
    fun treeDisplayPath(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return raw
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        if (!docId.isNullOrBlank()) {
            val parts = docId.split(":", limit = 2)
            if (parts.size == 2 && parts[0].equals("primary", true)) {
                return Environment.getExternalStorageDirectory().absolutePath + "/" + parts[1].trimEnd('/')
            }
            if (docId.startsWith("raw:")) return docId.removePrefix("raw:")
        }
        return runCatching { DocumentFile.fromTreeUri(App.instance, uri)?.name }.getOrNull()
            ?: uri.lastPathSegment.orEmpty().ifBlank { raw }
    }

    /**
     * 用户在设置里选择的「目标输出目录」的可读表示。
     * FFmpeg 仍写应用外部私有目录（Android 11+ 不能直接写公共目录），
     * 任务完成后由 publish 转存；这里只负责让 UI 显示与用户选择一致。
     */
    @Suppress("DEPRECATION")
    fun targetOutputDir(): String {
        val actual = externalDirNoCreate().absolutePath
        return when (Settings.outputTarget.value) {
            OutputTarget.EXTERNAL -> actual
            OutputTarget.GALLERY -> runCatching {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "FrameBetween"
                ).absolutePath
            }.getOrDefault(actual)
            OutputTarget.CUSTOM -> treeDisplayPath(Settings.customTree.value).ifBlank { actual }
        }
    }

    /** 供「插入输出路径 / 模板 / AI 建议」使用的展示路径，并记住它对应的真实可写路径。 */
    fun outputAlias(staging: String): String {
        val shown = targetOutputDir().trimEnd('/') + "/" + File(staging).name
        if (shown != staging) outputAliases[shown] = staging
        return shown
    }

    /** 执行前把展示路径还原成 FFmpeg 可写的暂存路径。 */
    fun resolveOutputAliases(tokens: List<String>): List<String> = tokens.map { token ->
        outputAliases[token] ?: outputAliases[token.trim().trim('\'', '"')] ?: token
    }

    /** 命令预览统一把暂存目录替换成用户选择的目标目录，避免显示成默认软件目录。 */
    fun presentCommand(command: String): String {
        val actual = externalDirNoCreate().absolutePath
        val shown = targetOutputDir()
        return if (actual == shown || actual.isBlank()) command else command.replace(actual, shown)
    }

    /**
     * 生成输出路径。写在同一目录而不是隐藏的 cacheDir；GALLERY / CUSTOM 目标在任务完成后由 publish 转存。
     */
    fun newOutput(ext: String, label: String = "out", sourceName: String? = null): String {
        val source = sourceName ?: AppState.current.value?.displayName ?: label
        val name = buildOutputFileName(source, ext)
        val dir = commandOutputDir()
        var candidate = File(dir, name)
        var index = 2
        while (candidate.exists() && index < 100) {
            val stem = name.substringBeforeLast('.')
            candidate = File(dir, "${stem}_$index.${ext.lowercase()}")
            index++
        }
        return candidate.absolutePath
    }

    fun newTempText(name: String, content: String): String {
        val file = File(tmpDir(), "${safeFileName(name)}_${System.currentTimeMillis()}.txt")
        file.writeText(content)
        return file.absolutePath
    }

    fun info(path: String): MediaInfo? = cache[path]

    fun isContentRef(ref: String): Boolean = ref.startsWith("content://")

    private fun displayName(uri: Uri): String {
        val resolver = App.instance.contentResolver
        return runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull() ?: "media_${System.currentTimeMillis()}"
    }

    /**
     * 把 SAF / MediaStore 的 content:// 尽可能还原成真实文件路径。
     * 得到路径后 FFmpeg 就能直接读原文件，无需任何复制；分区存储下需已授予媒体读取权限
     * 或该文件本身位于应用可访问目录（canRead 会兜住）。
     */
    @Suppress("DEPRECATION")
    fun resolveOriginalPath(uri: Uri): String? {
        val context = App.instance
        when (uri.scheme?.lowercase()) {
            "file" -> {
                val path = uri.path ?: return null
                return path.takeIf { File(it).isFile && File(it).canRead() }
            }
            "content" -> {
                @Suppress("DEPRECATION")
                runCatching {
                    context.contentResolver.query(
                        uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val path = c.getString(0)
                            if (!path.isNullOrBlank() && File(path).isFile && File(path).canRead()) {
                                return path
                            }
                        }
                    }
                }
                if (uri.authority == "com.android.externalstorage.documents") {
                    runCatching {
                        val parts = DocumentsContract.getDocumentId(uri).split(":", limit = 2)
                        if (parts.size == 2 && parts[0].equals("primary", true)) {
                            val path = Environment.getExternalStorageDirectory().absolutePath +
                                "/" + parts[1]
                            if (File(path).isFile && File(path).canRead()) return path
                        }
                    }
                }
                if (uri.authority == "com.android.providers.downloads.documents") {
                    runCatching {
                        val id = DocumentsContract.getDocumentId(uri)
                        if (id.startsWith("raw:")) {
                            val path = id.removePrefix("raw:")
                            if (File(path).isFile && File(path).canRead()) return path
                        }
                    }
                }
            }
        }
        return null
    }

    /** 优先直接用原始文件路径；仅当没有真实路径或原生层读不到时才回退复制输入副本。 */
    fun import(uri: Uri): MediaInfo {
        val name = displayName(uri)
        val direct = resolveOriginalPath(uri)
        if (direct != null) {
            val info = runCatching { probe(direct, name) }.getOrNull()
            if (info != null && (info.durationMs > 0 || info.sizeBytes > 0)) {
                return info.also { cache[it.path] = it }
            }
        }
        val target = File(inputsDir(), "${UUID.randomUUID().toString().take(8)}_${safeFileName(name)}")
        val stream = App.instance.contentResolver.openInputStream(uri)
            ?: throw IOException(L10n.t("无法打开文件：%1\$s", uri))
        stream.use { input ->
            target.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
        }
        return probe(target.absolutePath, name).also { cache[it.path] = it }
    }

    /** 更换文件时回收上一份输入副本（有任务在跑时保守保留）。 */
    fun releaseInput(path: String) {
        if (FFmpegEngine.hasRunningTasks()) return
        val file = File(path)
        if (file.parentFile?.absolutePath == inputsDir().absolutePath) {
            runCatching { file.delete() }
            cache.remove(path)
        }
    }

    fun probe(path: String, displayName: String = File(path).name): MediaInfo {
        val command = "-v error -show_entries " +
            "format=duration,size,bit_rate,format_name:" +
            "stream=index,codec_type,codec_name,profile,level,pix_fmt,width,height," +
            "display_aspect_ratio,avg_frame_rate,r_frame_rate,bit_rate,sample_rate," +
            "channels,channel_layout,nb_frames -of json " +
            CommandTokenizer.quote(path)

        val session = FFprobeKit.execute(command)
        val raw = session.getAllLogsAsString().orEmpty()
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        val root = if (start >= 0 && end > start) {
            runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrDefault(JSONObject())
        } else JSONObject()

        val format = root.optJSONObject("format")
        val durationSec = format?.optString("duration", "0")?.toDoubleOrNull() ?: 0.0
        val sizeBytes = format?.optString("size", "0")?.toLongOrNull()
            ?: File(path).length()
        val formatName = format?.optString("format_name", "").orEmpty()
        val formatBitrateKbps = (format?.optString("bit_rate", "0")?.toLongOrNull() ?: 0L) / 1000L

        var video: VideoStream? = null
        var audio: AudioStream? = null
        val streams = root.optJSONArray("streams")
        if (streams != null) {
            for (i in 0 until streams.length()) {
                val stream = streams.optJSONObject(i) ?: continue
                val type = stream.optString("codec_type", "")
                val bitrateKbps = (stream.optString("bit_rate", "0").toLongOrNull() ?: 0L) / 1000L
                when {
                    type == "video" && video == null -> video = VideoStream(
                        codec = stream.optString("codec_name", ""),
                        profile = stream.optString("profile", ""),
                        pixFmt = stream.optString("pix_fmt", ""),
                        width = stream.optInt("width", 0),
                        height = stream.optInt("height", 0),
                        fps = parseRate(
                            stream.optString("avg_frame_rate", "0").ifBlank { "0" },
                            stream.optString("r_frame_rate", "0")
                        ),
                        bitrateKbps = bitrateKbps,
                        nbFrames = stream.optString("nb_frames", "0").toLongOrNull() ?: 0L,
                        aspectRatio = stream.optString("display_aspect_ratio", "")
                    )
                    type == "audio" && audio == null -> audio = AudioStream(
                        codec = stream.optString("codec_name", ""),
                        sampleRate = stream.optString("sample_rate", "0").toIntOrNull() ?: 0,
                        channels = stream.optInt("channels", 0),
                        bitrateKbps = bitrateKbps,
                        channelLayout = stream.optString("channel_layout", "")
                    )
                }
            }
        }

        return MediaInfo(
            path = path,
            displayName = displayName,
            sizeBytes = sizeBytes,
            durationMs = (durationSec * 1000).toLong(),
            formatName = formatName,
            formatBitrateKbps = formatBitrateKbps,
            video = video,
            audio = audio
        )
    }

    private fun parseRate(primary: String, fallback: String): Double {
        val value = if (primary == "0/0" || primary == "0") fallback else primary
        val parts = value.split("/")
        val numerator = parts.getOrNull(0)?.toDoubleOrNull() ?: return 0.0
        val denominator = parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0
        return if (denominator > 0) numerator / denominator else 0.0
    }

    /** 任务输出引用可能是普通文件路径，也可能是 MediaStore / SAF 的 content://。 */
    fun outputUri(ref: String): Uri =
        if (isContentRef(ref)) Uri.parse(ref) else shareUri(ref)

    fun outputName(ref: String): String = when {
        isContentRef(ref) -> runCatching {
            DocumentFile.fromSingleUri(App.instance, Uri.parse(ref))?.name
        }.getOrNull() ?: Uri.parse(ref).lastPathSegment ?: "output"
        else -> File(ref).name
    }

    fun outputMime(ref: String): String {
        if (isContentRef(ref)) {
            return runCatching { App.instance.contentResolver.getType(Uri.parse(ref)) }.getOrNull()
                ?: mimeOf(ref.substringAfterLast('.', ""))
        }
        return mimeOf(ref)
    }

    fun outputExists(ref: String): Boolean = when {
        ref.isEmpty() -> false
        isContentRef(ref) -> true
        else -> File(ref).exists() && File(ref).length() > 0
    }

    fun shareUri(path: String): Uri {
        if (isContentRef(path)) return Uri.parse(path)
        return FileProvider.getUriForFile(
            App.instance,
            "${App.instance.packageName}.fileprovider",
            File(path)
        )
    }

    /** 保存到系统相册 / 图片库 / 音乐库；返回媒体库 Uri，失败返回 null。 */
    fun exportToGallery(path: String, mime: String): Uri? {
        val context = App.instance
        val file = File(path)
        if (!file.exists()) return null
        val isVideo = mime.startsWith("video")
        val isImage = mime.startsWith("image")

        if (Build.VERSION.SDK_INT < 29) {
            return runCatching {
                val base = Environment.getExternalStoragePublicDirectory(
                    when {
                        isVideo -> Environment.DIRECTORY_MOVIES
                        isImage -> Environment.DIRECTORY_PICTURES
                        else -> Environment.DIRECTORY_MUSIC
                    }
                )
                val dir = File(base, "FrameBetween").apply { mkdirs() }
                val dest = File(dir, file.name)
                file.copyTo(dest, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
                shareUri(dest.absolutePath)
            }.getOrNull()
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                (when {
                    isVideo -> Environment.DIRECTORY_MOVIES
                    isImage -> Environment.DIRECTORY_PICTURES
                    else -> Environment.DIRECTORY_MUSIC
                }) + "/FrameBetween"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = when {
            isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            isImage -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val uri = context.contentResolver.insert(collection, values) ?: return null
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out, 64 * 1024) }
            } ?: throw IOException(L10n.t("无法写入媒体库"))
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
            uri
        }.getOrElse {
            context.contentResolver.delete(uri, null, null)
            null
        }
    }

    /** 把产物按用户设定的默认输出目录发布；默认目录时原位保留，其余目标转存后删除源文件。 */
    fun publish(staging: String): String {
        if (staging.isEmpty() || !File(staging).exists()) return staging
        val context = App.instance
        val mime = mimeOf(staging)
        return when (Settings.outputTarget.value) {
            OutputTarget.EXTERNAL -> {
                val file = File(staging)
                val dest = File(externalDir(), file.name)
                if (file.absolutePath == dest.absolutePath) staging
                else runCatching {
                    file.copyTo(dest, overwrite = true)
                    file.delete()
                    dest.absolutePath
                }.getOrDefault(staging)
            }

            OutputTarget.GALLERY -> {
                val uri = exportToGallery(staging, mime)
                if (uri != null) {
                    File(staging).delete()
                    uri.toString()
                } else staging
            }

            OutputTarget.CUSTOM -> {
                val tree = Settings.customTree.value?.let { runCatching { Uri.parse(it) }.getOrNull() }
                val root = tree?.let { runCatching { DocumentFile.fromTreeUri(context, it) }.getOrNull() }
                val child = root?.runCatching { createFile(mime, File(staging).name) }?.getOrNull()
                val copied = child?.let {
                    runCatching {
                        context.contentResolver.openOutputStream(it.uri)?.use { out ->
                            File(staging).inputStream().use { input -> input.copyTo(out, 64 * 1024) }
                        } != null
                    }.getOrDefault(false)
                } ?: false
                if (copied) {
                    File(staging).delete()
                    child!!.uri.toString()
                } else {
                    runCatching { child?.delete() }
                    staging
                }
            }
        }
    }

    /** 只读路径构造：统计与清理时绝不 mkdirs，否则「清完又重建空目录」。 */
    private fun cacheRoot(): File = App.instance.cacheDir

    private fun legacyDirs(): List<File> = listOf(
        File(App.instance.filesDir, "outputs"),
        File(App.instance.filesDir, "inputs"),
        File(App.instance.filesDir, "cache")
    )

    private fun externalDirNoCreate(): File {
        val base = App.instance.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(App.instance.filesDir, "external")
        return File(base, "FrameBetween")
    }

    /** Android 运行时自用的目录，不纳入应用缓存清理。 */
    private val runtimeKeep = setOf("code_cache")

    /** 统一扫描口径：整个 cacheDir + files 目录 + 默认输出目录。 */
    private fun scanCacheFiles(): List<File> {
        val out = mutableListOf<File>()
        fun walk(dir: File) {
            if (!dir.isDirectory) return
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    if (child.name !in runtimeKeep) walk(child)
                } else if (child.isFile) {
                    out += child
                }
            }
        }
        walk(cacheRoot())
        legacyDirs().forEach(::walk)
        walk(externalDirNoCreate())
        return out
    }

    private fun isManagedFile(file: File): Boolean {
        val path = file.absolutePath
        return path.startsWith(cacheRoot().absolutePath) ||
            legacyDirs().any { path.startsWith(it.absolutePath) } ||
            path.startsWith(externalDirNoCreate().absolutePath)
    }

    /** 删除任务时回收它独占的输出文件；只处理本应用管理的目录，绝不误删用户文件。 */
    fun deleteOutput(ref: String): Long {
        if (ref.isEmpty()) return 0L
        // 已导出到系统媒体库 / 用户自定义目录的产物属于用户数据，不随任务清理
        if (isContentRef(ref)) return 0L
        val file = File(ref)
        if (!isManagedFile(file) || !file.exists()) return 0L
        val size = file.length()
        return if (file.delete()) size else 0L
    }

    fun cacheSize(): Long = scanCacheFiles().sumOf { it.length() }

    data class CacheClearResult(
        val freedBytes: Long,
        val deletedFiles: Int,
        val remainingBytes: Long
    )

    /**
     * 清除缓存：以整个 cacheDir 为根递归回收，保护集只由调用方提供（运行中任务的输出）。
     * 删除后自底向上移除空目录，不重建任何目录，确保 /data/data/.../cache 真的被清空。
     */
    fun clearCache(protectedRefs: Set<String>): CacheClearResult {
        val protectedPaths = protectedRefs
            .filterNot { isContentRef(it) }
            .map { File(it).absolutePath }
            .toSet()
        var freed = 0L
        var deleted = 0
        scanCacheFiles().forEach { file ->
            if (file.absolutePath in protectedPaths) return@forEach
            val size = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) {
                freed += size
                deleted++
            }
        }
        fun prune(dir: File) {
            if (!dir.isDirectory) return
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory && child.name !in runtimeKeep) prune(child)
            }
            if (dir.name !in runtimeKeep &&
                dir.absolutePath != cacheRoot().absolutePath &&
                dir.absolutePath != App.instance.filesDir.absolutePath &&
                dir.absolutePath != externalDirNoCreate().absolutePath
            ) {
                runCatching { dir.delete() }
            }
        }
        prune(cacheRoot())
        legacyDirs().forEach(::prune)
        prune(externalDirNoCreate())
        cache.clear()
        // 清除软件缓存同时清空「求助 AI」连续对话；单独的进程冷启动回收不会触碰它
        AiChatStore.clear()
        return CacheClearResult(freed, deleted, scanCacheFiles().sumOf { it.length() })
    }

    /** 冷启动清理：进程内的任务不会跨进程存活，上次残留的缓存可以安全回收。 */
    fun sweepOnStart() {
        cacheRoot().listFiles()?.forEach { child ->
            if (child.name !in runtimeKeep) runCatching { child.deleteRecursively() }
        }
        legacyDirs().forEach { runCatching { it.deleteRecursively() } }
    }

    /** 把内置的微信赞赏码保存到系统相册；返回是否成功。 */
    fun saveRewardQrToGallery(): Boolean = runCatching {
        val tmp = File(App.instance.cacheDir, L10n.t("帧间赞赏码.png"))
        if (tmp.exists()) tmp.delete()
        App.instance.resources
            .openRawResource(com.framebetween.app.R.drawable.reward_qrcode)
            .use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
        val uri = exportToGallery(tmp.absolutePath, "image/png")
        tmp.delete()
        uri != null
    }.getOrDefault(false)
}
