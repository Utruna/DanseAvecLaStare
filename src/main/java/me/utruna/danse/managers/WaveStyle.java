package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.util.EulerAngle;

public class WaveStyle extends AbstractDanceStyle {
    @Override
    public Location computeLocation(Location origin, int tick) {
        return origin.clone();
    }

    @Override
    public void applyPose(ArmorStand as, int tick) {
        double time = tick * 0.2;
        double waveR = Math.sin(time) * 1.0;
        double waveL = Math.sin(time + 1.5) * 1.0;
        as.setRightArmPose(new EulerAngle(waveR - 0.5, 0, Math.toRadians(30)));
        as.setLeftArmPose(new EulerAngle(waveL - 0.5, 0, Math.toRadians(-30)));
        as.setBodyPose(new EulerAngle(0, 0, Math.sin(time) * 0.15));
    }
}
