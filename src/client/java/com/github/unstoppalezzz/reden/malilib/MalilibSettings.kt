@file:Suppress("HasPlatformType")
@file:Environment(EnvType.CLIENT)

package com.github.unstoppalezzz.reden.malilib

import com.github.unstoppalezzz.reden.Reden
import com.google.gson.JsonParser
import fi.dy.masa.malilib.config.options.ConfigBase
import fi.dy.masa.malilib.config.options.ConfigBoolean
import fi.dy.masa.malilib.config.options.ConfigHotkey
import fi.dy.masa.malilib.hotkeys.IHotkey
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
//? if <26.3
/*import org.lwjgl.glfw.GLFW*/

// @formatter:off
@JvmField val HOTKEYS = mutableListOf<IHotkey>()
@JvmField val GENERIC_TAB = mutableListOf<ConfigBase<*>>()
@JvmField val DEBUG_TAB = mutableListOf<ConfigBase<*>>()
private fun <T : IHotkey> T.hotkey() = this.apply(HOTKEYS::add)
private fun <T : ConfigBase<*>> T.generic() = apply { (GENERIC_TAB.add(this)) }
private fun <T : ConfigBase<*>> T.debug() = apply { (DEBUG_TAB.add(this)) }

private val KEYBOARD_LAYOUT: String = run {
    //? if <26.3 {
    /*try {
        if (GLFW.glfwGetKeyName(GLFW.GLFW_KEY_Z, 0).equals("y", ignoreCase = true)) return@run "de"
    } catch (e: Throwable) {
    }
    *///?}
    "us"
}

private val DEFAULT_HOTKEYS: Map<String, Map<String, String>> = try {
    Reden::class.java.getResourceAsStream("/assets/reden-reforged/default_hotkeys.json")!!.reader().use { reader ->
        JsonParser.parseReader(reader).asJsonObject.entrySet().associate { (layout, keys) ->
            layout to keys.asJsonObject.entrySet().associate { it.key to it.value.asString }
        }
    }
} catch (e: Exception) {
    Reden.LOGGER.error("Failed to load default_hotkeys.json", e)
    emptyMap()
}

private fun defaultHotkey(name: String, fallback: String) =
    DEFAULT_HOTKEYS[KEYBOARD_LAYOUT]?.get(name) ?: DEFAULT_HOTKEYS["us"]?.get(name) ?: fallback

// Generic
@JvmField val REDEN_CONFIG_KEY = ConfigHotkey("redenConfigKey", defaultHotkey("redenConfigKey", "R,C"),"reden.config.redenConfigKey.comment").generic().hotkey()
@JvmField val NO_TIME_OUT = ConfigBoolean("noTimeOut", false, "reden.config.noTimeOut.comment").generic()
@JvmField val UNDO_KEY = ConfigHotkey("undoKey", defaultHotkey("undoKey", "LEFT_CONTROL,Z"),"reden.config.undoKey.comment").generic().hotkey()
@JvmField val REDO_KEY = ConfigHotkey("redoKey", defaultHotkey("redoKey", "LEFT_CONTROL,Y"),"reden.config.redoKey.comment").generic().hotkey()
@JvmField val CREATIVE_ONLY = ConfigBoolean("creativeOnly", true, "reden.config.creativeOnly.comment").generic()
//? if >=1.21 {
@JvmField val CHAT_RIGHT_CLICK_MENU = ConfigBoolean("chatRightClickMenu", true, "reden.config.chatRightClickMenu.comment", "reden.config.chatRightClickMenu", "reden.config.chatRightClickMenu").generic()
//?} else {
/*@JvmField val CHAT_RIGHT_CLICK_MENU = ConfigBoolean("chatRightClickMenu", true, "reden.config.chatRightClickMenu.comment", "reden.config.chatRightClickMenu").generic()
*///?}
// Debug
@JvmField val DEBUG_LOGGING = ConfigBoolean("debugLogging", false, "reden.config.debugLogging.comment").debug()
// @formatter:on

fun getAllOptions() = GENERIC_TAB + DEBUG_TAB
