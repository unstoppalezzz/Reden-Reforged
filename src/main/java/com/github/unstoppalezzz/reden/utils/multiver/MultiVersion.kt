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
    fun literal(text: String) = Component.literal(text)
    fun of(text: String?) = Component.nullToEmpty(text)
    fun empty() = Component.empty()
    fun translatable(key: String, vararg args: Any): Component {
        return Component.translatable(key, *args)
    }
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
        /*style.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(text)))
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
