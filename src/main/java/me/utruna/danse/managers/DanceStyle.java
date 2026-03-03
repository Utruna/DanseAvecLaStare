package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

public interface DanceStyle {
    Location computeLocation(Location origin, int tick);
    void applyPose(ArmorStand as, int tick);
    String getName();
}
