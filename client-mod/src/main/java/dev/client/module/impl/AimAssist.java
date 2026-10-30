package dev.client.module.impl;

import dev.client.module.BoolSetting;
import dev.client.module.Module;
import dev.client.module.NumSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AimAssist extends Module {
    private final NumSetting range = add(new NumSetting("Range", 4.5, 1.0, 8.0, 0.1));
    private final NumSetting fov = add(new NumSetting("FOV", 360, 10, 360, 5));          // 360 = full circle
    private final NumSetting smooth = add(new NumSetting("Smoothness", 3, 0.5, 20, 0.5)); // higher = faster pull
    private final NumSetting inertia = add(new NumSetting("Inertia", 6, 1, 30, 0.5));     // lower = softer start/stop
    private final NumSetting maxSpeed = add(new NumSetting("Max deg/s", 180, 30, 720, 10));
    private final BoolSetting vertical = add(new BoolSetting("Vertical", true));
    private final BoolSetting onlyClick = add(new BoolSetting("Only when attacking", true));
    private final BoolSetting playersOnly = add(new BoolSetting("Players only", true));

    // current angular velocity (deg/s), smoothed so the aim eases in and out
    private float velYaw, velPitch;

    public AimAssist() {
        super("AimAssist", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        velYaw = velPitch = 0f;
    }

    @Override
    public void onFrame(float dt) {
        if (dt <= 0f) return;
        if (mc.currentScreen != null || mc.player == null || mc.world == null) {
            velYaw = velPitch = 0f;
            return;
        }

        float targetVelYaw = 0f, targetVelPitch = 0f;

        boolean active = !onlyClick.value || mc.options.keyAttack.isPressed();
        if (active) {
            Vec3d eye = mc.player.getCameraPosVec(1.0f);
            boolean fullCircle = fov.value >= 360.0;
            double bestScore = Double.MAX_VALUE;
            boolean found = false;
            float bestDy = 0f, bestDp = 0f;

            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity) || e == mc.player || !e.isAlive()) continue;
                if (playersOnly.value && !(e instanceof PlayerEntity)) continue;
                if (mc.player.distanceTo(e) > range.value) continue;

                Vec3d p = new Vec3d(e.getX(), e.getY() + e.getHeight() * 0.7, e.getZ());
                double dx = p.x - eye.x, dy = p.y - eye.y, dz = p.z - eye.z;
                float tYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float tPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                float dYaw = MathHelper.wrapDegrees(tYaw - mc.player.yaw);
                float dPitch = tPitch - mc.player.pitch;

                double ang = Math.hypot(dYaw, dPitch);
                if (!fullCircle && ang > fov.value / 2.0) continue;

                // prefer the closest one in angle; in full circle, break ties by distance too
                double score = ang + (fullCircle ? mc.player.distanceTo(e) * 0.5 : 0.0);
                if (score < bestScore) {
                    bestScore = score;
                    bestDy = dYaw;
                    bestDp = dPitch;
                    found = true;
                }
            }

            if (found) {
                // desired velocity proportional to the remaining angle (slows down when close)
                targetVelYaw = bestDy * smooth.f();
                targetVelPitch = vertical.value ? bestDp * smooth.f() : 0f;
            }
        }

        float cap = maxSpeed.f();
        targetVelYaw = MathHelper.clamp(targetVelYaw, -cap, cap);
        targetVelPitch = MathHelper.clamp(targetVelPitch, -cap, cap);

        // inertia: move the real velocity toward the desired one (frame-rate independent)
        float a = 1.0f - (float) Math.exp(-inertia.value * dt);
        velYaw += (targetVelYaw - velYaw) * a;
        velPitch += (targetVelPitch - velPitch) * a;

        if (Math.abs(velYaw) < 0.01f && Math.abs(velPitch) < 0.01f) return;

        mc.player.yaw += velYaw * dt;
        mc.player.pitch = MathHelper.clamp(mc.player.pitch + velPitch * dt, -90.0f, 90.0f);
    }
}
