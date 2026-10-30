package dev.client.module;

public class NumSetting extends Setting {
    public final double min, max, step;
    public double value;

    public NumSetting(String name, double value, double min, double max, double step) {
        super(name);
        this.value = value;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public void set(double v) {
        v = Math.round(v / step) * step;
        this.value = Math.max(min, Math.min(max, v));
    }

    public float f() {
        return (float) value;
    }
}
