package dev.client.module;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public enum Category {
        COMBAT("Combat"), RENDER("Render"), HUD("HUD"), SERVER("Server");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    public final String name;
    public final Category category;
    public final List<Setting> settings = new ArrayList<>();
    public boolean enabled;
    public boolean open; // settings expanded in GUI
    public int key = -1; // GLFW key code, -1 = unbound
    public float toggleAnim, openAnim; // GUI animation state

    protected Module(String name, Category category) {
        this.name = name;
        this.category = category;
    }

    protected <T extends Setting> T add(T s) {
        settings.add(s);
        return s;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean e) {
        if (enabled == e) return;
        enabled = e;
        if (e) onEnable(); else onDisable();
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    /** Called every rendered frame; dt in seconds. */
    public void onFrame(float dt) {}
    public void onWorldRender(WorldRenderContext ctx) {}
}
