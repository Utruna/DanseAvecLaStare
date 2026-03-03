package me.utruna.danse.managers;

public abstract class AbstractDanceStyle implements DanceStyle {
    @Override
    public String getName() {
        String cls = this.getClass().getSimpleName();
        if (cls.endsWith("Style")) cls = cls.substring(0, cls.length() - 5);
        return cls.toLowerCase();
    }

    protected float normalizeYaw(float yaw) {
        while (yaw >= 180.0f) yaw -= 360.0f;
        while (yaw < -180.0f) yaw += 360.0f;
        return yaw;
    }
}
