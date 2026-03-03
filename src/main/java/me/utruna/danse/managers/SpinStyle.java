package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;

public class SpinStyle extends AbstractDanceStyle {
    @Override
    public Location computeLocation(Location origin, int tick) {
        Location loc = origin.clone();
        loc.setYaw(normalizeYaw(origin.getYaw() + (tick * 15.0f)));
        return loc;
    }

    @Override
    public void applyPose(ArmorStand as, int tick) {
        as.setRightArmPose(new EulerAngle(Math.toRadians(-90), 0, Math.toRadians(90)));
        as.setLeftArmPose(new EulerAngle(Math.toRadians(-90), 0, Math.toRadians(-90)));
    }
}
