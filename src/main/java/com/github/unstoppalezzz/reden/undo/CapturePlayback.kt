package com.github.unstoppalezzz.reden.undo

import com.github.unstoppalezzz.reden.Reden
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerLevel
import java.lang.reflect.Method

object CapturePlayback {
    private const val MOD_ID = "g4mespeed-capture-playback"
    private const val LEVEL_ACCESS = "com.g4mesoft.captureplayback.access.GSIServerLevelAccess"

    val loaded: Boolean by lazy { FabricLoader.getInstance().isModLoaded(MOD_ID) }

    private val getPlaybackStreams: Method? by lazy {
        if (!loaded) return@lazy null
        try {
            Class.forName(LEVEL_ACCESS).getMethod("gcp_getPlaybackStreams")
        } catch (e: Throwable) {
            Reden.LOGGER.warn("Capture & Playback is loaded but its level access could not be found, playback won't be undoable", e)
            null
        }
    }

    fun hasPlayback(level: ServerLevel): Boolean {
        val method = getPlaybackStreams ?: return false
        return try {
            (method.invoke(level) as? Collection<*>)?.isNotEmpty() == true
        } catch (_: Throwable) {
            false
        }
    }

    fun isPlaybackCommand(command: String): Boolean {
        val trimmed = command.trimStart().removePrefix("/")
        return trimmed == "playback" || trimmed.startsWith("playback ")
    }
}
