package dev.client.module.impl;

import dev.client.module.HudModule;
import dev.client.gui.Render2D;
import net.minecraft.client.util.math.MatrixStack;

public class FpsHud extends HudModule {
    private int frames, fps;
    private long last = System.currentTimeMillis();

    public FpsHud() {
        super("FPS", 6, 6, true);
    }

    @Override
    public void render(MatrixStack ms) {
        frames++;
        long now = System.currentTimeMillis();
        if (now - last >= 1000) {
            fps = frames;
            frames = 0;
            last = now;
        }
        String t = "FPS: " + fps;
        w = mc.textRenderer.getWidth(t) + 8;
        h = 14;
        Render2D.shadow(ms, 5, x, y, x + w, y + h, 0.9f);
        Render2D.rrect(ms, 5, x, y, x + w, y + h, 0xDD121216);
        mc.textRenderer.drawWithShadow(ms, t, x + 4, y + 3, 0xFFFFFFFF);
    }
}
