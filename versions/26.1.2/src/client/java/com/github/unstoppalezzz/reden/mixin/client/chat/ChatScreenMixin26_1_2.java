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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.input.MouseButtonEvent;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin26_1_2 extends Screen {
    // Compatibility mixin for 26.1.2: uses reflection and optional injects
    @Unique
    QuickMenuWidget quickMenuWidget = null;

    protected ChatScreenMixin26_1_2(Component title) {
        super(title);
    }

    @Redirect(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"), require = 0)
    private void keyPressed_gui(Minecraft client, Screen screen) {
        if (screen == null) {
            if (isCurrentScreenViaGui(client)) {
                setScreenCompat(client, null);
            }
        } else {
            setScreenCompat(client, screen);
        }
    }

    @Redirect(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"), require = 0)
    private void keyPressed_mc(Minecraft client, Screen screen) {
        if (screen == null) {
            if (isCurrentScreenViaMinecraft(client)) {
                setScreenCompat(client, null);
            }
        } else {
            setScreenCompat(client, screen);
        }
    }

    @Unique
    private void setScreenCompat(Minecraft client, Screen screen) {
        try {
            java.lang.reflect.Method m = client.getClass().getMethod("setScreen", Screen.class);
            m.invoke(client, screen);
            return;
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Field guiField = client.getClass().getField("gui");
            Object gui = guiField.get(client);
            java.lang.reflect.Method ms = gui.getClass().getMethod("setScreen", Screen.class);
            ms.invoke(gui, screen);
            return;
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private boolean isCurrentScreenViaGui(Minecraft client) {
        try {
            java.lang.reflect.Field guiField = client.getClass().getField("gui");
            Object gui = guiField.get(client);
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
    private boolean isCurrentScreenViaMinecraft(Minecraft client) {
        try {
            try {
                java.lang.reflect.Method getScreen = client.getClass().getMethod("screen");
                Object screen = getScreen.invoke(client);
                return screen == reden$getThis();
            } catch (Throwable ignored) {
            }
            try {
                java.lang.reflect.Field f = client.getClass().getField("screen");
                Object screen = f.get(client);
                return screen == reden$getThis();
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (quickMenuWidget != null && quickMenuWidget.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_2 && MalilibSettingsKt.CHAT_RIGHT_CLICK_MENU.getBooleanValue()) {
            // TODO: restore the 26.2 chat right-click menu once the Mojang chat screen state API is migrated.
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void mouseClicked_new(MouseButtonEvent event, boolean consumed, CallbackInfoReturnable<Boolean> cir) {
        try {
            double mouseX = reflectGetDouble(event, "getX", "x", "getPosX", "posX", "getScreenX");
            double mouseY = reflectGetDouble(event, "getY", "y", "getPosY", "posY", "getScreenY");
            int button = reflectGetInt(event, "getButton", "button", "getAction", "action");
            if (quickMenuWidget != null && quickMenuWidget.mouseClicked(mouseX, mouseY, button)) {
                cir.setReturnValue(true);
                return;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_2 && MalilibSettingsKt.CHAT_RIGHT_CLICK_MENU.getBooleanValue()) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private double reflectGetDouble(Object obj, String... names) {
        for (String name : names) {
            try {
                try {
                    java.lang.reflect.Method m = obj.getClass().getMethod(name);
                    Object v = m.invoke(obj);
                    if (v instanceof Number) return ((Number) v).doubleValue();
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    java.lang.reflect.Field f = obj.getClass().getField(name);
                    Object v = f.get(obj);
                    if (v instanceof Number) return ((Number) v).doubleValue();
                } catch (NoSuchFieldException ignored) {
                }
            } catch (Throwable ignored) {
            }
        }
        return 0.0;
    }

    @Unique
    private int reflectGetInt(Object obj, String... names) {
        for (String name : names) {
            try {
                try {
                    java.lang.reflect.Method m = obj.getClass().getMethod(name);
                    Object v = m.invoke(obj);
                    if (v instanceof Number) return ((Number) v).intValue();
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    java.lang.reflect.Field f = obj.getClass().getField(name);
                    Object v = f.get(obj);
                    if (v instanceof Number) return ((Number) v).intValue();
                } catch (NoSuchFieldException ignored) {
                }
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    @Unique
    private ChatScreen reden$getThis() {
        return (ChatScreen) (Object) this;
    }
}
