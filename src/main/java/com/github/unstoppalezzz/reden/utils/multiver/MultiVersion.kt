package com.github.unstoppalezzz.reden.utils.multiver

//? if >=1.21.6 {
import net.minecraft.nbt.CompoundTag
//?}
//? if >=26.1 {
import net.minecraft.network.chat.ClickEvent
//?}
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
//? if >=1.21.6 {
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Entity
//?}
import net.minecraft.world.entity.player.Player
//? if >=1.21.6 {
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.world.level.storage.TagValueOutput
//?}
//? if >=26.1 {
import java.net.URI
//?}

object Text {
    //? if >=1.19 {
    fun literal(text: String): MutableComponent = Component.literal(text)
    fun empty(): MutableComponent = Component.empty()
    fun translatable(key: String, vararg args: Any): MutableComponent = Component.translatable(key, *args)
    //?} else {
    /*fun literal(text: String): MutableComponent = net.minecraft.network.chat.TextComponent(text)
    fun empty(): MutableComponent = net.minecraft.network.chat.TextComponent("")
    fun translatable(key: String, vararg args: Any): MutableComponent = net.minecraft.network.chat.TranslatableComponent(key, *args)
    *///?}
    fun of(text: String?) = Component.nullToEmpty(text)
}

//? if >=26.1 {
fun MutableComponent.clickOpenUrl(url: String) = apply {
    withStyle { style ->
        style.withClickEvent(ClickEvent.OpenUrl(URI(url)))
    }
}
//?}

fun MutableComponent.hoverShowText(text: String) = apply {
    withStyle { style ->
        //? if >=1.21.5 {
        style.withHoverEvent(HoverEvent.ShowText(Component.literal(text)))
        //?} else {
        /*style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(text)))
        *///?}
    }
}

fun Player.sendSystemMessage(text: Component) {
    //? if >=26.1 {
    sendSystemMessage(text)
    //?} else {
    /*displayClientMessage(text, false)
    *///?}
}

val net.minecraft.world.entity.Entity.levelCompat: net.minecraft.world.level.Level
    //? if >=1.20 {
    get() = level()
    //?} else {
    /*get() = level
    *///?}

//? if >=1.21.6 {
fun Entity.saveWithoutId(nbt: CompoundTag): CompoundTag {
    val vo = TagValueOutput.createWithContext(
        ProblemReporter.DISCARDING,
        level().registryAccess()
    )
    saveWithoutId(vo)
    return vo.buildResult()
}

fun Entity.load(nbt: CompoundTag) {
    val vi = TagValueInput.create(
        ProblemReporter.DISCARDING,
        level().registryAccess(),
        nbt
    )
    load(vi)
}
//?}

//? if <1.19.3 {
/*fun net.minecraft.world.item.ItemStack.copyWithCount(count: Int) = copy().also { it.count = count }

fun net.minecraft.nbt.Tag.sizeInBytes(): Int {
    val out = java.io.ByteArrayOutputStream()
    write(java.io.DataOutputStream(out))
    return out.size()
}
*///?}
