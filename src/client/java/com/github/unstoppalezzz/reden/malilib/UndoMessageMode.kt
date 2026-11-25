package com.github.unstoppalezzz.reden.malilib

import com.github.unstoppalezzz.reden.access.NamedSliderConfig
import fi.dy.masa.malilib.config.options.ConfigInteger
import fi.dy.masa.malilib.util.StringUtils

enum class UndoMessageMode(private val id: String) {
    CHAT("chat"),
    INFO_BAR("info_bar"),
    NONE("none");

    val displayName: String get() = StringUtils.translate("reden.config.undoMessage.$id")
}

class UndoMessageConfig(name: String, default: UndoMessageMode, comment: String) :
    ConfigInteger(name, default.ordinal, 0, UndoMessageMode.values().size - 1, true, comment),
    NamedSliderConfig {

    val mode: UndoMessageMode
        get() = UndoMessageMode.values()[integerValue.coerceIn(0, UndoMessageMode.values().size - 1)]

    override fun shouldUseSlider() = true

    override fun toggleUseSlider() {}

    override fun getSliderDisplayName() = mode.displayName
}
