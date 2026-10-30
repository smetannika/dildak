package dev.client.gui;

import dev.client.Config;
import dev.client.module.BindSetting;
import dev.client.module.BoolSetting;
import dev.client.module.HudModule;
import dev.client.module.Module;
import dev.client.module.ModuleManager;
import dev.client.module.NumSetting;
import dev.client.module.Setting;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

import java.util.ArrayList;
import java.util.List;

/**
 * One floating window: category tabs on the left, module list in the middle, settings of the
 * selected module on the right.
 *
 * LMB module = toggle, RMB module = show its settings, MMB module (or the Bind button) = bind a key
 * (Backspace/Delete = unbind, Esc = cancel). Mouse wheel scrolls settings. Drag HUD blocks with LMB
 * outside the window. Right Shift closes.
 */
public class ClickGui extends Screen {
    // palette
    private static final int A1 = 0xFF8B5CF6;      // violet
    private static final int A2 = 0xFF38BDF8;      // sky blue
    private static final int BG = 0xF2101018;
    private static final int SIDEBAR = 0xFF0B0B11;
    private static final int CARD = 0xFF171720;
    private static final int CARD_HOVER = 0xFF1E1E2A;
    private static final int DIM = 0xFF8A8A9C;
    private static final int TEXT = 0xFFEDEDF5;
    private static final int TRACK_OFF = 0xFF34343F;

    // layout (unscaled GUI pixels)
    private static final int W = 470, H = 290;
    private static final int SIDE_W = 84, LIST_X = 100, LIST_W = 170, ROW_H = 26, ROW_GAP = 4;
    private static final int PANEL_X = 278, PANEL_W = 184;

    private static final int T_MODULE = 0, T_TAB = 1, T_BIND = 2, T_SETTING = 3;

    private static class Hit {
        int type;
        float x, y, w, h;
        float sx, sw;          // slider track bounds
        float clipY1, clipY2;  // visible area for scrolled rows
        Module m;
        Module.Category cat;
        Setting s;
    }

    private final List<Hit> hits = new ArrayList<>();

    private Module.Category selCat = Module.Category.values()[0];
    private Module selMod;
    private Module binding;
    private BindSetting bindingSetting;
    private Hit dragging;
    private HudModule dragHud;
    private double dragOffX, dragOffY;

    // animation state
    private float tabPillY = -1f;
    private float contentFade = 1f, panelFade = 1f;
    private float scroll, scrollTarget;

    private final long openedAt = System.nanoTime();
    private long lastNs = openedAt;

    public ClickGui() {
        super(new LiteralText("ClickGUI"));
        selMod = firstOf(selCat);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- helpers

    private static Module firstOf(Module.Category c) {
        for (Module m : ModuleManager.modules) if (m.category == c) return m;
        return null;
    }

    private static List<Module> modulesOf(Module.Category c) {
        List<Module> l = new ArrayList<>();
        for (Module m : ModuleManager.modules) if (m.category == c) l.add(m);
        return l;
    }

    private static String pretty(Module.Category c) {
        return c.label;
    }

    private float uiScale() {
        return Math.min(1f, Math.min((width - 16f) / W, (height - 16f) / H));
    }

    private double lx(double mx) {
        return (mx - width / 2.0) / uiScale() + width / 2.0;
    }

    private double ly(double my) {
        return (my - height / 2.0) / uiScale() + height / 2.0;
    }

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float approach(float v, float target, float speed) {
        return v + (target - v) * Math.min(1f, speed);
    }

    private void text(MatrixStack ms, String t, float x, float y, int color, float alpha) {
        int c = Render2D.ca(color, alpha);
        if (((c >>> 24) & 255) < 8) return; // vanilla treats very low alpha as opaque
        textRenderer.drawWithShadow(ms, t, x, y, c);
    }

    private void textScaled(MatrixStack ms, String t, float x, float y, float s, int color, float alpha) {
        ms.push();
        ms.translate(x, y, 0);
        ms.scale(s, s, 1f);
        text(ms, t, 0, 0, color, alpha);
        ms.pop();
    }

    private void gradientText(MatrixStack ms, String t, float x, float y, float s, float alpha) {
        ms.push();
        ms.translate(x, y, 0);
        ms.scale(s, s, 1f);
        float cx = 0;
        for (int i = 0; i < t.length(); i++) {
            String ch = String.valueOf(t.charAt(i));
            int col = Render2D.lerp(A1, A2, t.length() <= 1 ? 0f : i / (float) (t.length() - 1));
            text(ms, ch, cx, 0, col, alpha);
            cx += textRenderer.getWidth(ch);
        }
        ms.pop();
    }

    private Hit addHit(int type, float x, float y, float w, float h) {
        Hit hit = new Hit();
        hit.type = type;
        hit.x = x; hit.y = y; hit.w = w; hit.h = h;
        hit.clipY1 = -10000f; hit.clipY2 = 10000f;
        hits.add(hit);
        return hit;
    }

    private static boolean inside(Hit h, double mx, double my) {
        return mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h && my >= h.clipY1 && my < h.clipY2;
    }

    private void selectCategory(Module.Category c) {
        if (c == selCat) return;
        selCat = c;
        selMod = firstOf(c);
        contentFade = 0f;
        panelFade = 0f;
        scroll = scrollTarget = 0f;
    }

    private void selectModule(Module m) {
        if (m == selMod) return;
        selMod = m;
        panelFade = 0f;
        scroll = scrollTarget = 0f;
    }

    private static float settingH(Setting s) {
        return s instanceof NumSetting ? 30f : 24f;
    }

    private static float settingsContentH(Module m) {
        float h = 0;
        for (Setting s : m.settings) h += settingH(s);
        return h;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastNs) / 1_000_000_000f, 0.1f);
        lastNs = now;
        float k = dt * 14f;

