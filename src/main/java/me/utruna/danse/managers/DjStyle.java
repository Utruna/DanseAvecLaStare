package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

public class DjStyle extends AbstractDanceStyle {
    @Override
    public Location computeLocation(Location origin, int tick) {
        return origin.clone();
    }

    @Override
    public String getName() {
        return "dj";
    }

    @Override
    public void applyPose(ArmorStand stand, int tick) {
        // No special pose for DJ style currently
    }
}