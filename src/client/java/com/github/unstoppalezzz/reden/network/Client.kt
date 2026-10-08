package com.github.unstoppalezzz.reden.network

import com.github.unstoppalezzz.reden.Reden
import com.github.unstoppalezzz.reden.utils.multiver.Text
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.chat.Component
//? if >=1.20.5
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking

// @formatter:off
//? if > 1.21.1 {
import com.github.unstoppalezzz.reden.utils.multiver.sendSystemMessage
//?}

private const val MESSAGE_PREFIX = "reden.message."

fun translateMessage(category: String, key: String, vararg args: Any): Component {
    return Text.translatable("$MESSAGE_PREFIX$category.base", Text.translatable("$MESSAGE_PREFIX$category.$key", args))
}

private fun undoStatusMessage(status: Int) = when (status) {
    0     -> translateMessage("undo", "rollback_success")
    1     -> translateMessage("undo", "restore_success")
    2     -> translateMessage("undo", "no_blocks_info")
    16    -> translateMessage("undo", "no_permission")
    32    -> translateMessage("undo", "not_recording")
    64    -> translateMessage("undo", "busy")
    65536 -> translateMessage("undo", "unknown_error")
    else  -> translateMessage("undo", "unknown_status")
}

private fun onHello(packet: HelloS2CPacket) {
    Reden.LOGGER.info("Hello from server: $packet")
    Reden.LOGGER.info("Feature set: " + packet.featureSet.joinToString())
    packet.featureSet.forEach { name ->
        when (name) {
            //? if >=1.20.5 {
            "undo" -> ClientPlayNetworking.registerGlobalReceiver(Undo.ID) { packet, context ->
                context.player().sendSystemMessage(undoStatusMessage(packet.status))
            }
            //?} else {
            /*"undo" -> ClientPlayNetworking.registerGlobalReceiver(Undo.ID) { client, _, buf, _ ->
                val packet = Undo.decode(buf)
                client.execute { client.player?.sendSystemMessage(undoStatusMessage(packet.status)) }
            }
            *///?}
        }
    }
}

fun registerClientPackets() {
    //? if >=1.20.5 {
    ClientConfigurationNetworking.registerGlobalReceiver(HelloS2CPacket.ID) { packet, _ -> onHello(packet) }
    //?} else {
    /*// 1.20.1 has no configuration phase, the server sends the hello on join
    ClientPlayNetworking.registerGlobalReceiver(HelloS2CPacket.ID) { client, _, buf, _ ->
        val packet = HelloS2CPacket.decode(buf)
        client.execute { onHello(packet) }
    }
    *///?}
}
