package com.framebetween.app.core

import java.io.File

/**
 * 隐藏目录 / .nomedia 目录树的收录判定，扫描器与过滤层共用同一份规则。
 * 只用 java.io.File，没有 Android 依赖，JVM 单元测试可以直接建临时目录验证。
 */
object LibraryRules {
    fun allowed(
        hidden: Boolean,
        nomedia: Boolean,
        includeHidden: Boolean,
        includeNoMedia: Boolean
    ): Boolean = when {
        // 同时属于隐藏目录与 .nomedia 树时，任一开关开启即可显示
        nomedia && hidden -> includeNoMedia || includeHidden
        nomedia -> includeNoMedia
        hidden -> includeHidden
        else -> true
    }

    /** 向上找 .nomedia 标记时最多走几层，避免一路查到系统根目录拖慢扫描。 */
    private const val MAX_PARENTS = 8

    /**
     * 按真实路径补判 .nomedia：MediaStore.Video / Audio / Files 返回的条目不带
     * nomediaDir 标记，只靠扫描器打的标记会漏判，取消勾选后这些文件仍留在媒体库里。
     * [path] 可以是文件路径，也可以是目录路径；两者都会从它所在的目录开始向上找。
     */
    fun hasNoMediaMarker(path: String): Boolean {
        if (path.isBlank()) return false
        val target = File(path)
        var dir: File? = if (target.isDirectory) target else target.parentFile
        var depth = 0
        while (dir != null && depth < MAX_PARENTS) {
            if (File(dir, ".nomedia").isFile) return true
            dir = dir.parentFile
            depth++
        }
        return false
    }
}
