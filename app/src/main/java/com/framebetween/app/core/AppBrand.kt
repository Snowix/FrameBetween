package com.framebetween.app.core
import com.framebetween.app.core.L10n

/** 界面内应用名只由「应用内界面语言」决定，不跟随系统资源限定符。 */
object AppBrand {
    fun displayName(): String = if (L10n.language == "en") "FrameBetween" else L10n.t("帧间")
}
