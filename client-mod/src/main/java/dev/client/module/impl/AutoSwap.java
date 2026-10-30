package dev.client.module.impl;

import dev.client.module.BoolSetting;
import dev.client.module.Module;
import dev.client.module.NumSetting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Locale;

/**
 * Keeps the off-hand filled with the same kind of item: when a totem pops or a sphere is used up,
 * the next totem / sphere from the inventory is moved into the off-hand.
 * A "sphere" is any item whose name contains "сфер" or "sphere".
 */
public class AutoSwap extends Module {
    private enum Kind { NONE, TOTEM, SPHERE }

    private final BoolSetting totems = add(new BoolSetting("Totems", true));
    private final BoolSetting spheres = add(new BoolSetting("Spheres", true));
    private final NumSetting delay = add(new NumSetting("Delay ms", 80, 0, 500, 5));

    private Kind lastKind = Kind.NONE;
    private long swapAt = -1;

    public AutoSwap() {
        super("AutoSwap", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        lastKind = Kind.NONE;
        swapAt = -1;
    }

    private static boolean isSphere(ItemStack s) {
        if (s.isEmpty()) return false;
        String n = s.getName().getString().toLowerCase(Locale.ROOT);
        return n.contains("сфер") || n.contains("sphere");
    }

    private Kind kindOf(ItemStack s) {
        if (s.isEmpty()) return Kind.NONE;
        if (totems.value && s.getItem() == Items.TOTEM_OF_UNDYING) return Kind.TOTEM;
        if (spheres.value && isSphere(s)) return Kind.SPHERE;
        return Kind.NONE;
    }

    private boolean matches(ItemStack s, Kind k) {
        if (s.isEmpty()) return false;
        if (k == Kind.TOTEM) return s.getItem() == Items.TOTEM_OF_UNDYING;
        if (k == Kind.SPHERE) return isSphere(s);
        return false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!mc.player.isAlive()) {
            lastKind = Kind.NONE;
            swapAt = -1;
            return;
        }
        if (mc.currentScreen instanceof HandledScreen) return; // a container is open, don't interfere

        Kind cur = kindOf(mc.player.getOffHandStack());
        if (cur != Kind.NONE) {
            lastKind = cur; // remember what is in the off-hand right now
            swapAt = -1;
            return;
        }
        if (!mc.player.getOffHandStack().isEmpty() || lastKind == Kind.NONE) return;

        // off-hand just became empty: schedule a refill with the same kind
        long now = System.currentTimeMillis();
        if (swapAt < 0) swapAt = now + (long) delay.value;
        if (now < swapAt) return;

        int slot = findSlot(lastKind);
        if (slot >= 0) {
            // button 40 = swap the clicked slot with the off-hand
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 40,
                    SlotActionType.SWAP, mc.player);
        }
        lastKind = Kind.NONE;
        swapAt = -1;
    }

    /** Returns a PlayerScreenHandler slot id of the first matching stack, or -1. */
    private int findSlot(Kind k) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.inventory.main.get(i);
            if (matches(s, k)) return i < 9 ? 36 + i : i; // hotbar 0-8 -> slots 36-44, main -> 9-35
        }
        return -1;
    }
}
