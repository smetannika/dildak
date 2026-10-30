package dev.client.module;

import net.minecraft.client.util.math.MatrixStack;

/** A module that draws a movable block on the in-game HUD. Drag it with LMB while ClickGUI is open. */
public abstract class HudModule extends Module {
    public int x, y;
    public int w = 80, h = 14;

    protected HudModule(String name, int x, int y, boolean defaultOn) {
        super(name, Category.HUD);
        this.x = x;
        this.y = y;
        this.enabled = defaultOn;
    }

    /** Must update w and h, then draw at (x, y). */
    public abstract void render(MatrixStack ms);

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
