package dev.client.module;

public abstract class Setting {
    public final String name;
    public float anim; // GUI animation state

    protected Setting(String name) {
        this.name = name;
    }
}
