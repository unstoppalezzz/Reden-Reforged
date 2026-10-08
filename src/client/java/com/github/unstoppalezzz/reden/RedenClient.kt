package com.github.unstoppalezzz.reden

import com.github.unstoppalezzz.reden.malilib.GuiConfigs
import com.github.unstoppalezzz.reden.malilib.HOTKEYS
import com.github.unstoppalezzz.reden.malilib.configureKeyCallbacks
import com.github.unstoppalezzz.reden.malilib.getAllOptions
//? if <26.1
/*import com.github.unstoppalezzz.reden.network.Undo*/
import com.github.unstoppalezzz.reden.network.registerClientPackets
import com.github.unstoppalezzz.reden.utils.checkMalilib
import com.github.unstoppalezzz.reden.malilib.DEBUG_LOGGING
import com.github.unstoppalezzz.reden.utils.setDebug
import com.google.gson.Gson
import com.google.gson.JsonObject
import fi.dy.masa.malilib.config.ConfigManager
import fi.dy.masa.malilib.config.ConfigUtils
import fi.dy.masa.malilib.config.IConfigHandler
import fi.dy.masa.malilib.event.InitializationHandler
import fi.dy.masa.malilib.event.InputEventHandler
import fi.dy.masa.malilib.hotkeys.IKeybindManager
import fi.dy.masa.malilib.hotkeys.IKeybindProvider
import fi.dy.masa.malilib.util.FileUtils
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.Minecraft
import java.nio.file.Files
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists

val GSON = Gson()

private fun configDirectory() =
    //? if >=1.21 {
    FileUtils.getConfigDirectoryAsPath()
    //?} else {
    /*FileUtils.getConfigDirectory().toPath()
    *///?}

fun loadMalilibSettings() {
    val path = configDirectory().resolve("reden/config.json")
        .createParentDirectories()
    if (!path.exists()) {
        return
    }
    val jo = GSON.fromJson(Files.readString(path), JsonObject::class.java)
    ConfigUtils.readConfigBase(jo, Reden.MOD_NAME, getAllOptions())
    setDebug(DEBUG_LOGGING.booleanValue)
}

fun saveMalilibOptions() {
    val jo = JsonObject()
    ConfigUtils.writeConfigBase(jo, Reden.MOD_NAME, getAllOptions())
    Files.writeString(
        configDirectory().resolve("reden/config.json")
            .createParentDirectories(),
        GSON.toJson(jo)
    )
}

class RedenClient : ClientModInitializer {
    override fun onInitializeClient() {
        checkMalilib()
        //? if <26.1 {
        /*try {
            Undo.register()
        } catch (t: Throwable) {
            Reden.LOGGER.error("Failed to register Undo payload on client", t)
        }
        *///?}
        registerClientPackets()
        //? if >=1.21 {
        fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN.registerConfigScreenFactory(
            fi.dy.masa.malilib.util.data.ModInfo(
                Reden.MOD_ID,
                Reden.MOD_NAME,
                ::GuiConfigs
            )
        )
        //?}
        InitializationHandler.getInstance().registerInitializationHandler {
            ConfigManager.getInstance().registerConfigHandler(Reden.MOD_ID, object : IConfigHandler {
                override fun load() {
                    loadMalilibSettings()
                }

                override fun save() {
                    saveMalilibOptions()
                }
            })
            loadMalilibSettings()
            DEBUG_LOGGING.setValueChangeCallback { setDebug(it.booleanValue) }
            val mc = Minecraft.getInstance()
            configureKeyCallbacks(mc)

            InputEventHandler.getKeybindManager().registerKeybindProvider(object : IKeybindProvider {
                override fun addKeysToMap(iKeybindManager: IKeybindManager) {
                    HOTKEYS.forEach {
                        iKeybindManager.addKeybindToMap(it.keybind)
                    }
                }

                override fun addHotkeys(keybindManager: IKeybindManager) {
                    keybindManager.addHotkeysForCategory(Reden.MOD_NAME, "reden.hotkeys.category.generic_hotkeys", HOTKEYS)
                }
            })
        }
    }
}
