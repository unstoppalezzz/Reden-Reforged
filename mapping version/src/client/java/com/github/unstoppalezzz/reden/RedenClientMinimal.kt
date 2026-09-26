package com.github.unstoppalezzz.reden

import com.github.unstoppalezzz.reden.network.Undo
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.KeyMapping
import com.github.unstoppalezzz.reden.utils.multiver.createMiscKeyMapping
import org.lwjgl.glfw.GLFW
import net.minecraft.network.chat.Component

class RedenClientMinimal : ClientModInitializer {
    companion object {
        lateinit var undoKey: KeyMapping
        lateinit var redoKey: KeyMapping
    }

    override fun onInitializeClient() {
        try {
            Undo.register()
        } catch (t: Throwable) {
            Reden.LOGGER.error("Failed to register Undo payload on client", t)
        }

        // No client receiver registration: server sends system messages directly.

        undoKey = createMiscKeyMapping("key.reden.undo", GLFW.GLFW_KEY_Z)
        redoKey = createMiscKeyMapping("key.reden.redo", GLFW.GLFW_KEY_Y)
        KeyBindingHelper.registerKeyBinding(undoKey)
        KeyBindingHelper.registerKeyBinding(redoKey)

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            try {
                if (undoKey.consumeClick()) {
                    val ctrl = GLFW.glfwGetKey(GLFW.glfwGetCurrentContext(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                            GLFW.glfwGetKey(GLFW.glfwGetCurrentContext(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
                    if (ctrl) ClientPlayNetworking.send(Undo(0))
                }
                if (redoKey.consumeClick()) {
                    val ctrl = GLFW.glfwGetKey(GLFW.glfwGetCurrentContext(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                            GLFW.glfwGetKey(GLFW.glfwGetCurrentContext(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
                    if (ctrl) ClientPlayNetworking.send(Undo(1))
                }
            } catch (t: Throwable) {
                Reden.LOGGER.error("Error handling keybind", t)
            }
        }
    }
}
