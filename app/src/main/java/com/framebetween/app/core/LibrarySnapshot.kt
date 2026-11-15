package com.framebetween.app.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * 媒体库磁盘快照中的一条记录。
 *
 * 只保留基本类型：[uri] 以字符串保存、[source] 保存枚举名，因此本文件完全不依赖
 * Android SDK，JVM 单元测试可以直接验证编解码往返。Uri.parse / Uri.toString 的
 * 转换放在 [MediaLibrary] 里完成。
 */
data class SnapshotRow(
    val id: String,
    val title: String,
    val uri: String,
    val sizeBytes: Long,
    val dateMs: Long,
    val durationMs: Long,
    val mime: String,
    val folder: String,
    val source: String,
    val albumId: Long,
    val path: String?,
    val folderPath: String?,
    val width: Int,
    val height: Int,
    val fps: Double,
    val hiddenDir: Boolean,
    val nomediaDir: Boolean
)

/**
 * 冷启动秒开用的二进制快照编解码。
 *
 * 采用 DataOutputStream 而不是 JSON / 分隔符文本：路径里可能包含 Tab、逗号、换行或
 * 任何奇怪字符，长度前缀写入天然不会有分隔符冲突；也不引入 org.json 等 Android stub，
 * 纯 JDK 即可运行。文件损坏、版本不符、条目数异常时一律返回空列表，让调用方安静地
 * 走完整扫描，绝不让缓存把媒体库搞崩。
 */
object LibrarySnapshot {

    private const val MAGIC = 0x46534442 // "FSDB"
    private const val VERSION = 1
    // 扫描上限放宽后快照也要跟得上，否则冷启动恢复时后半段文件会「凭空消失」
    const val MAX_ROWS = 20000

    fun encode(rows: List<SnapshotRow>): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(rows.size.coerceAtMost(MAX_ROWS))
            rows.take(MAX_ROWS).forEach { row ->
                out.writeUTF(row.id)
                out.writeUTF(row.title)
                out.writeUTF(row.uri)
                out.writeLong(row.sizeBytes)
                out.writeLong(row.dateMs)
                out.writeLong(row.durationMs)
                out.writeUTF(row.mime)
                out.writeUTF(row.folder)
                out.writeUTF(row.source)
                out.writeLong(row.albumId)
                writeNullable(out, row.path)
                writeNullable(out, row.folderPath)
                out.writeInt(row.width)
                out.writeInt(row.height)
                out.writeDouble(row.fps)
                out.writeBoolean(row.hiddenDir)
                out.writeBoolean(row.nomediaDir)
            }
        }
        return buffer.toByteArray()
    }

    /** 任何异常（截断、乱码、版本不符）都退化为空列表，不影响正常扫描。 */
    fun decode(bytes: ByteArray): List<SnapshotRow> = try {
        decodeOrThrow(bytes)
    } catch (e: Exception) {
        emptyList()
    }

    private fun decodeOrThrow(bytes: ByteArray): List<SnapshotRow> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        if (input.readInt() != MAGIC || input.readInt() != VERSION) return emptyList()
        val count = input.readInt()
        if (count < 0 || count > MAX_ROWS) return emptyList()
        return List(count) {
            SnapshotRow(
                id = input.readUTF(),
                title = input.readUTF(),
                uri = input.readUTF(),
                sizeBytes = input.readLong(),
                dateMs = input.readLong(),
                durationMs = input.readLong(),
                mime = input.readUTF(),
                folder = input.readUTF(),
                source = input.readUTF(),
                albumId = input.readLong(),
                path = readNullable(input),
                folderPath = readNullable(input),
                width = input.readInt(),
                height = input.readInt(),
                fps = input.readDouble(),
                hiddenDir = input.readBoolean(),
                nomediaDir = input.readBoolean()
            )
        }
    }

    private fun writeNullable(out: DataOutputStream, value: String?) {
        out.writeBoolean(value != null)
        if (value != null) out.writeUTF(value)
    }

    private fun readNullable(input: DataInputStream): String? =
        if (input.readBoolean()) input.readUTF() else null
}
