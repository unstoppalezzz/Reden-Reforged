package com.github.unstoppalezzz.reden.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public abstract class QuickMenuWidget implements GuiEventListener {
    private final Screen parent;
    private final List<MenuEntry> entries = new ArrayList<>();
    private final Minecraft client = Minecraft.getInstance();
    int x;
    int y;
    int width;

    public QuickMenuWidget(Screen parent, int x, int y) {
        this.parent = parent;
        this.x = x;
        this.y = y;
    }

    public interface ClickAction {
        void onClick(MenuEntry entry, int button);
    }
    public static final ClickAction CLOSE_ACTION = (entry, button) -> entry.getParent().remove();
    public static final ClickAction EMPTY_ACTION = (entry, button) -> { };
    public class MenuEntry {
        Component name;
        ClickAction action;

        public MenuEntry(Component name, ClickAction action) {
            this.name = name;
            this.action = action;
        }

        QuickMenuWidget getParent() {
            return QuickMenuWidget.this;
        }

        public void setName(Component name) {
            this.name = name;
        }

        public void setAction(ClickAction action) {
            this.action = action;
        }

        public Component getName() {
            return name;
        }

        public ClickAction getAction() {
            return action;
        }
    }
    public void addEntry(Component name, ClickAction action) {
        entries.add(new MenuEntry(name, action));
    }

    public abstract void remove();

    // TODO: the old draw callback was removed in the 26.2 Mojang GUI API. Re-enable
    // once the custom right-click menu is migrated to the new GuiGraphicsExtractor pipeline.

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (entries.isEmpty()) {
            return false;
        }
        for (int i = 0; i < entries.size(); i++) {
            MenuEntry entry = entries.get(i);
            if (mouseX >= x && mouseX <= x + width && mouseY >= y + i * 14 && mouseY <= y + i * 14 + 14) {
                ClickAction action = entry.action;
                entry.action = CLOSE_ACTION;
                action.onClick(entry, button);
                return true;
            }
        }
        remove();
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        remove();
        return false;
    }

    public void setFocused(boolean focused) {

    }

    public boolean isFocused() {
        return false;
    }

}
