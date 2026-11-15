package com.framebetween.app.util

/**
 * API 密钥展示脱敏：首尾各保留 [visible] 位，中间字符替换成等长星号。
 *
 * 输出长度与原文严格相同，因此输入框的 VisualTransformation 可以沿用恒等偏移映射，
 * 光标与选区位置不会因为脱敏而错位。真实密钥仍保存在状态与本机配置里，不参与展示。
 */
object ApiKeyMask {

    fun mask(raw: String, visible: Int = 4): String {
        if (raw.isEmpty()) return ""
        val keep = visible.coerceAtLeast(0)
        // 太短的密钥首尾会重叠，全部遮蔽，避免等于没脱敏。
        if (keep == 0 || raw.length <= keep * 2) return "*".repeat(raw.length)
        return raw.take(keep) + "*".repeat(raw.length - keep * 2) + raw.takeLast(keep)
    }
}