        float e = ease((now - openedAt) / 1_000_000_000f / 0.25f);
        float us = uiScale();
        float s = us * (0.94f + 0.06f * e);
        float cxm = width / 2f, cym = height / 2f;
        float wx = (width - W) / 2f, wy = (height - H) / 2f;
        double lmx = lx(mouseX), lmy = ly(mouseY);

        // ----- animations
        contentFade = approach(contentFade, 1f, dt * 9f);
        panelFade = approach(panelFade, 1f, dt * 9f);
        for (Module m : ModuleManager.modules) {
            m.toggleAnim = approach(m.toggleAnim, m.enabled ? 1f : 0f, k);
            for (Setting st : m.settings) {
                float target = 0f;
                if (st instanceof BoolSetting) target = ((BoolSetting) st).value ? 1f : 0f;
                else if (st instanceof NumSetting) {
                    NumSetting n = (NumSetting) st;
                    target = (float) ((n.value - n.min) / (n.max - n.min));
                }
                st.anim = approach(st.anim, target, k * 1.3f);
            }
        }
        scroll = approach(scroll, scrollTarget, dt * 16f);

        hits.clear();
        fill(ms, 0, 0, width, height, Render2D.ca(0xFF000000, 0.5f * e));

        ms.push();
        ms.translate(cxm, cym, 0);
        ms.scale(s, s, 1f);
        ms.translate(-cxm, -cym, 0);

        // ----- window
        Render2D.shadow(ms, 12, wx, wy, wx + W, wy + H, A1, e * 0.55f);
        Render2D.shadow(ms, 12, wx, wy, wx + W, wy + H, e * 0.9f);
        Render2D.rrect(ms, 12, wx, wy, wx + W, wy + H, Render2D.ca(BG, e));
        Render2D.rrectH(ms, 1, wx + 40, wy + 1, wx + W - 40, wy + 3, Render2D.ca(A1, e), Render2D.ca(A2, e));

        // ----- sidebar
        Render2D.rrect(ms, 9, wx + 8, wy + 8, wx + 8 + SIDE_W, wy + H - 8, Render2D.ca(SIDEBAR, e));
        gradientText(ms, "CLIENT", wx + 20, wy + 20, 1.25f, e);
        text(ms, "1.16.5", wx + 21, wy + 35, DIM, e);

        Module.Category[] cats = Module.Category.values();
        float tabsTop = wy + 56;
        int selIndex = 0;
        for (int i = 0; i < cats.length; i++) if (cats[i] == selCat) selIndex = i;
        float pillTarget = tabsTop + selIndex * 30;
        tabPillY = tabPillY < 0 ? pillTarget : approach(tabPillY, pillTarget, k);

