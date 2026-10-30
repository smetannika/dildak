package dev.client.module;

/** A key bind that lives inside a module's settings (shown as a clickable "[KEY]" button in the GUI). */
public class BindSetting extends Setting {
    public int key = -1; // GLFW key code, -1 = unbound

    public BindSetting(String name) {
        super(name);
    }
}
