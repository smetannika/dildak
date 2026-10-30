package dev.client.module;

import dev.client.gui.ClickGui;
import dev.client.module.impl.AimAssist;
import dev.client.module.impl.AutoSwap;
import dev.client.module.impl.Esp;
import dev.client.module.impl.FpsHud;
import dev.client.module.impl.KeybindHud;
import dev.client.module.impl.ServerHelper;
import dev.client.module.impl.TargetHud;
import dev.client.module.impl.TriggerBot;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    public static final int OPEN_GUI_KEY = 344; // GLFW_KEY_RIGHT_SHIFT

    public static final List<Module> modules = new ArrayList<>();

    private static boolean guiKeyWasDown;
    private static final boolean[] keyWasDown = new boolean[512];
    private static long lastFrame = System.nanoTime();

    public static void init() {
        modules.add(new TriggerBot());
        modules.add(new AimAssist());
        modules.add(new AutoSwap());
        modules.add(new ServerHelper());
        modules.add(new Esp());
        modules.add(new FpsHud());
        modules.add(new TargetHud());
        modules.add(new KeybindHud());

        HudRenderCallback.EVENT.register((ms, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
            for (Module m : modules) {
                if (m.enabled && m instanceof HudModule) ((HudModule) m).render(ms);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(ModuleManager::tick);

        WorldRenderEvents.START.register(ctx -> {
            long now = System.nanoTime();
            float dt = Math.min((now - lastFrame) / 1_000_000_000f, 0.1f);
            lastFrame = now;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;
            for (Module m : modules) if (m.enabled) m.onFrame(dt);
        });

        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;
            for (Module m : modules) if (m.enabled) m.onWorldRender(ctx);
        });
    }

    private static void tick(MinecraftClient mc) {
        if (mc.getWindow() == null) return;
        long handle = mc.getWindow().getHandle();

        boolean guiDown = InputUtil.isKeyPressed(handle, OPEN_GUI_KEY);
        if (guiDown && !guiKeyWasDown && mc.currentScreen == null && mc.player != null) {
            mc.openScreen(new ClickGui());
        }
        guiKeyWasDown = guiDown;

        if (mc.player == null || mc.world == null) return;

        for (Module m : modules) {
            if (m.key > 0 && m.key < keyWasDown.length) {
                boolean down = InputUtil.isKeyPressed(handle, m.key);
                if (down && !keyWasDown[m.key] && mc.currentScreen == null) m.toggle();
                keyWasDown[m.key] = down;
            }
            if (m.enabled) m.onTick();
        }
    }
}
