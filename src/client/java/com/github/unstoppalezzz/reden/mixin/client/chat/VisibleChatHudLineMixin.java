package com.github.unstoppalezzz.reden.mixin.client.chat;

import com.github.unstoppalezzz.reden.access.VisibleChatHudLineAccess;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GuiMessage.Line.class)
public class VisibleChatHudLineMixin implements VisibleChatHudLineAccess {
    @Unique private Component content;
    @Override
    public Component getText$reden() {
        return this.content;
    }

    @Override
    public void setText$reden(Component text) {
        this.content = text;
    }
}
