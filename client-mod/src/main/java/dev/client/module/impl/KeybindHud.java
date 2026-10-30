package dev.client.module.impl;

import dev.client.gui.ClickGui;
import dev.client.module.HudModule;
import dev.client.module.Module;
import dev.client.module.ModuleManager;
import dev.client.gui.Render2D;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;

/** Lists every module that has a key bound; green = enabled, grey = disabled. */
public class KeybindHud extends HudModule {
    public KeybindHud() {
        super("Keybinds", 6, 80, true);
    }

    @Override
    public void render(MatrixStack ms) {
        List<Module> bound = new ArrayList<>();
        for (Module m : ModuleManager.modules) {
            if (m.key > 0 && !(m instanceof HudModule && m == this)) bound.add(m);
        }
        boolean preview = bound.isEmpty() && mc.currentScreen instanceof ClickGui;
        if (bound.isEmpty() && !preview) return;

        String title = "Keybinds";
        int width = mc.textRenderer.getWidth(title);
        List<String> left = new ArrayList<>(), right = new ArrayList<>();
        for (Module m : bound) {
            String k = InputUtil.fromKeyCode(m.key, 0).getLocalizedText().getString();
            left.add(m.name);
            right.add("[" + k + "]");
            width = Math.max(width, mc.textRenderer.getWidth(m.name) + mc.textRenderer.getWidth("[" + k + "]") + 12);
        }
        if (preview) {
            left.add("No binds yet");
            right.add("");
            width = Math.max(width, mc.textRenderer.getWidth("No binds yet"));
        }

        w = width + 8;
        h = 14 + left.size() * 11;
        Render2D.shadow(ms, 5, x, y, x + w, y + h, 0.9f);
        Render2D.rrect(ms, 5, x, y, x + w, y + h, 0xDD121216);
        mc.textRenderer.drawWithShadow(ms, title, x + 4, y + 3, 0xFFFFFFFF);

        int ly = y + 15;
        for (int i = 0; i < left.size(); i++) {
            boolean on = i < bound.size() && bound.get(i).enabled;
            int col = on ? 0xFF4CAF50 : 0xFFAAAAAA;
            mc.textRenderer.drawWithShadow(ms, left.get(i), x + 4, ly, col);
            mc.textRenderer.drawWithShadow(ms, right.get(i), x + w - 4 - mc.textRenderer.getWidth(right.get(i)), ly, col);
            ly += 11;
        }
    }
}
