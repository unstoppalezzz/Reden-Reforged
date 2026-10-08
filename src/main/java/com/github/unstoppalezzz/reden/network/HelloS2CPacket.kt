package com.github.unstoppalezzz.reden.network

import com.github.unstoppalezzz.reden.Reden
import com.github.unstoppalezzz.reden.network.HelloS2CPacket.Companion.ID
import kotlinx.serialization.Serializable
//? if >=1.20.5 {
import com.github.unstoppalezzz.reden.network.HelloS2CPacket.Companion.CODEC
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
*///?}

@Serializable
class HelloS2CPacket(
    val versionString: String,
    val featureSet: Set<String>,
) : CustomPacketPayload {
    override fun type() = ID

    companion object : PacketCodecHelper<HelloS2CPacket> by PacketCodec(Reden.identifier("hello_s2c"))
}

private fun helloPacket() = HelloS2CPacket(
    Reden.MOD_VERSION, setOf(
        "reden",
        "undo",
    )
)

fun registerHello() {
    //? if >=1.20.5 {
    //? if >=26.1 {
    PayloadTypeRegistry.clientboundConfiguration().register(ID, CODEC)
    //?} else {
    /*PayloadTypeRegistry.configurationS2C().register(ID, CODEC)
    *///?}
    ServerConfigurationConnectionEvents.CONFIGURE.register { handler, _ ->
        ServerConfigurationNetworking.send(handler, helloPacket())
    }
    //?} else {
    /*ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
        ServerPlayNetworking.send(handler.player, ID, HelloS2CPacket.encode(helloPacket()))
    }
    *///?}
}
