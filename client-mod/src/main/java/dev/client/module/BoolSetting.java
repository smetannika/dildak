package dev.client.module;

public class BoolSetting extends Setting {
    public boolean value;

    public BoolSetting(String name, boolean value) {
        super(name);
        this.value = value;
    }
}
