package com.github.unstoppalezzz.reden.mixin.client.chat;

import com.github.unstoppalezzz.reden.access.VisibleChatHudLineAccess;
//? if >=26.1 {
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
//?} else {
/*import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
*///?}
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if <26.1
/*import org.spongepowered.asm.mixin.injection.ModifyArg;*/
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <26.1 {
/*import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Iterator;
import java.util.List;
*///?}

@Mixin(ChatComponent.class)
public class ChatHudMixin {
    @Unique private Component currentMessage;

    @Inject(
        //? if >=26.1 {
        method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        //?} else {
        /*method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        *///?}
        at = @At("HEAD")
    )
    private void addMessage(Component message, MessageSignature messageSignature, GuiMessageTag guiMessageTag, CallbackInfo ci) {
        currentMessage = message;
        //? if <26.1 {
        /*if (message != null) {
            String s = message.getString();
            if (s.contains("[Reden/Undo]") || s.contains("Reden/Undo")) {
                try {
                    // remove previous Reden/Undo messages from trimmedMessages history
                    ChatComponent chat = (ChatComponent) (Object) this;
                    chat.trimmedMessages.removeIf(line -> {
                        try {
                            Component c = ((VisibleChatHudLineAccess) line).getText$reden();
                            return c != null && c.getString().contains("[Reden/Undo]");
                        } catch (Throwable t) {
                            return false;
                        }
                    });
                } catch (Throwable ignored) {}
            }
        }
        *///?}
    }

    //? if <26.1 {
    /*@ModifyArg(
        //? if >=1.20.5 {
        method = "addMessageToDisplayQueue",
        //?} else {
        /^method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
        ^///?}
        at = @At(value = "INVOKE", target = "Ljava/util/List;add(ILjava/lang/Object;)V", ordinal = 0),
        index = 1
    )
    private Object addVisibleMessage(Object element) {
        GuiMessage.Line visible = (GuiMessage.Line) element;
        ((VisibleChatHudLineAccess) element).setText$reden(currentMessage);
        return visible;
    }

    @Redirect(
        //? if >=1.20.5 {
        method = "addMessageToDisplayQueue",
        //?} else {
        /^method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
        ^///?}
        at = @At(value = "INVOKE", target = "Ljava/util/List;add(ILjava/lang/Object;)V", ordinal = 0)
    )
    private void redirectAddVisibleMessage(List list, int index, Object element) {
        if (currentMessage != null) {
            String s = currentMessage.getString();
            if (s.contains("[Reden/Undo]") || s.contains("Reden/Undo")) {
                // remove previous Reden/Undo messages from the visible list so only the most recent is shown
                Iterator it = list.iterator();
                while (it.hasNext()) {
                    Object o = it.next();
                    if (o instanceof GuiMessage.Line) {
                        try {
                            Component c = ((VisibleChatHudLineAccess) o).getText$reden();
                            if (c != null && c.getString().contains("[Reden/Undo]")) {
                                it.remove();
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                list.add(index, element);
                return;
            }
        }
        list.add(index, element);
    }

    //? if >= 1.21.9 {
    @Redirect(
        method = "addMessage",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;openChatScreen(Lnet/minecraft/client/gui/components/ChatComponent$ChatMethod;)V")
    )
    private void redirectOpenChatScreen(net.minecraft.client.gui.components.ChatComponent chat, net.minecraft.client.gui.components.ChatComponent.ChatMethod method) {
        // keep legacy redirect but don't rely on it; try to open normally
        chat.openChatScreen(method);
    }

    @Inject(
        method = "openChatScreen(Lnet/minecraft/client/gui/components/ChatComponent$ChatMethod;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onOpenChatScreen(net.minecraft.client.gui.components.ChatComponent.ChatMethod method, CallbackInfo ci) {
        try {
            ChatComponent chat = (ChatComponent) (Object) this;
            for (Object line : chat.trimmedMessages) {
                try {
                    Component c = ((VisibleChatHudLineAccess) line).getText$reden();
                    if (c != null) {
                        String s = c.getString();
                        if (s.contains("[Reden/Undo]") || s.contains("Reden/Undo")) {
                            ci.cancel();
                            return;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
    //?}
    *///?}

    // TODO (26.x): 26.2 chat rendering changed again; the old List.add-based modifier no longer matches ChatComponent internals.
    // Re-enable only after identifying the exact insertion point for GuiMessage.Line in the Mojang implementation.
}
