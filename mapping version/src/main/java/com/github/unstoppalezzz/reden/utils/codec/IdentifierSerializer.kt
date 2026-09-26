package com.github.unstoppalezzz.reden.utils.codec

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
//? if >= 1.21.11 {
import net.minecraft.resources.Identifier
//?} else {
/*import net.minecraft.resources.ResourceLocation as Identifier
*///?}

object IdentifierSerializer : KSerializer<Identifier> {
    override val descriptor = PrimitiveSerialDescriptor("reden.identifier", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder) = Identifier.tryParse(decoder.decodeString())!!
    override fun serialize(encoder: Encoder, value: Identifier) {
        encoder.encodeString(value.toString())
    }
}
