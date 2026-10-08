package com.github.unstoppalezzz.reden.network

import com.github.unstoppalezzz.reden.utils.codec.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer
import net.minecraft.network.FriendlyByteBuf
//? if >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
//?} else {
/*import io.netty.buffer.Unpooled
*///?}
//? if >= 1.21.11 {
import net.minecraft.resources.Identifier
//?} else {
/*import net.minecraft.resources.ResourceLocation as Identifier
*///?}
import kotlin.reflect.typeOf

//? if <1.20.5 {
/*/^*
 * Stand-in for Minecraft's CustomPacketPayload, which only exists since 1.20.5.
 * Packets are sent through Fabric's raw FriendlyByteBuf networking API instead.
 ^/
interface CustomPacketPayload {
    fun type(): Identifier
}
*///?}

@OptIn(ExperimentalSerializationApi::class)
@Suppress("PropertyName")
interface PacketCodecHelper<T : CustomPacketPayload> {
    companion object {
        val cbor = Cbor {
            serializersModule = SerializersModule {
                include(serializersModuleOf(UUIDSerializer))
                include(serializersModuleOf(BlockPosSerializer))
                include(serializersModuleOf(IdentifierSerializer))
                include(serializersModuleOf(Vec3dSerializer))
                include(serializersModuleOf(NbtSerializer))
                include(serializersModuleOf(TextSerializer))
                include(serializersModuleOf(FabricVersionSerializer))
            }
        }
    }

    //? if >=1.20.5 {
    val ID: CustomPacketPayload.Type<T>
    val CODEC: StreamCodec<FriendlyByteBuf, T>
    fun playC2S() {
        //? if >=26.1 {
        PayloadTypeRegistry.serverboundPlay().register(ID, CODEC)
        //?} else {
        /*PayloadTypeRegistry.playC2S().register(ID, CODEC)
        *///?}
    }
    //?} else {
    /*val ID: Identifier
    fun encode(packet: T): FriendlyByteBuf
    fun decode(buf: FriendlyByteBuf): T
    *///?}
}

@OptIn(ExperimentalSerializationApi::class)
@Suppress("FunctionName")
inline fun <reified T : CustomPacketPayload> PacketCodec(id: Identifier): PacketCodecHelper<T> {
    val type = typeOf<T>()
    return object : PacketCodecHelper<T> {
        //? if >=1.20.5 {
        override val ID = CustomPacketPayload.Type<T>(id)
        override val CODEC = StreamCodec.of<FriendlyByteBuf, T>({ buf, obj ->
            buf.writeByteArray(PacketCodecHelper.cbor.encodeToByteArray(serializer(type), obj))
        }, { buf ->
            val bytes = buf.readByteArray()
            PacketCodecHelper.cbor.decodeFromByteArray(serializer(type), bytes) as T
        })
        //?} else {
        /*override val ID = id
        override fun encode(packet: T) = FriendlyByteBuf(Unpooled.buffer()).apply {
            writeByteArray(PacketCodecHelper.cbor.encodeToByteArray(serializer(type), packet))
        }
        override fun decode(buf: FriendlyByteBuf) =
            PacketCodecHelper.cbor.decodeFromByteArray(serializer(type), buf.readByteArray()) as T
        *///?}
    }
}
