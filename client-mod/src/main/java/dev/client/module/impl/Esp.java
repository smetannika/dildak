package dev.client.module.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.client.module.BoolSetting;
import dev.client.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Esp extends Module {
    private final BoolSetting players = add(new BoolSetting("Players", true));
    private final BoolSetting hostile = add(new BoolSetting("Hostile mobs", true));
    private final BoolSetting passive = add(new BoolSetting("Passive mobs", false));
    private final BoolSetting walls = add(new BoolSetting("Through walls", true));

    public Esp() {
        super("ESP", Category.RENDER);
    }

    @Override
    public void onWorldRender(WorldRenderContext ctx) {
        MatrixStack ms = ctx.matrixStack();
        Vec3d cam = ctx.camera().getPos();
        float pt = ctx.tickDelta();

        VertexConsumerProvider.Immediate vcp = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLines());

        if (walls.value) RenderSystem.disableDepthTest();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity) || e == mc.player || !e.isAlive()) continue;

            float r, g, b;
            if (e instanceof PlayerEntity) {
                if (!players.value) continue;
                r = 1f; g = 0.2f; b = 0.2f;
            } else if (e instanceof HostileEntity) {
                if (!hostile.value) continue;
                r = 1f; g = 0.6f; b = 0.1f;
            } else {
                if (!passive.value) continue;
                r = 0.2f; g = 1f; b = 0.4f;
            }

            double ox = MathHelper.lerp(pt, e.lastRenderX, e.getX()) - e.getX();
            double oy = MathHelper.lerp(pt, e.lastRenderY, e.getY()) - e.getY();
            double oz = MathHelper.lerp(pt, e.lastRenderZ, e.getZ()) - e.getZ();
            Box box = e.getBoundingBox().offset(ox, oy, oz);
            WorldRenderer.drawBox(ms, vc, box, r, g, b, 1.0f);
        }

        ms.pop();
        vcp.draw(RenderLayer.getLines());
        if (walls.value) RenderSystem.enableDepthTest();
    }
}
