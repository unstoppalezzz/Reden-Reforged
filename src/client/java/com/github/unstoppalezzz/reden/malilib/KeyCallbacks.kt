package com.github.unstoppalezzz.reden.malilib

import com.github.unstoppalezzz.reden.Reden
import com.github.unstoppalezzz.reden.network.Undo
import com.github.unstoppalezzz.reden.undo.AxiomKeys
import com.github.unstoppalezzz.reden.utils.multiver.Text
//? if <1.19
/*import com.github.unstoppalezzz.reden.utils.multiver.sendSystemMessage*/
import com.github.unstoppalezzz.reden.utils.red
import fi.dy.masa.malilib.config.options.ConfigHotkey
import fi.dy.masa.malilib.hotkeys.KeybindMulti
import java.util.function.Predicate
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft

fun configureKeyCallbacks(mc: Minecraft) {
    REDEN_CONFIG_KEY.callback {
        //? if >=26.1 {
        mc.setScreenAndShow(GuiConfigs())
        //?} else {
        /*mc.setScreen(GuiConfigs())
        *///?}
        true
    }
    UNDO_KEY.callback {
        if (!undoAllowed(mc))
            return@callback false
        else
            sendUndo(Undo(0))
        true
    }
    REDO_KEY.callback {
        if (undoAllowed(mc)) {
            sendUndo(Undo(1))
            true
        } else false
    }
    AxiomKeys.supersede = Predicate { isUndo ->
        val keys = (if (isUndo) UNDO_KEY else REDO_KEY).keybind.keys
        keys.isNotEmpty() && keys.all(KeybindMulti::isKeyDown) && undoAllowed(mc) && canSendUndo()
    }
}

private fun undoAllowed(mc: Minecraft) =
    mc.gameMode?.playerMode?.isCreative == true || (mc.gameMode != null && !CREATIVE_ONLY.booleanValue)

private fun canSendUndo(): Boolean =
    //? if >=1.20.5 {
    ClientPlayNetworking.canSend(Undo.ID)
    //?} else {
    /*Minecraft.getInstance().connection != null
    *///?}

fun syncUndoSettings() {
    if (!canSendUndo()) return
    sendUndo(Undo(if (CREATIVE_ONLY.booleanValue) Undo.REQUEST_CREATIVE_ONLY else Undo.REQUEST_ALLOW_SURVIVAL))
}

private fun sendUndo(packet: Undo) {
    //? if >=1.20.5 {
    ClientPlayNetworking.send(packet)
    //?} else {
    /*ClientPlayNetworking.send(Undo.ID, Undo.encode(packet))
    *///?}
}

private fun ConfigHotkey.callback(action: () -> Boolean) {
    keybind.setCallback { _, _ ->
        try {
            if (action()) {
//                onFunctionUsed(name)
                true
            } else false
        } catch (e: Exception) {
            Reden.LOGGER.error("Error when executing hotkey $name", e)
//            reportException(e)
//? if <= 1.21.1 || >=26.1 {
            Minecraft.getInstance().player?.sendSystemMessage(Text.literal("Error when executing hotkey $name").red())
//?} else {
            /*Minecraft.getInstance().player?.displayClientMessage(Text.literal("Error when executing hotkey $name").red(), false)
*///?}
            false
        }
    }
}