        Render2D.rrectH(ms, 7, wx + 14, tabPillY, wx + 14 + 72, tabPillY + 26,
                Render2D.ca(A1, 0.30f * e), Render2D.ca(A2, 0.30f * e));
        Render2D.rrectH(ms, 1.5f, wx + 14, tabPillY + 6, wx + 17, tabPillY + 20, Render2D.ca(A1, e), Render2D.ca(A2, e));

        for (int i = 0; i < cats.length; i++) {
            float ty = tabsTop + i * 30;
            Hit th = addHit(T_TAB, wx + 14, ty, 72, 26);
            th.cat = cats[i];
            boolean hover = inside(th, lmx, lmy);
            int tc = Render2D.lerp(DIM, TEXT, cats[i] == selCat ? 1f : (hover ? 0.6f : 0f));
            text(ms, pretty(cats[i]), wx + 26, ty + 9, tc, e);
        }

        float hy = wy + H - 46;
        text(ms, "LMB  toggle", wx + 18, hy, DIM, e * 0.8f);
        text(ms, "RMB  settings", wx + 18, hy + 11, DIM, e * 0.8f);
        text(ms, "MMB  bind", wx + 18, hy + 22, DIM, e * 0.8f);

        // ----- content header
        float cx0 = wx + LIST_X;
        List<Module> list = modulesOf(selCat);
        textScaled(ms, pretty(selCat), cx0, wy + 14, 1.45f, TEXT, e);
        text(ms, list.size() + (list.size() == 1 ? " module" : " modules"), cx0 + 1, wy + 31, DIM, e);

        // ----- module list
        float slide = (1f - contentFade) * 12f;
        float ca = e * contentFade;
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            float rx = cx0 + slide, ry = wy + 46 + i * (ROW_H + ROW_GAP);

            Hit mh = addHit(T_MODULE, cx0, ry, LIST_W, ROW_H);
            mh.m = m;
            boolean hover = inside(mh, lmx, lmy);
            boolean sel = m == selMod;

            int bg = hover || sel ? CARD_HOVER : CARD;
            if (sel) {
                Render2D.rrectH(ms, 7, rx, ry, rx + LIST_W, ry + ROW_H, Render2D.ca(A1, 0.85f * ca), Render2D.ca(A2, 0.85f * ca));
                Render2D.rrect(ms, 6, rx + 1, ry + 1, rx + LIST_W - 1, ry + ROW_H - 1, Render2D.ca(bg, ca));
            } else {
                Render2D.rrect(ms, 7, rx, ry, rx + LIST_W, ry + ROW_H, Render2D.ca(bg, ca));
            }
            if (m.toggleAnim > 0.01f) {
                Render2D.rrectH(ms, 6, rx + 1, ry + 1, rx + LIST_W - 1, ry + ROW_H - 1,
                        Render2D.ca(A1, 0.22f * m.toggleAnim * ca), Render2D.ca(A2, 0.22f * m.toggleAnim * ca));
            }

            text(ms, m.name, rx + 12, ry + 9, Render2D.lerp(0xFFB4B4C4, TEXT, m.toggleAnim), ca);

            // switch
            float sx = rx + LIST_W - 36, sy = ry + 7;
            Render2D.rrectH(ms, 6, sx, sy, sx + 26, sy + 12,
                    Render2D.ca(Render2D.lerp(TRACK_OFF, A1, m.toggleAnim), ca),
                    Render2D.ca(Render2D.lerp(TRACK_OFF, A2, m.toggleAnim), ca));
            float kx = sx + 1.5f + m.toggleAnim * 14f;
            Render2D.rrect(ms, 4.5f, kx, sy + 1.5f, kx + 9, sy + 10.5f, Render2D.ca(0xFFFFFFFF, ca));

