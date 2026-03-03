package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;

public class TwistStyle extends AbstractDanceStyle {
    @Override
    public Location computeLocation(Location origin, int tick) {
        double phase = tick * 0.15;
        Location loc = origin.clone();
        loc.setYaw(normalizeYaw(origin.getYaw() + (float) (Math.sin(phase) * 40.0)));
        return loc;
    }

    @Override
    public void applyPose(ArmorStand as, int tick) {
        double angle = Math.sin(tick * 0.3) * 0.6;
        as.setRightArmPose(new EulerAngle(angle, 0, Math.toRadians(10)));
        as.setLeftArmPose(new EulerAngle(-angle, 0, Math.toRadians(-10)));
    }
}
