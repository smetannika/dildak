package dev.client.module.impl;

import dev.client.gui.ClickGui;
import dev.client.module.HudModule;
import dev.client.gui.Render2D;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class TargetHud extends HudModule {
    private LivingEntity target;
    private long seen;

    public TargetHud() {
        super("Target HUD", 6, 40, true);
        w = 130;
        h = 34;
    }

    @Override
    public void render(MatrixStack ms) {
        long now = System.currentTimeMillis();
        HitResult hr = mc.crosshairTarget;
        if (hr instanceof EntityHitResult) {
            Entity e = ((EntityHitResult) hr).getEntity();
            if (e instanceof LivingEntity && e != mc.player) {
                target = (LivingEntity) e;
                seen = now;
            }
        }

        boolean live = target != null && target.isAlive() && now - seen < 3000;
        boolean preview = !live && mc.currentScreen instanceof ClickGui; // so it can be dragged
        if (!live && !preview) return;

        String name = live ? target.getName().getString() : "Target";
        float hp = live ? target.getHealth() : 20f;
        float max = live ? Math.max(target.getMaxHealth(), 1f) : 20f;
        float dist = live ? mc.player.distanceTo(target) : 0f;
        float ratio = Math.max(0f, Math.min(1f, hp / max));

        w = Math.max(130, mc.textRenderer.getWidth(name) + 12);
        h = 34;
        Render2D.shadow(ms, 5, x, y, x + w, y + h, 0.9f);
        Render2D.rrect(ms, 5, x, y, x + w, y + h, 0xDD121216);
        mc.textRenderer.drawWithShadow(ms, name, x + 4, y + 3, 0xFFFFFFFF);

        int bx = x + 4, by = y + 15, bw = w - 8;
        Render2D.rrect(ms, 3, bx, by, bx + bw, by + 6, 0xFF333333);
        int color = ratio > 0.5f ? 0xFF4CAF50 : (ratio > 0.25f ? 0xFFFFC107 : 0xFFF44336);
        Render2D.rrect(ms, 3, bx, by, bx + (int) (bw * ratio), by + 6, color);

        mc.textRenderer.drawWithShadow(ms, String.format("%.1f / %.1f", hp, max), x + 4, y + 23, 0xFFDDDDDD);
        String d = String.format("%.1fm", dist);
        mc.textRenderer.drawWithShadow(ms, d, x + w - 4 - mc.textRenderer.getWidth(d), y + 23, 0xFFAAAAAA);
    }
}