            String tag = binding == m ? "..." : (m.key > 0 ? "[" + keyName(m.key) + "]" : "");
            if (!tag.isEmpty()) {
                text(ms, tag, sx - 6 - textRenderer.getWidth(tag), ry + 9, DIM, ca);
            }
        }

        // ----- settings panel
        float px = wx + PANEL_X, py = wy + 14, ph = H - 22;
        Render2D.rrect(ms, 9, px, py, px + PANEL_W, py + ph, Render2D.ca(CARD, e));
        float pa = e * panelFade;
        float pdx = (1f - panelFade) * 8f;

        if (selMod == null) {
            text(ms, "Select a module", px + 12, py + 14, DIM, pa);
        } else {
            Module m = selMod;
            float ix = px + pdx;
            textScaled(ms, m.name, ix + 12, py + 12, 1.3f, TEXT, pa);
            text(ms, pretty(m.category) + "  ·  " + (m.enabled ? "ON" : "OFF"), ix + 12, py + 28,
                    m.enabled ? A2 : DIM, pa);

            // bind button
            Hit bh = addHit(T_BIND, px + 12, py + 42, PANEL_W - 24, 20);
            bh.m = m;
            boolean bHover = inside(bh, lmx, lmy);
            Render2D.rrect(ms, 6, ix + 12, py + 42, ix + PANEL_W - 12, py + 62,
                    Render2D.ca(bHover || binding == m ? 0xFF272735 : 0xFF1E1E2A, pa));
            text(ms, "Bind", ix + 20, py + 48, DIM, pa);
            String bt = binding == m ? "press a key..." : (m.key > 0 ? "[" + keyName(m.key) + "]" : "none");
            text(ms, bt, ix + PANEL_W - 20 - textRenderer.getWidth(bt), py + 48,
                    binding == m ? A2 : TEXT, pa);

            Render2D.rrect(ms, 0.5f, ix + 12, py + 69, ix + PANEL_W - 12, py + 70, Render2D.ca(0xFF2A2A38, pa));

            // scrolling settings area
            float areaY1 = py + 74, areaY2 = py + ph - 8, areaH = areaY2 - areaY1;
            float contentH = settingsContentH(m);
            float maxScroll = Math.max(0f, contentH - areaH);
            scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
            scroll = Math.max(0f, Math.min(maxScroll, scroll));

            if (m.settings.isEmpty()) {
                text(ms, "No settings", ix + 12, areaY1 + 4, DIM, pa);
            } else {
                // map the clip rectangle through the current transform (centre-scaled by s)
                float sx1 = (px + 2 - cxm) * s + cxm, sy1 = (areaY1 - cym) * s + cym;
                Render2D.scissor(sx1, sy1, (PANEL_W - 4) * s, areaH * s);

                float ry = areaY1 - scroll;
                for (Setting st : m.settings) {
                    float rh = settingH(st);
                    if (ry + rh > areaY1 - 2 && ry < areaY2 + 2) {
                        Hit sh = addHit(T_SETTING, px + 8, ry, PANEL_W - 16, rh);
                        sh.m = m; sh.s = st;
                        sh.sx = px + 12; sh.sw = PANEL_W - 24;
                        sh.clipY1 = areaY1; sh.clipY2 = areaY2;

                        if (st instanceof BindSetting) {
                            BindSetting bs = (BindSetting) st;
                            boolean bHov = inside(sh, lmx, lmy);
                            text(ms, st.name, ix + 12, ry + 8, 0xFFCFCFDC, pa);
                            String bt2 = bindingSetting == bs ? "press a key..." : (bs.key > 0 ? "[" + keyName(bs.key) + "]" : "none");
                            float bw2 = textRenderer.getWidth(bt2) + 12;
                            float bx2 = ix + PANEL_W - 12 - bw2;
                            Render2D.rrect(ms, 5, bx2, ry + 4, bx2 + bw2, ry + 18,
                                    Render2D.ca(bHov || bindingSetting == bs ? 0xFF2A2A3A : 0xFF1E1E2A, pa));
                            text(ms, bt2, bx2 + 6, ry + 8, bindingSetting == bs ? A2 : TEXT, pa);
                        } else if (st instanceof BoolSetting) {
                            text(ms, st.name, ix + 12, ry + 8, 0xFFCFCFDC, pa);
                            float tx = ix + PANEL_W - 12 - 26, ty = ry + 6;
                            Render2D.rrectH(ms, 6, tx, ty, tx + 26, ty + 12,
                                    Render2D.ca(Render2D.lerp(TRACK_OFF, A1, st.anim), pa),
                                    Render2D.ca(Render2D.lerp(TRACK_OFF, A2, st.anim), pa));
                            float kx = tx + 1.5f + st.anim * 14f;
                            Render2D.rrect(ms, 4.5f, kx, ty + 1.5f, kx + 9, ty + 10.5f, Render2D.ca(0xFFFFFFFF, pa));
                        } else if (st instanceof NumSetting) {
                            NumSetting n = (NumSetting) st;
                            text(ms, st.name, ix + 12, ry + 4, 0xFFCFCFDC, pa);
                            String val = fmt(n.value);
                            text(ms, val, ix + PANEL_W - 12 - textRenderer.getWidth(val), ry + 4, TEXT, pa);
                            float tx = ix + 12, tw = PANEL_W - 24, ty = ry + 18;
                            Render2D.rrect(ms, 2.5f, tx, ty, tx + tw, ty + 5, Render2D.ca(TRACK_OFF, pa));
                            float fw = Math.max(5f, tw * st.anim);
                            Render2D.rrectH(ms, 2.5f, tx, ty, tx + fw, ty + 5, Render2D.ca(A1, pa), Render2D.ca(A2, pa));
                            Render2D.rrect(ms, 5.5f, tx + fw - 5.5f, ty - 3, tx + fw + 5.5f, ty + 8, Render2D.ca(0xFFFFFFFF, pa));
                        }
                    }
                    ry += rh;
                }
                Render2D.endScissor();
            }
        }

        ms.pop();
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = lx(mouseX), my = ly(mouseY);

        for (Hit h : hits) {
            if (!inside(h, mx, my)) continue;
            switch (h.type) {
                case T_TAB:
                    if (button == 0) selectCategory(h.cat);
                    break;
                case T_MODULE:
                    if (button == 0) h.m.toggle();
                    else if (button == 1) selectModule(h.m);
                    else if (button == 2) { selectModule(h.m); binding = h.m; }
                    break;
                case T_BIND:
                    if (button == 0) binding = h.m;
                    else if (button == 1) h.m.key = -1;
                    break;
                case T_SETTING:
                    if (h.s instanceof BindSetting) {
                        if (button == 0) bindingSetting = (BindSetting) h.s;
                        else if (button == 1) ((BindSetting) h.s).key = -1;
                        break;
                    }
                    if (button == 0) {
                        if (h.s instanceof BoolSetting) {
                            ((BoolSetting) h.s).value = !((BoolSetting) h.s).value;
                        } else if (h.s instanceof NumSetting) {
                            dragging = h;
                            slide(h, mx);
                        }
                    }
                    break;
                default:
                    break;
            }
            return true;
        }

        // clicks inside the window but on nothing are swallowed
        float wx = (width - W) / 2f, wy = (height - H) / 2f;
        if (mx >= wx && mx < wx + W && my >= wy && my < wy + H) return true;

        // outside the window: drag HUD blocks
        if (button == 0) {
            for (Module m : ModuleManager.modules) {
                if (m instanceof HudModule && m.enabled && ((HudModule) m).contains(mouseX, mouseY)) {
                    dragHud = (HudModule) m;
                    dragOffX = mouseX - dragHud.x;
                    dragOffY = mouseY - dragHud.y;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging != null) {
            slide(dragging, lx(mouseX));
            return true;
        }
        if (dragHud != null) {
            dragHud.x = (int) Math.max(0, Math.min(width - dragHud.w, mouseX - dragOffX));
            dragHud.y = (int) Math.max(0, Math.min(height - dragHud.h, mouseY - dragOffY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        dragHud = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scrollTarget -= (float) amount * 22f;
        if (scrollTarget < 0f) scrollTarget = 0f; // upper clamp happens while rendering
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingSetting != null) {
            if (keyCode == 259 || keyCode == 261) bindingSetting.key = -1; // Backspace / Delete
            else if (keyCode != 256) bindingSetting.key = keyCode;         // Esc cancels
            bindingSetting = null;
            return true;
        }
        if (binding != null) {
            if (keyCode == 259 || keyCode == 261) binding.key = -1; // Backspace / Delete
            else if (keyCode != 256) binding.key = keyCode;          // Esc cancels
            binding = null;
            return true;
        }
        if (keyCode == ModuleManager.OPEN_GUI_KEY) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }

    private void slide(Hit h, double lmx) {
        NumSetting n = (NumSetting) h.s;
        double frac = Math.max(0, Math.min(1, (lmx - h.sx) / h.sw));
        n.set(n.min + frac * (n.max - n.min));
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.2f", v);
    }

    private static String keyName(int key) {
        return InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
    }
}
