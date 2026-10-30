package dev.client.module.impl;

import dev.client.module.BoolSetting;
import dev.client.module.Module;
import dev.client.module.NumSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.concurrent.ThreadLocalRandom;

public class TriggerBot extends Module {
    private final NumSetting cooldown = add(new NumSetting("Cooldown", 0.9, 0.5, 1.0, 0.05));
    private final NumSetting minDelay = add(new NumSetting("Min delay ms", 40, 0, 500, 5));
    private final NumSetting maxDelay = add(new NumSetting("Max delay ms", 120, 0, 500, 5));
    private final BoolSetting playersOnly = add(new BoolSetting("Players only", true));
    private final BoolSetting skipInvisible = add(new BoolSetting("Skip invisible", false));

    private long attackAt = -1;

    public TriggerBot() {
        super("TriggerBot", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        attackAt = -1;
    }

    @Override
    public void onFrame(float dt) {
        if (mc.currentScreen != null || mc.player == null || mc.interactionManager == null) return;

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof EntityHitResult)) {
            attackAt = -1;
            return;
        }
        Entity e = ((EntityHitResult) hit).getEntity();
        if (!(e instanceof LivingEntity) || e == mc.player || !e.isAlive()
                || (playersOnly.value && !(e instanceof PlayerEntity))
                || (skipInvisible.value && e.isInvisible())) {
            attackAt = -1;
            return;
        }

        if (mc.player.getAttackCooldownProgress(0.0f) < cooldown.f()) return;

        long now = System.currentTimeMillis();
        if (attackAt < 0) {
            int lo = (int) Math.min(minDelay.value, maxDelay.value);
            int hi = (int) Math.max(minDelay.value, maxDelay.value);
            attackAt = now + (hi > lo ? ThreadLocalRandom.current().nextInt(lo, hi + 1) : lo);
        }
        if (now >= attackAt) {
            mc.interactionManager.attackEntity(mc.player, e);
            mc.player.swingHand(Hand.MAIN_HAND);
            attackAt = -1;
        }
    }
}
