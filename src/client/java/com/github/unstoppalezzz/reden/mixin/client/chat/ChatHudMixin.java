package com.github.unstoppalezzz.reden.mixin.client.chat;

import com.github.unstoppalezzz.reden.access.VisibleChatHudLineAccess;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatHudMixin {
    @Unique private Component currentMessage;

    @Inject(
        method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At("HEAD")
    )
    private void addMessage(Component message, MessageSignature messageSignature, GuiMessageTag guiMessageTag, CallbackInfo ci) {
        currentMessage = message;
    }

    // TODO: 26.2 chat rendering changed again; the old List.add-based modifier no longer matches ChatComponent internals.
    // Re-enable only after identifying the exact insertion point for GuiMessage.Line in the Mojang implementation.
}
