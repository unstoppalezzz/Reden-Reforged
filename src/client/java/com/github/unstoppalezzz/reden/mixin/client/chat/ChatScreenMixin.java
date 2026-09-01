package com.github.unstoppalezzz.reden.mixin.client.chat;

import com.github.unstoppalezzz.reden.gui.QuickMenuWidget;
import com.github.unstoppalezzz.reden.malilib.MalilibSettingsKt;
import com.github.unstoppalezzz.reden.mixinhelper.ChatMixinHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Shadow @Nullable protected abstract Style getComponentStyleAt(double d, double e);
    @Unique
    QuickMenuWidget quickMenuWidget = null;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Redirect(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void keyPressed(Minecraft client, Screen screen) {
        if (screen == null) {
            if (isCurrentScreenViaGui(client)) {
                setScreenViaGuiCompat(client, null);
            }
        } else {
            setScreenViaGuiCompat(client, screen);
        }
    }

    @Unique
    private boolean isCurrentScreenViaGui(Minecraft client) {
        try {
            Object gui = client.gui;
            try {
                java.lang.reflect.Method screenMethod = gui.getClass().getMethod("screen");
                Object screen = screenMethod.invoke(gui);
                return screen == reden$getThis();
            } catch (Throwable ignored) {
            }
            try {
                java.lang.reflect.Field screenField = gui.getClass().getField("screen");
                Object screen = screenField.get(gui);
                return screen == reden$getThis();
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Unique
    private void setScreenViaGuiCompat(Minecraft client, Screen screen) {
        try {
            Object gui = client.gui;
            try {
                java.lang.reflect.Method ms = gui.getClass().getMethod("setScreen", Screen.class);
                ms.invoke(gui, screen);
                return;
            } catch (Throwable ignored) {
            }
            try {
                java.lang.reflect.Field screenField = gui.getClass().getField("screen");
                screenField.set(gui, screen);
                return;
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (quickMenuWidget != null && quickMenuWidget.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_2 && MalilibSettingsKt.CHAT_RIGHT_CLICK_MENU.getBooleanValue()) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private ChatScreen reden$getThis() {
        return (ChatScreen) (Object) this;
    }
}