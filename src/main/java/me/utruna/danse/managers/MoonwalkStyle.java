package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;

public class MoonwalkStyle extends AbstractDanceStyle {
    @Override
    public Location computeLocation(Location origin, int tick) {
        double back = (tick % 100) * 0.05;
        Location loc = origin.clone();
        loc.add(loc.getDirection().multiply(-back));
        return loc;
    }

    @Override
    public void applyPose(ArmorStand as, int tick) {
        double speed = tick * 0.4;
        double move = Math.sin(speed) * 0.5;
        as.setRightLegPose(new EulerAngle(move, 0, 0));
        as.setLeftLegPose(new EulerAngle(-move, 0, 0));
        as.setHeadPose(new EulerAngle(Math.toRadians(25), 0, 0));
        as.setRightArmPose(new EulerAngle(Math.toRadians(10), 0, 0));
        as.setLeftArmPose(new EulerAngle(Math.toRadians(10), 0, 0));
    }
}
