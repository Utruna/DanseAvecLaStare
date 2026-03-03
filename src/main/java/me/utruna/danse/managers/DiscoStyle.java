package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;

public class DiscoStyle extends AbstractDanceStyle {
    @Override
    public Location computeLocation(Location origin, int tick) {
        return origin.clone();
    }

    @Override
    public void applyPose(ArmorStand as, int tick) {
        double speed = tick * 0.3;
        as.setRightArmPose(new EulerAngle(Math.toRadians(-120), 0, Math.toRadians(Math.sin(speed) * 40)));
        as.setLeftArmPose(new EulerAngle(Math.toRadians(20), 0, 0));
        as.setHeadPose(new EulerAngle(0, Math.sin(speed) * 0.4, 0));
        double leg = Math.abs(Math.sin(speed)) * 0.3;
        as.setRightLegPose(new EulerAngle(leg, 0, 0));
        as.setLeftLegPose(new EulerAngle(leg, 0, 0));
    }
}
