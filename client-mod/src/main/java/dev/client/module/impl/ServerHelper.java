package dev.client.module.impl;

import dev.client.module.BindSetting;
import dev.client.module.Module;
import dev.client.module.NumSetting;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.Locale;

/**
 * Server Helper (FT). Each BindSetting is a key that uses a special server item:
 * it finds the item by name, switches to it, right-clicks, and switches back.
 * "Трапка" = any item whose name contains "трапк" or "trap".
 */
public class ServerHelper extends Module {
    private final BindSetting trapBind = add(new BindSetting("Трапка (FT)"));
    private final NumSetting delay = add(new NumSetting("Switch delay ms", 60, 0, 300, 5));

    private boolean trapWasDown;

    // use sequence: 0 idle, 1 item selected -> waiting to use, 2 used -> waiting to switch back
    private int phase;
    private long at;
    private int prevSlot;
    private boolean usedSwap;
    private int invSlot = -1;

    public ServerHelper() {
        super("Server Helper", Category.SERVER);
        this.enabled = true; // binds work out of the box
    }

    @Override
    public void onDisable() {
        // never leave the player with a swapped slot
        if (phase != 0) restore();
        phase = 0;
    }

    private static boolean isTrap(ItemStack s) {
        if (s.isEmpty()) return false;
        String n = s.getName().getString().toLowerCase(Locale.ROOT);
        return n.contains("трапк") || n.contains("trap");
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            phase = 0;
            return;
        }
        long now = System.currentTimeMillis();

        if (phase == 1 && now >= at) {
            use();
            phase = 2;
            at = now + (long) delay.value;
            return;
        }
        if (phase == 2 && now >= at) {
            restore();
            phase = 0;
            return;
        }
        if (phase != 0) return;

        boolean down = trapBind.key > 0
                && InputUtil.isKeyPressed(mc.getWindow().getHandle(), trapBind.key);
        if (down && !trapWasDown && mc.currentScreen == null) startTrap(now);
        trapWasDown = down;
    }

    private void startTrap(long now) {
        int hotbar = -1, inv = -1;
        for (int i = 0; i < 9; i++) {
            if (isTrap(mc.player.inventory.main.get(i))) { hotbar = i; break; }
        }
        if (hotbar < 0) {
            for (int i = 9; i < 36; i++) {
                if (isTrap(mc.player.inventory.main.get(i))) { inv = i; break; }
            }
        }
        if (hotbar < 0 && inv < 0) return; // no trap in the inventory

        prevSlot = mc.player.inventory.selectedSlot;
        if (hotbar >= 0) {
            mc.player.inventory.selectedSlot = hotbar;
            usedSwap = false;
        } else {
            // swap the inventory item with the currently selected hotbar slot
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, inv, prevSlot,
                    SlotActionType.SWAP, mc.player);
            invSlot = inv;
            usedSwap = true;
        }
        phase = 1;
        at = now + (long) delay.value;
    }

    private void use() {
        HitResult hr = mc.crosshairTarget;
        ActionResult r = ActionResult.PASS;
        if (hr instanceof BlockHitResult && hr.getType() == HitResult.Type.BLOCK) {
            r = mc.interactionManager.interactBlock(mc.player, mc.world, Hand.MAIN_HAND, (BlockHitResult) hr);
        }
        if (!r.isAccepted()) {
            mc.interactionManager.interactItem(mc.player, mc.world, Hand.MAIN_HAND);
        }
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void restore() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (usedSwap && invSlot >= 0) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, invSlot, prevSlot,
                    SlotActionType.SWAP, mc.player);
        } else {
            mc.player.inventory.selectedSlot = prevSlot;
        }
        usedSwap = false;
        invSlot = -1;
    }
}
