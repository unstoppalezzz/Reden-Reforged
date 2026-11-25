package com.github.unstoppalezzz.reden.mixin.chat;

import com.github.unstoppalezzz.reden.network.ClientKt;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void reden$routeUndoMessage(Component message, CallbackInfo ci) {
        if (ClientKt.hideUndoMessageFromChat(message)) ci.cancel();
    }
}
